-- Completa V4: los 9 habitos de sistema que en produccion tienen is_active=false. Se habian
-- excluido a proposito en V4 (no hay historial de usuario que preservar, y 4 de los 9 ni
-- siquiera tenian horario -- ver docs/MODULOS_A_AVANZAR.md D-47). Se agregan igual, marcados
-- `activo=false` (baja logica, el mismo mecanismo que ya usa el esquema para esto), a pedido
-- explicito del dueno del proyecto ("ponlos por si acaso") -- por las dudas de que se
-- reactiven o de que algo mas adelante los referencie por id.
--
-- Mismo origen y mismo mapeo de valores que V4 (ver su cabecera). Verificaciones hechas antes
-- de escribir este archivo:
--   - 0 colisiones de titulo contra los 22 habitos de sistema que ya existen (los 18 de V4 +
--     los 4 fixtures de QA que ya estaban en la BD local).
--   - 0 colisiones de id (uuids de produccion, distintos de los 18 ya migrados).
--   - 0 colisiones de clave_sistema (los 9 la traen NULL en origen).
--   - Sin la anomalia de E-47/V4 (CHECKBOX con journal_entry_type poblado): el unico de estos
--     9 con journal_entry_type es 'REGISTRO DE INTOXICACIÓN CONSCIENTE', que SI es tipo
--     JOURNALING -- journal_entry_type='INTOXICATION_LOG' mapea limpio a REGISTRO_INTOXICACION,
--     no viola el CHECK `diario_solo_journaling`.
--   - Categorias (CUERPO/MENTE/CONSCIENCIA) e iconos (WALKING/WORKOUT/COLD_SHOWER/READING/
--     WATER/GRATITUDE/JOURNALING) usados por estos 9 ya estan sembrados desde V1.
--
-- Solo 5 de los 9 tienen horario en el dato de origen (habit_schedules); los otros 4
-- (REGISTRO DE INTOXICACIÓN CONSCIENTE, MANTRA MATUTINO & DECRETO, RITUAL DEL AGUA PRESENTE,
-- MOVIMIENTO LIBRE/BAILE) quedan sin fila en horarios_habito -- asi estaban en produccion, no
-- se inventa un horario que no tenian.

BEGIN;

SET search_path TO renaser, public;

