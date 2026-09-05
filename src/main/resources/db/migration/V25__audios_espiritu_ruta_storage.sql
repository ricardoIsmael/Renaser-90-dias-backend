-- ============================================================================
-- V25 -- audios_espiritu: ruta de almacenamiento propia, para poder REPRODUCIR el audio
-- ============================================================================
--
-- Problema que resuelve
-- ---------------------
-- `audios_espiritu` solo tiene `drive_file_id`: la referencia al archivo en el Google Drive
-- del backend viejo. Ese id NO es una URL reproducible -- no hay integracion con Drive en
-- este backend (decision D-34, `NoOpAudioCatalogAdapter`), y los 43 mp3 de Espiritu nunca se
-- migraron a S3 (D-50: "no hay ni puede haber un prefijo espiritu/ en este bucket").
-- Resultado: aunque el catalogo tenga 43 filas cargadas (V5), el aprendiz no puede escuchar
-- nada, porque no hay de donde sacar bytes.
--
-- Esta columna es el mismo mecanismo que ya usa `audioterapias.ruta_storage`: una ruta de
-- objeto dentro del bucket, que `AlmacenamientoPort.firmarLectura` convierte en URL
-- prefirmada (S3 con `renaser.storage.proveedor=s3`). Es la MISMA infraestructura que hoy
-- ya sirve los 13 mp3 de Audioterapia Semanal, probada en vivo (D-50).
--
-- NULLABLE a proposito, y hoy NULL en las 43 filas
-- -----------------------------------------------
-- No se rellena con ningun valor inventado: los archivos todavia no estan en el bucket. Con
-- la columna en NULL, `EspirituService` devuelve `audioUrl = null` y la app muestra el dia
-- como "contenido en preparacion" en vez de romper -- exactamente el mismo criterio de
-- degradacion que ya tiene el modulo cuando el catalogo no cubre un dia.
--
-- Para ENCENDER el audio hace falta un paso operativo, no un cambio de codigo:
--   1. bajar los 43 mp3 del Drive viejo (ids en `audios_espiritu.drive_file_id`),
--   2. `aws s3 sync` al prefijo `espiritu/` del bucket `s3-renaser90dias`,
--   3. `UPDATE audios_espiritu SET ruta_storage = 'espiritu/' || <archivo> WHERE dia = N;`
-- Es el mismo procedimiento que se hizo para `audioterapias` en D-50.
--
-- `drive_file_id` se conserva: es la trazabilidad del origen de cada audio y la unica forma
-- de saber que archivo de Drive corresponde a que dia cuando se haga esa migracion.

BEGIN;

SET search_path TO renaser, public;

ALTER TABLE audios_espiritu ADD COLUMN ruta_storage text;

COMMENT ON COLUMN audios_espiritu.ruta_storage IS
    'Ruta del objeto en el bucket (mismo rol que audioterapias.ruta_storage). NULL = archivo '
    'todavia no migrado desde Google Drive; el dia se sirve sin audio reproducible.';

COMMIT;
