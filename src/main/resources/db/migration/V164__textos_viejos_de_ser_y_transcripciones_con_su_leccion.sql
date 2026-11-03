-- ============================================================================
-- V164 — Textos viejos que SER lee y transcripciones que ya no se vuelven «material general» (issue #62, D-314).
-- ============================================================================
--
-- Qué problema resuelve
-- ---------------------
-- La revisión de la base de conocimiento de SER del 2026-11-02 (`ser-base-de-conocimiento.md`) encontró dos cosas en la base, las dos
-- por la misma razón: el dato quedó atrás de una decisión del dueño.
--
-- 1. `base_conocimiento.leccion_id` tenía `ON DELETE SET NULL` (V1). Una transcripción cargada con su lección es de la formación de esa lección
--    (`academy` filtra las lecciones visibles por formación). Pero el filtro de SER deja pasar a TODAS las formaciones los fragmentos con
--    `leccion_id IS NULL` (`PgVectorNativoAdapter`: «material general»). Con `SET NULL`, borrar una lección de la Escuela volvía su transcripción
--    «general» sin que nadie lo decidiera, y la veían el Diplomado y la Certificación («90 días», «tu guía de la semana uno»). Hoy son 0 filas,
--    pero es un camino abierto.
--
-- 2. `consultar_como_se_hace_habito` le entrega a SER `habitos.descripcion`, y cuatro descripciones del catálogo contradicen lo que el dueño
--    ya decidió y lo que el prompt dice:
--      · los tres «RITUAL TIERRA - AGUA - FUEGO» dicen «Foto/video del ritual», y se registran escribiendo (AGENTS.md de la app, 2026-10-31, punto 7);
--      · «ÚLTIMA COMIDA DEL DÍA» dice «Foto de la última comida del día 6pm»; es opcional y se marca con el círculo (V160, spec 038);
--      · «KILÓMETROS DIARIOS» solo describe la captura; con los pasos conectados los km se cuentan solos (V161, spec 037).
--    A eso se suma la descripción del rol Aprendiz, «Participante del programa de 90 días», cuando hay tres formaciones (90 días, 3 meses, 1 año).
--
-- Qué hace
-- --------
-- A. Cambia la llave foránea `base_conocimiento_leccion_id_fkey` a `ON DELETE CASCADE`: borrar una lección borra su transcripción. Los fragmentos SIN
--    lección (`leccion_id IS NULL`) no se tocan: son conocimiento general cargado a propósito por Administración (`POST /admin/conocimiento`) y siguen
--    siendo visibles para todas las formaciones. `ON UPDATE CASCADE` queda como estaba. Idempotente: `DROP … IF EXISTS` y `ADD` con el mismo nombre.
--    El filtro de visibilidad de las dos consultas (`leccion_id IS NULL OR leccion_id = ANY(visibles)`) no cambia: lo que cambia es que ya no hay
--    camino que fabrique un `NULL` por accidente.
--
-- B. Actualiza esas cuatro descripciones de hábito y la del rol Aprendiz, **solo si el texto actual sigue siendo el original del catálogo** (el
--    `WHERE` compara el texto exacto). Si el dueño ya las editó desde el panel de producción, no se pisa nada. Idempotente: con el texto nuevo el
--    `WHERE` ya no coincide y no cambia ninguna fila (tampoco mueve `actualizado_en`).
--    · Los rituales se buscan por `clave_sistema` (RITUAL_MORNING/MIDDAY/NIGHT), no por id: es única por formación (V134), así que no confunde con
--      «RITUAL DE MAÑANA (domingo)» (sin clave, de evidencia opcional, cuyo «Foto/video del ritual» sigue siendo verdad) y alcanza a una copia de otra
--      formación si la hubiera.
--    · Kilómetros se busca por `clave_sistema = 'DAILY_KM'`: existe en la Escuela (V84) y en el Diplomado (creado desde el panel, otro id).
--    · La última comida se busca por su id de catálogo, como V160 (no tiene clave de sistema).
--
-- Por qué no se reusa nada / por qué este nombre
-- ----------------------------------------------
-- Es un cambio de dato (cuatro textos) y de una restricción; no hay columna nueva. Una sola migración porque las dos mitades nacen del mismo issue
-- y se revisan juntas. `V163` la reservó la auditoría de consultas; esta es `V164`. Con `SET search_path` arriba, como todas (issue #61): sin él, esta
-- migración fallaría al correr sobre el esquema `public`.
--
-- Lo que NO toca: `recuerdos_renasia`, `mensajes_renasia` ni la base vectorial (qué hacer con lo ya guardado lo decide el dueño).
-- ============================================================================
SET search_path TO renaser, public;

-- A. Una transcripción vive y muere con su lección.
ALTER TABLE base_conocimiento DROP CONSTRAINT IF EXISTS base_conocimiento_leccion_id_fkey;
ALTER TABLE base_conocimiento
    ADD CONSTRAINT base_conocimiento_leccion_id_fkey
        FOREIGN KEY (leccion_id) REFERENCES lecciones (id) ON DELETE CASCADE ON UPDATE CASCADE;

COMMENT ON COLUMN base_conocimiento.leccion_id IS
    'Lección de la que sale el fragmento (V1). Con lección, el fragmento es de la formación de esa lección y se borra con ella (V164, issue #62). Sin lección (NULL) es conocimiento general cargado a propósito: lo ven todas las formaciones.';

-- B. Descripciones que SER entrega y que contradecían lo decidido. Solo si siguen siendo las originales.
UPDATE habitos
   SET descripcion = 'Se registra escribiendo: ¿cómo te sientes después de realizar el ritual?',
       actualizado_en = now()
 WHERE clave_sistema IN ('RITUAL_MORNING', 'RITUAL_MIDDAY', 'RITUAL_NIGHT')
   AND descripcion = 'Foto/video del ritual';

UPDATE habitos
   SET descripcion = 'Tu última comida del día, a las 6 pm. Es opcional: la marcas como hecha con el círculo, sin foto obligatoria. Si quieres, puedes dejar una foto.',
       actualizado_en = now()
 WHERE id = '3a8a82a0-787d-46bc-a69e-b29c110a37bc'
   AND descripcion = 'Foto de la última comida del día 6pm';

UPDATE habitos
   SET descripcion = 'Si conectas los pasos de tu teléfono (Health Connect, en Training: «Cuenta tus pasos solo»), tus kilómetros se cuentan solos. Si no, sube la captura de tu app de actividad con la distancia y la fecha de hoy, y escribe cuántos km recorriste.',
       actualizado_en = now()
 WHERE clave_sistema = 'DAILY_KM'
   AND descripcion = 'Sube la captura de tu app de actividad con la distancia y la fecha de hoy, y escribe cuántos km recorriste.';

UPDATE roles
   SET descripcion = 'Participante de un programa de Renaser'
 WHERE clave = 'APRENDIZ'
   AND descripcion = 'Participante del programa de 90 días';