INSERT INTO habitos (
    id, ambito, participante_id, titulo, descripcion, tipo, categoria_clave, icono_clave,
    grupo, clave_sistema, exigencia_evidencia, tipo_entrada_diario, es_opcional,
    obligatorio_en_intoxicacion, eleccion_dia_semanal, horas_extra_evidencia,
    dia_limite_edicion_libre, orden, activo, creado_en, actualizado_en
) VALUES
    ('d95cc787-bbe5-46ea-b7e5-4896778def91', 'SISTEMA', NULL, 'REGISTRO DE INTOXICACIÓN CONSCIENTE', 'Respuesta nocturna a las 6 preguntas de observación', 'JOURNALING', 'CONSCIENCIA', 'JOURNALING', NULL, NULL, 'OPCIONAL', 'REGISTRO_INTOXICACION', false, false, false, NULL, 90, 16, false, '2026-07-31 15:32:38.95', '2026-08-16 02:46:04.705'),
    ('ea87fdec-d4c1-4c4f-9e61-1557bc7255d1', 'SISTEMA', NULL, 'KILÓMETROS DIARIOS', 'Captura de tu aplicación de actividad donde se vea la distancia recorrida y la fecha de hoy. La captura debe mostrar al menos la distancia en kilómetros.', 'CHECKBOX', 'CUERPO', 'WALKING', NULL, NULL, 'OBLIGATORIA', NULL, true, false, false, NULL, 90, 16, false, '2026-07-31 15:32:38.95', '2026-08-18 18:42:29.046'),
    ('ec0f2b08-3a18-4ff4-8e2b-243e5220d211', 'SISTEMA', NULL, 'ENTRENAMIENTO FÍSICO', 'Foto tomada durante o al finalizar el entrenamiento, o una captura de tu aplicación fitness donde se vea la fecha y duración.', 'CHECKBOX', 'CUERPO', 'WORKOUT', NULL, NULL, 'OBLIGATORIA', NULL, true, false, false, NULL, 90, 15, false, '2026-07-31 15:32:38.95', '2026-08-18 18:42:30.291'),
    ('0103602a-7f28-41f6-bb95-a9623aab3c7e', 'SISTEMA', NULL, 'DUCHA FRÍA DIARIA', 'Check en actividad', 'CHECKBOX', 'CUERPO', 'COLD_SHOWER', NULL, NULL, 'OPCIONAL', NULL, true, false, false, NULL, 90, 14, false, '2026-07-31 15:32:38.95', '2026-08-18 18:42:33.442'),
    ('6d238aee-b030-41a5-a6f8-dd0a961f8c7f', 'SISTEMA', NULL, 'LECTURA CONSCIENTE (3 PÁGINAS)', 'Lectura física de al menos 3 páginas', 'CHECKBOX', 'MENTE', 'READING', NULL, NULL, 'OPCIONAL', NULL, false, false, false, NULL, 90, 13, false, '2026-07-31 15:32:38.95', '2026-08-28 14:38:14.878'),
    ('1419575e-e64a-4495-be85-11b41af72b15', 'SISTEMA', NULL, 'AGUA CON LIMÓN + JUGO VERDE (noche)', 'Toma nocturna antes de dormir', 'CHECKBOX', 'CUERPO', 'WATER', 'AGUA CON LIMÓN + JUGO VERDE', NULL, 'OBLIGATORIA', NULL, false, false, false, NULL, NULL, 4, false, '2026-07-31 15:32:38.95', '2026-08-04 17:49:34.794'),
    ('19b1941e-2694-4500-a459-ff9747ec0ece', 'SISTEMA', NULL, 'MANTRA MATUTINO & DECRETO', 'Hoy será un día maravilloso / Yo elijo esto porque amo mi vida', 'CHECKBOX', 'MENTE', 'GRATITUDE', NULL, NULL, 'OPCIONAL', NULL, false, false, false, NULL, NULL, 20, false, '2026-07-31 15:32:38.95', '2026-08-04 17:50:48.394'),
    ('6c817288-7614-4820-a814-17a5dac297b3', 'SISTEMA', NULL, 'RITUAL DEL AGUA PRESENTE', 'Beber agua con presencia plena e intención', 'CHECKBOX', 'CUERPO', 'WATER', NULL, NULL, 'OPCIONAL', NULL, false, false, false, NULL, NULL, 21, false, '2026-07-31 15:32:38.95', '2026-08-04 17:50:48.394'),
    ('0ab39e62-5270-4919-8768-66c6f03205fb', 'SISTEMA', NULL, 'MOVIMIENTO LIBRE / BAILE', 'Expresión corporal sin técnica ni juicio', 'CHECKBOX', 'CUERPO', 'WORKOUT', NULL, NULL, 'OPCIONAL', NULL, false, false, false, NULL, NULL, 22, false, '2026-07-31 15:32:38.95', '2026-08-04 17:50:48.394');

INSERT INTO horarios_habito (
    habito_id, dia_inicio, dia_fin, tipo_dia, hora_disparo, hora_limite, creado_en, actualizado_en
) VALUES
    ('ea87fdec-d4c1-4c4f-9e61-1557bc7255d1', 1, 90, 'TODOS', '07:00', NULL, '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('ec0f2b08-3a18-4ff4-8e2b-243e5220d211', 1, 90, 'TODOS', '06:30', '08:30', '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('0103602a-7f28-41f6-bb95-a9623aab3c7e', 1, 90, 'TODOS', '06:00', '08:00', '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('6d238aee-b030-41a5-a6f8-dd0a961f8c7f', 1, 90, 'TODOS', '20:00', NULL, '2026-07-31 15:32:38.95', '2026-07-31 15:32:38.95'),
    ('1419575e-e64a-4495-be85-11b41af72b15', 1, 34, 'DISCIPLINA', '20:00', NULL, '2026-07-31 15:32:38.95', '2026-07-31 15:32:38.95');

COMMIT;
