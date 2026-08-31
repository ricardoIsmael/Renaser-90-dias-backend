-- usuarios.avatar_url guardaba una URL PREFIRMADA, que vence. Pasa a guardar la URL permanente.
--
-- El defecto que cierra (E-57 de docs/BITACORA_ERRORES.md): AvatarService.confirmar() firmaba
-- una URL de lectura con validez de 7 dias y persistia ESA URL como texto. A los 7 dias del
-- ultimo cambio de foto la firma vencia y nadie la volvia a firmar nunca -- el avatar quedaba
-- roto para siempre, y no solo en el perfil: el mismo string sale en el muro, los comentarios,
-- el chat, los miembros de celula, los testimonios y el panel admin, todos via
-- users.api.UserSummary. Hoy no se nota porque el adaptador por defecto es el NoOp (D-34) y
-- devuelve about:blank; se rompia solo, y tarde, el dia que se active S3.
--
-- La decision (D-55): el objeto del avatar pasa a ser de LECTURA PUBLICA y la columna guarda su
-- URL permanente -- ahora el nombre `avatar_url` dice la verdad. Firmar en cada respuesta
-- tambien arreglaba el vencimiento, pero cambia la URL en cada lectura y con eso invalida el
-- cache de imagen del cliente: un muro con 20 avatares volveria a bajar las 20 fotos cada vez.
-- El avatar es de baja sensibilidad y se ve constantemente; es el patron de GitHub/Slack, y el
-- dueno del proyecto acepto explicitamente que la ruta sea adivinable.
--
-- REQUISITO DE INFRAESTRUCTURA, que no vive en el codigo: el bucket tiene que permitir
-- s3:GetObject anonimo sobre el prefijo `avatares/*`. S3 bloquea el acceso publico por defecto
-- (Block Public Access), asi que sin desactivar esa opcion para el bucket y sin la bucket policy
-- correspondiente, la URL que devuelve el backend es correcta pero responde 403. Esta escrito
-- junto a los permisos IAM minimos en docs/MODULOS_A_AVANZAR.md D-55.
--
-- Por que se toca una BD declarada congelada (D-40): no hay cambio de estructura -- ni columnas
-- nuevas, ni renombres, ni tipos. Solo se reparan valores ya guardados, con una regla derivable
-- del propio dato (la firma vive en la query string), mas un CHECK que impide reintroducir el
-- defecto.

BEGIN;

-- Explicito a proposito, por el mismo motivo que V3/V11/V12: si esta migracion corre sola en un
-- despliegue posterior, el search_path que fija V1 no esta puesto en la sesion.
SET search_path TO renaser, public;

-- 1) Las que quedaron con el marcador del adaptador NoOp -> NULL.
--    `about:blank#pendiente-s3/avatares/<id>` no es una URL servible ni se puede reparar: se
--    generaba cuando NO habia adaptador de S3, y en ese mismo escenario la URL de SUBIDA tambien
--    era un marcador, asi que el cliente nunca llego a subir un archivo. Detras de esas filas no
--    hay objeto en S3. NULL es el valor correcto -- "no tiene avatar" -- y no una URL inventada
--    que devolveria 404. Va ANTES del corte por '?' porque el marcador no tiene query string.
UPDATE usuarios
   SET avatar_url = NULL
 WHERE avatar_url LIKE 'about:blank%';

-- 2) Las prefirmadas -> se les corta la firma y queda la URL permanente del mismo objeto.
--    En una URL prefirmada de S3 (SigV4) TODO lo que caduca viaja en la query string
--    (X-Amz-Signature, X-Amz-Expires, X-Amz-Date...); el esquema, host y clave de adelante del
--    '?' son exactamente la URL publica de ese objeto. Por eso `split_part(..., '?', 1)` es una
--    reparacion exacta, no una heuristica.
--    No se tocan las NULL: NULL significa "no tiene avatar" y tiene que seguir significandolo.
--    Tampoco las que ya no tienen '?', que quedan igual (split_part devuelve el string entero).
UPDATE usuarios
   SET avatar_url = split_part(avatar_url, '?', 1)
 WHERE avatar_url IS NOT NULL
   AND position('?' in avatar_url) > 0;

-- 3) Mismo defecto, otra tabla: promover una publicacion a testimonio COPIA el avatar del autor
--    a `testimonios.avatar_url`, asi que las filas creadas antes de este cambio heredaron una
--    URL prefirmada congelada. Se reparan con la misma regla. El snapshot en si es intencional
--    (un testimonio es una foto de un momento) y se conserva: lo que se arregla es el
--    vencimiento, no el hecho de copiar.
UPDATE testimonios
   SET avatar_url = NULL
 WHERE avatar_url LIKE 'about:blank%';

UPDATE testimonios
   SET avatar_url = split_part(avatar_url, '?', 1)
 WHERE avatar_url IS NOT NULL
   AND position('?' in avatar_url) > 0;

-- 4) La red de seguridad, para que no vuelva a pasar aunque alguien escriba la tabla por fuera
--    del backend. Se mira la marca inequivoca de SigV4 y no el '?' a secas: un CDN podria
--    legitimamente servir una imagen con query string, pero X-Amz-Signature solo aparece en una
--    URL prefirmada, que es exactamente lo que no puede persistirse. La misma regla vive en
--    User.changeAvatar, para fallar antes y con mejor mensaje.
ALTER TABLE usuarios ADD CONSTRAINT usuarios_avatar_url_no_prefirmada
    CHECK (avatar_url IS NULL OR avatar_url NOT ILIKE '%X-Amz-Signature%');

ALTER TABLE testimonios ADD CONSTRAINT testimonios_avatar_url_no_prefirmada
    CHECK (avatar_url IS NULL OR avatar_url NOT ILIKE '%X-Amz-Signature%');

COMMENT ON COLUMN usuarios.avatar_url IS
    'URL PERMANENTE de la foto de perfil. Excepcion deliberada a P-03 (D-55): el objeto del '
    'avatar es de lectura publica, el resto de los binarios guarda ruta y se firma al leer. '
    'JAMAS una URL prefirmada: vence y deja la foto rota para siempre (E-57).';

COMMENT ON COLUMN testimonios.avatar_url IS
    'Copia del avatar del autor al momento de promover (un testimonio es una foto de un '
    'momento). Misma regla que usuarios.avatar_url: URL permanente, nunca una prefirmada.';

COMMIT;
