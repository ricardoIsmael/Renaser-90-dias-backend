-- La foto propia de un grupo, la que eligen el administrador o el mentor del grupo (D-212, 2026-09-27).
--
-- Que problema resuelve
-- ---------------------
-- Todos los grupos muestran en el chat la misma foto: la tarjeta de Canva sin nombre que la app trae
-- en el APK. El duenio decidio que la foto de un grupo se pueda cambiar, y que la cambien «Admin y el
-- mentor de ese grupo». Hace falta guardar cual es la foto de cada grupo y desde cuando.
--
-- Por que dos columnas de `celulas` y no una tabla
-- ------------------------------------------------
-- Un grupo tiene a lo sumo una foto propia, y es un dato del grupo, no un historial: al cambiarla, la
-- anterior se borra del almacenamiento. Una tabla aparte seria una relacion 1 a 0..1 con la misma
-- clave. Sin columnas existentes que sirvan: `url_videollamada` es otra cosa, y no hay ninguna de
-- imagen en `celulas`.
--
-- Por que la clave y no una URL
-- -----------------------------
-- `foto_ruta` es la clave del objeto en el almacenamiento (`grupos/<id>/foto-<milisegundos>.jpg`), no
-- una URL: el objeto es privado y lo sirve el backend con sesion (`GET /api/v1/chat/conversations/{id}/foto`,
-- solo a quien puede ver el chat del grupo). Guardar una URL prefirmada fue el error de E-57: vence.
-- La clave cambia en cada foto nueva, asi que nunca se pisa un objeto que un telefono tenga en cache.
--
-- Por que la fecha
-- ----------------
-- `foto_cambiada_en` rompe el cache del telefono: la ruta que recibe la app lleva `?v=<milisegundos>`
-- de este instante, y con otra foto la ruta cambia. NULL las dos = el grupo usa la foto de Renaser. El
-- CHECK las ata: una clave sin fecha (o al reves) no tendria sentido, y se puede evaluar en la fila.
--
-- Quien las escribe
-- -----------------
-- Nadie por JPA: `CelulaJpaEntity` no las mapea, asi que guardar un grupo no las pisa (mismo criterio que
-- `asignaciones_celula.bienvenida_enviada_en`, V71). Las lee y escribe `FotoDelGrupoJdbcAdapter`, que al
-- reemplazar la foto devuelve la clave anterior en el mismo UPDATE, para borrar exactamente ese objeto.
ALTER TABLE renaser.celulas
    ADD COLUMN foto_ruta text,
    ADD COLUMN foto_cambiada_en timestamptz;

ALTER TABLE renaser.celulas
    ADD CONSTRAINT celulas_foto_completa_o_ausente
        CHECK ((foto_ruta IS NULL) = (foto_cambiada_en IS NULL));

COMMENT ON COLUMN renaser.celulas.foto_ruta IS
    'Clave en el almacenamiento de la foto propia del grupo (D-212). NULL = usa la foto de Renaser.';
COMMENT ON COLUMN renaser.celulas.foto_cambiada_en IS
    'Cuando se eligio la foto propia; va en la ruta (?v=) para que el telefono la vuelva a bajar (D-212).';
