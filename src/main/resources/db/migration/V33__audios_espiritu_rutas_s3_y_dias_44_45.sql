-- =====================================================================================
-- Pastilla Renacer: conectar el catalogo con los archivos reales, y cerrar los dos dias
-- que faltaban.
--
-- POR QUE SE TOCA LA BASE
--
-- Las 43 filas de `audios_espiritu` tenian `drive_file_id` cargado y `ruta_storage` en NULL,
-- las 43. `EspirituService` (habits) lee EXCLUSIVAMENTE `ruta_storage`:
--
--     if (audio == null || audio.rutaStorage() == null || audio.rutaStorage().isBlank()) { ... }
--
-- `drive_file_id` no lo lee nadie: quedo de cuando la fuente iba a ser Google Drive, integracion
-- que nunca se hizo (CLAUDE.md S11, `AudioCatalogPort` solo tiene `NoOpAudioCatalogAdapter`).
-- Resultado: los audios estaban catalogados y NO sonaba ninguno. Este es el arreglo.
--
-- POR QUE 45 Y NO 43
--
-- La carpeta de origen tiene 45 archivos (DIA01..DIA45) y la tabla tenia 43. Los dos que
-- faltaban venian ademas en `.ogg`, formato que iOS no reproduce de forma nativa, asi que se
-- convirtieron a mp3 (128 kbps) antes de subirlos. Los 45 estan en
-- `s3://renaser90dias-prod/contenido/pastilla-renacer/dia-NN.mp3`, verificado sin huecos.
--
-- POR QUE `drive_file_id` PASA A SER NULLABLE
--
-- Era NOT NULL, y las filas nuevas (44, 45) no tienen id de Drive porque nunca estuvieron ahi.
-- En vez de inventar un valor de relleno para satisfacer una restriccion de una integracion
-- muerta, se afloja la columna. No se borra: las 43 filas viejas conservan su id, que sirve de
-- rastro de donde salio cada audio.
--
-- POR QUE LA CLAVE ES `dia-NN` Y NO EL NOMBRE ORIGINAL
--
-- Los archivos venian como `DIA01 - Estas sufriendo por victima.mp3`: espacios, acentos, signos
-- de interrogacion y un caso con el guion pegado (`DIA23-`). Como clave de S3 eso obliga a
-- codificar en cada URL y se rompe distinto en cada cliente. `dia-NN.mp3` es estable, ordenable,
-- y se deriva de `audios_espiritu.dia` sin ninguna tabla de traduccion.
--
-- OJO — el `dia` de esta tabla NO es el dia de programa: es el numero de audio. El desbloqueo lo
-- calcula `EspirituService.AUDIO_UNLOCK_START_DAY = 7` (dia de programa 8 -> audio 1). Con 45
-- audios, la Pastilla cubre hasta el dia de programa 52.
-- =====================================================================================

ALTER TABLE renaser.audios_espiritu
    ALTER COLUMN drive_file_id DROP NOT NULL;

-- Las 43 existentes: se completa la ruta derivandola del propio `dia`, sin listar 43 UPDATE.
UPDATE renaser.audios_espiritu
SET ruta_storage   = 'contenido/pastilla-renacer/dia-' || lpad(dia::text, 2, '0') || '.mp3',
    actualizado_en = now()
WHERE ruta_storage IS NULL;

-- Erratum de carga: el titulo del audio 43 quedo con un cero de mas ("patron-0de"). El archivo
-- de origen se llama bien; fue un error al cargar el catalogo. Es texto que ve el aprendiz.
UPDATE renaser.audios_espiritu
SET titulo         = 'Rompe-el-patron-de-no-incomodar',
    actualizado_en = now()
WHERE dia = 43;

-- Los dos que faltaban. Titulos tomados del nombre de archivo de origen.
INSERT INTO renaser.audios_espiritu (dia, titulo, drive_file_id, mime, tamano_bytes, ruta_storage)
VALUES (44, 'Tu mente te manipula', NULL, 'audio/mpeg',  9410732, 'contenido/pastilla-renacer/dia-44.mp3'),
       (45, 'Abraza el dolor',      NULL, 'audio/mpeg', 11763500, 'contenido/pastilla-renacer/dia-45.mp3')
ON CONFLICT (dia) DO NOTHING;
