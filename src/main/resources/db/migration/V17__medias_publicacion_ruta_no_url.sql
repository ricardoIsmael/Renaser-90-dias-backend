-- medias_publicacion.ruta_storage guardaba una URL ABSOLUTA donde va una CLAVE de objeto de S3.
--
-- El defecto que cierra (E-79 de docs/BITACORA_ERRORES.md): MediaItemRequest.aArchivoEntrada()
-- prometia en su javadoc traducir la URL que manda la app a bucket+ruta, pero no lo hacia --
-- metia la URL entera en el campo `ruta`. PublicacionMuroService.aVista() pasa esa ruta a
-- AlmacenamientoPort.firmarLectura, que la trata como clave de objeto, asi que la URL firmada
-- que recibia el celular quedaba anidada sobre si misma:
--
--   https://s3-renaser90dias.s3.us-east-1.amazonaws.com/https%3A//s3-renaser90dias.s3.amazonaws.com/muro/fotos/...
--
-- y S3 respondia 404. La foto se subia bien y quedaba en el bucket (verificado: el objeto existe
-- y con la clave correcta responde 200 image/jpeg); lo unico roto era leerla. En la app la falla
-- se veia como el recuadro gris con "Foto 1", porque FotoMuro esconde la imagen en onError.
--
-- Misma familia que E-57 (avatares) pero al reves: aquel persistia una URL *firmada* donde iba
-- una clave, este persiste una URL *absoluta*. Por eso el barrido de E-57, que buscaba URLs
-- firmadas persistidas, dio limpio en `community` y el defecto sobrevivio.
--
-- Por que se toca una BD declarada congelada (D-40): no hay cambio de estructura -- ni columnas
-- nuevas, ni renombres, ni tipos. Solo se reparan valores ya guardados, con una regla derivable
-- del propio dato, mas un CHECK que impide reintroducir el defecto. Mismo criterio que V13.

BEGIN;

-- Explicito a proposito, por el mismo motivo que V3/V11/V12/V13: si esta migracion corre sola en
-- un despliegue posterior, el search_path que fija V1 no esta puesto en la sesion.
SET search_path TO renaser, public;

-- 1) Se corta la firma y el esquema+host, en ese orden.
--    El '?' primero porque en SigV4 todo lo que caduca vive en la query string; el
--    `^https?://[^/]+/` despues deja el camino del objeto. Es la misma operacion que hace
--    MediaItemRequest.aClaveDeObjeto en Java, escrita en SQL: las dos tienen que coincidir, si
--    una cambia la otra tambien.
UPDATE medias_publicacion
   SET ruta_storage = regexp_replace(split_part(ruta_storage, '?', 1), '^https?://[^/]+/', '')
 WHERE ruta_storage ~* '^https?://';

-- 2) Las URL path-style (https://s3.amazonaws.com/<bucket>/muro/...) dejan el nombre del bucket
--    adelante despues del paso 1. Se ancla en el prefijo `muro/`, con el que
--    PublicacionMuroService.rutaDeMedia arma TODA clave del Muro, para recortarlo sin que esta
--    migracion tenga que conocer el nombre del bucket -- igual que el `indexOf(PREFIJO_MURO)` de
--    aClaveDeObjeto. `> 1` y no `> 0`: una clave que YA empieza en `muro/` esta bien y no se toca.
UPDATE medias_publicacion
   SET ruta_storage = substring(ruta_storage from position('muro/' in ruta_storage))
 WHERE position('muro/' in ruta_storage) > 1;

-- 3) La red de seguridad, para que no vuelva a entrar una URL aunque alguien escriba la tabla por
--    fuera del backend. Se prohibe el esquema, que es la marca inequivoca de "esto es una URL y
--    no una clave" -- una clave de S3 puede contener cualquier cosa menos empezar con http://.
--    La misma regla vive en MediaItemRequest.aClaveDeObjeto, para corregir antes y sin fallar.
ALTER TABLE medias_publicacion ADD CONSTRAINT medias_publicacion_ruta_no_es_url
    CHECK (ruta_storage NOT ILIKE 'http://%' AND ruta_storage NOT ILIKE 'https://%');

COMMENT ON COLUMN medias_publicacion.ruta_storage IS
    'CLAVE del objeto en S3 (ej. muro/fotos/<autorId>/<uuid>), nunca una URL. La URL de lectura '
    'se firma en cada respuesta y caduca; la clave es lo unico permanente (P-03). Guardar una '
    'URL aca deja la foto en 404 para siempre aunque el archivo exista (E-79).';

COMMIT;
