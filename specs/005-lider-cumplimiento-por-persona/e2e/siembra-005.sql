-- =====================================================================================
-- E2E de la spec 005 (el lider ve el cumplimiento de cada persona por grupo) - datos de prueba.
-- SOLO BASE LOCAL `renaser_lider005` (clon de renaser_habitos_rol, V96 -> V109). NUNCA produccion ni otra base.
--   docker exec -i renaser-db psql -U postgres -d renaser_lider005 -f siembra-005.sql
-- Relativo a "hoy" (Lima): lunes 19/10/2026. Semana = sabado a viernes:
--   W-1 = 3..9/10 (la primera de Ana), W0 = 10..16/10 (ultima cerrada), W+0 = 17..23/10 (en curso).
-- Cuentas (ids e2e00500-0000-4000-8000-0000000000NN, clave unica = la de habitos-por-rol).
-- =====================================================================================
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE param AS SELECT (now() AT TIME ZONE 'America/Lima')::date AS hoy;

CREATE TEMP TABLE cuenta (
    n int PRIMARY KEY, caso text UNIQUE, id uuid, email text, nombre text, rol text,
    fecha_inicio date, grupo int, cerrada boolean DEFAULT false
);
INSERT INTO cuenta (n, caso, id, email, nombre, rol, fecha_inicio, grupo) VALUES
 (1,  'LUISA',   'e2e00500-0000-4000-8000-000000000001', 'e2e-lid-luisa@renaser.test',   'Luisa Rojas Mentora',  'MENTOR',   DATE '2026-09-25', 1),
 (2,  'PEDRO',   'e2e00500-0000-4000-8000-000000000002', 'e2e-lid-pedro@renaser.test',   'Pedro Mentor Loto',    'MENTOR',   NULL,              2),
 (3,  'GABRIELA','e2e00500-0000-4000-8000-000000000003', 'e2e-lid-gabriela@renaser.test','Gabriela Guía Cedro',  'MENTOR',   DATE '2026-10-10', 3),
 (11, 'ANA',     'e2e00500-0000-4000-8000-000000000011', 'e2e-lid-ana@renaser.test',     'Ana Pérez',            'APRENDIZ', DATE '2026-10-03', 1),
 (12, 'ROCIO',   'e2e00500-0000-4000-8000-000000000012', 'e2e-lid-rocio@renaser.test',   'Rocío Roja',           'APRENDIZ', DATE '2026-09-25', 1),
 (13, 'VALERIA', 'e2e00500-0000-4000-8000-000000000013', 'e2e-lid-valeria@renaser.test', 'Valeria Verde',        'APRENDIZ', DATE '2026-09-25', 1),
 (14, 'INES',    'e2e00500-0000-4000-8000-000000000014', 'e2e-lid-ines@renaser.test',    'Inés Inicio',          'APRENDIZ', DATE '2026-10-17', 1),
 (15, 'PRUEBA',  'e2e00500-0000-4000-8000-000000000015', 'e2e-lid-pruebaqa@renaser.test','Pruebas QA Cuenta',    'APRENDIZ', DATE '2026-09-25', 1),
 (16, 'CERRADA', 'e2e00500-0000-4000-8000-000000000016', 'e2e-lid-cecilia@renaser.test', 'Cecilia Cerrada',      'APRENDIZ', DATE '2026-09-25', 1),
 (21, 'BETO',    'e2e00500-0000-4000-8000-000000000021', 'e2e-lid-beto@renaser.test',    'Beto Paz',             'APRENDIZ', DATE '2026-09-25', 2),
 (22, 'CARLA',   'e2e00500-0000-4000-8000-000000000022', 'e2e-lid-carla@renaser.test',   'Carla Cielo',          'APRENDIZ', DATE '2026-09-25', 2),
 (31, 'HUGO',    'e2e00500-0000-4000-8000-000000000031', 'e2e-lid-hugo@renaser.test',    'Hugo Cedro',           'APRENDIZ', DATE '2026-09-25', 3);
UPDATE cuenta SET cerrada = true WHERE caso = 'CERRADA';

INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, hash_contrasena, contrasena_actualizada_en,
                              creado_en, actualizado_en, baja_solicitada_en, motivo_estado, estado_cambiado_en)
SELECT c.id, c.email, c.nombre, CAST(c.rol AS renaser.rol_usuario),
       CAST(CASE WHEN c.cerrada THEN 'SUSPENDIDO' ELSE 'ACTIVO' END AS renaser.estado_usuario),
       (SELECT hash_contrasena FROM renaser.usuarios WHERE id = 'fb769662-da4b-4d88-9abd-c3f59420586a'),
       now(), now() - interval '40 days', now(),
       CASE WHEN c.cerrada THEN now() - interval '2 days' END,
       CASE WHEN c.cerrada THEN 'Cuenta cerrada por la propia persona' END,
       CASE WHEN c.cerrada THEN now() - interval '2 days' END
  FROM cuenta c
ON CONFLICT (id) DO UPDATE SET estado = EXCLUDED.estado, hash_contrasena = EXCLUDED.hash_contrasena, actualizado_en = now();

INSERT INTO renaser.perfiles_mentor (usuario_id, nivel, estado_operativo, creado_en, actualizado_en)
SELECT id, 'N1', 'VERDE', now(), now() FROM cuenta WHERE caso IN ('LUISA','PEDRO','GABRIELA')
ON CONFLICT (usuario_id) DO NOTHING;

CREATE TEMP TABLE grupo (n int PRIMARY KEY, id uuid, nombre text, mentor text);
INSERT INTO grupo VALUES (1, 'e2e00500-0000-4000-8000-0000000000a1', 'Fénix 005', 'LUISA'),
                         (2, 'e2e00500-0000-4000-8000-0000000000a2', 'Loto 005',  'PEDRO'),
                         (3, 'e2e00500-0000-4000-8000-0000000000a3', 'Cedro 005', 'GABRIELA');
INSERT INTO renaser.celulas (id, nombre, mentor_id, cohorte_id, tipo)
SELECT g.id, g.nombre, (SELECT id FROM cuenta WHERE caso = g.mentor), '11111111-1111-4111-8111-111111111111', 'REGULAR'
  FROM grupo g
ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, mentor_id = EXCLUDED.mentor_id;

DELETE FROM renaser.asignaciones_celula WHERE celula_id IN (SELECT id FROM grupo);
INSERT INTO renaser.asignaciones_celula (celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
SELECT g.id, m.id, 'MENTOR', timestamptz '2026-10-01 00:00-05', NULL, 'ADMINISTRATIVO', 'e2e005-mentor|' || m.id || '|' || g.id
  FROM grupo g JOIN cuenta m ON m.caso = g.mentor;
INSERT INTO renaser.asignaciones_celula (celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
SELECT g.id, c.id, 'APRENDIZ', timestamptz '2026-10-01 00:00-05', NULL, 'ADMINISTRATIVO', 'e2e005-alta|' || c.id || '|' || g.id
  FROM cuenta c JOIN grupo g ON g.n = c.grupo WHERE c.rol = 'APRENDIZ';

INSERT INTO renaser.participantes_programa (usuario_id, mentor_id, celula_id, dia_programa, dia_programa_avanzado_el,
                                            fase, fecha_inicio, programa_activado_en, timezone, programa_completado,
                                            programa_completado_en, dia_post_programa, creado_en, actualizado_en,
                                            dias_ajuste_programa)
SELECT c.id,
       CASE WHEN c.rol = 'APRENDIZ' THEN (SELECT m.id FROM cuenta m JOIN grupo g ON g.mentor = m.caso WHERE g.n = c.grupo) END,
       CASE WHEN c.rol = 'APRENDIZ' THEN (SELECT g.id FROM grupo g WHERE g.n = c.grupo) END,
       LEAST(90, p.hoy - c.fecha_inicio + 1), p.hoy,
       CAST(CASE WHEN p.hoy - c.fecha_inicio + 1 <= 7 THEN 'FASE_1_RENACER' ELSE 'FASE_2_DESARROLLO' END AS renaser.fase_programa),
       c.fecha_inicio,
       ((c.fecha_inicio - 1)::timestamp + interval '20 hours') AT TIME ZONE 'America/Lima',
       'America/Lima', false, NULL, 0, now() - interval '40 days', now(), 0
  FROM cuenta c CROSS JOIN param p
 WHERE c.fecha_inicio IS NOT NULL
ON CONFLICT (usuario_id) DO UPDATE
   SET mentor_id = EXCLUDED.mentor_id, celula_id = EXCLUDED.celula_id, dia_programa = EXCLUDED.dia_programa,
       fecha_inicio = EXCLUDED.fecha_inicio, programa_activado_en = EXCLUDED.programa_activado_en,
       fase = EXCLUDED.fase, actualizado_en = now();

INSERT INTO renaser.estado_onboarding (usuario_id, flujo_actual, seccion_actual, paso_actual, progreso_flujo,
                                       terminos_aceptados_en, pacto_aceptado_en, pacto_firmado_en, iniciado_en,
                                       ultima_actividad_en, completado, completado_en, creado_en, actualizado_en)
SELECT c.id, 'pacto', 'firma', 0, '{"chapter": 2, "porcentaje": 100, "totalChapters": 3}'::jsonb,
       now() - interval '38 days', now() - interval '38 days', now() - interval '38 days', now() - interval '39 days',
       now() - interval '38 days', true, now() - interval '38 days', now() - interval '39 days', now()
  FROM cuenta c
ON CONFLICT (usuario_id) DO UPDATE SET completado = true, actualizado_en = now();

INSERT INTO renaser.puntajes_participante (participante_id, coherencia, puntos_liga, racha_actual, racha_maxima, actualizado_en)
SELECT c.id, 80, 100 + c.n, 0, 0, now() FROM cuenta c WHERE c.fecha_inicio IS NOT NULL
ON CONFLICT (participante_id) DO UPDATE SET actualizado_en = now();

DELETE FROM renaser.publicaciones_muro WHERE autor_id IN (SELECT id FROM cuenta);
DELETE FROM renaser.registros_habito   WHERE participante_id IN (SELECT id FROM cuenta);
DELETE FROM renaser.rocas_diarias      WHERE participante_id IN (SELECT id FROM cuenta);
DELETE FROM renaser.semaforo_dias      WHERE participante_id IN (SELECT id FROM cuenta);
DELETE FROM renaser.semaforo_semanas   WHERE participante_id IN (SELECT id FROM cuenta);
DELETE FROM renaser.semaforo_pausas    WHERE usuario_id      IN (SELECT id FROM cuenta);

CREATE TEMP TABLE hab (clave text PRIMARY KEY, id uuid, titulo text);
INSERT INTO hab (clave, id, titulo)
SELECT v.clave, h.id, h.titulo
  FROM (VALUES ('AGUA', 'WARM_LEMON_WATER'), ('JUGO', 'GREEN_JUICE'), ('RITUAL', 'RITUAL_MORNING'),
               ('PASTILLA', 'PASTILLA_RENACER'), ('POST', 'COMMUNITY_POST'), ('DESPERTAR', 'WAKE_UP'),
               ('AUDIO', 'AUDIO_THERAPY_WEEKLY'), ('COMIDA1', NULL), ('COMIDA2', NULL), ('NOCHE', 'RITUAL_NIGHT')) AS v(clave, cs)
  JOIN renaser.habitos h ON h.ambito = 'SISTEMA' AND (
       h.clave_sistema = v.cs
    OR (v.clave = 'COMIDA1' AND h.titulo = 'PRIMERA COMIDA (ROMPO EL AYUNO)')
    OR (v.clave = 'COMIDA2' AND h.titulo = 'ÚLTIMA COMIDA DEL DÍA'));
DO $$ BEGIN IF (SELECT count(*) FROM hab) <> 10 THEN RAISE EXCEPTION 'Faltan habitos: %', (SELECT count(*) FROM hab); END IF; END $$;

-- Ana: matriz explicita, 17 dias (3/10 .. 19/10) = 7 (W-1) + 7 (W0, el 13 es la pausa: '-') + 3 (17, 18 y hoy)
CREATE TEMP TABLE m_ana (clave text PRIMARY KEY, dias text);
INSERT INTO m_ana VALUES
 ('AGUA',      '1110111' || '111-101' || '111'),
 ('JUGO',      '1101110' || '110-110' || '100'),
 ('RITUAL',    '1111101' || '111-011' || '110'),
 ('PASTILLA',  '1011011' || '011-101' || '110'),
 ('POST',      '1101111' || '110-111' || '010'),
 ('DESPERTAR', '1111011' || '101-110' || '111');

-- Los demas: cuantos de los 6 habitos estan cumplidos por dia (en orden); -1 = sin registro
CREATE TEMP TABLE m_otros (caso text PRIMARY KEY, cuentas int[]);
INSERT INTO m_otros VALUES
 ('LUISA',   ARRAY[5,5,6,5,4,5,5,  5,4,5,5,6,5,5,  5,5,1]),
 ('ROCIO',   ARRAY[-1,-1,-1,3,2,3,3, 3,2,4,3,3,2,3, 3,3,2]),
 ('VALERIA', ARRAY[-1,-1,-1,6,6,5,6, 6,6,5,6,6,5,6, 6,6,3]),
 ('PRUEBA',  ARRAY[-1,-1,-1,4,4,4,5, 5,4,4,4,5,5,4, 4,4,2]),
 ('CERRADA', ARRAY[-1,-1,-1,4,4,4,5, 5,4,4,4,5,5,4, 4,4,2]),
 ('BETO',    ARRAY[-1,-1,-1,2,3,3,2, 3,3,2,3,3,2,3, 3,2,1]),
 ('CARLA',   ARRAY[-1,-1,-1,6,5,6,6, 6,5,6,6,6,5,6, 6,6,2]),
 ('GABRIELA',ARRAY[-1,-1,-1,-1,-1,-1,-1, 4,5,5,5,4,5,5, 5,4,2]),
 ('HUGO',    ARRAY[-1,-1,-1,5,4,5,5, 4,5,5,4,5,5,5, 5,4,2]);
CREATE TEMP TABLE orden6 (ix int PRIMARY KEY, clave text);
INSERT INTO orden6 VALUES (1,'AGUA'),(2,'JUGO'),(3,'COMIDA1'),(4,'RITUAL'),(5,'COMIDA2'),(6,'NOCHE');

CREATE TEMP TABLE reg_tmp (participante uuid, habito text, fecha date, marca text);
INSERT INTO reg_tmp (participante, habito, fecha, marca)
SELECT c.id, o.clave, (DATE '2026-10-03' + (d.i - 1)), CASE WHEN o.ix <= mo.cuentas[d.i] THEN '1' ELSE '0' END
  FROM m_otros mo JOIN cuenta c ON c.caso = mo.caso
  CROSS JOIN generate_series(1, 17) d(i) CROSS JOIN orden6 o
 WHERE mo.cuentas[d.i] >= 0 AND (DATE '2026-10-03' + (d.i - 1)) >= c.fecha_inicio;
INSERT INTO reg_tmp (participante, habito, fecha, marca)
SELECT c.id, ma.clave, (DATE '2026-10-03' + (d.i - 1)), substr(ma.dias, d.i, 1)
  FROM m_ana ma CROSS JOIN cuenta c CROSS JOIN generate_series(1, 17) d(i)
 WHERE c.caso = 'ANA' AND substr(ma.dias, d.i, 1) IN ('0', '1');
INSERT INTO reg_tmp (participante, habito, fecha, marca)
SELECT c.id, 'AUDIO', f.fecha, '1' FROM cuenta c,
       (VALUES (DATE '2026-10-04'), (DATE '2026-10-11'), (DATE '2026-10-18')) f(fecha) WHERE c.caso = 'ANA';
INSERT INTO reg_tmp (participante, habito, fecha, marca)
SELECT c.id, o.clave, p.hoy, CASE WHEN o.ix <= 1 THEN '1' ELSE '0' END
  FROM cuenta c, orden6 o, param p WHERE c.caso = 'INES';

INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa, tipo_dia, es_opcional,
                                      estado, puntos_otorgados, respuesta_texto, completado_en, creado_en, actualizado_en)
SELECT md5('reg|' || r.participante || '|' || r.habito || '|' || r.fecha)::uuid, r.participante, h.id, r.fecha,
       LEAST(90, GREATEST(0, r.fecha - c.fecha_inicio + 1)),
       CAST(CASE WHEN extract(isodow FROM r.fecha) = 7 THEN 'DOMINGO' ELSE 'DISCIPLINA' END AS renaser.tipo_dia),
       false,
       CAST(CASE WHEN r.marca = '1' THEN 'COMPLETADO' WHEN r.fecha < p.hoy THEN 'EXPIRADO' ELSE 'PENDIENTE' END AS renaser.estado_registro),
       CASE WHEN r.marca = '1' THEN 5 ELSE 0 END,
       CASE WHEN r.marca <> '1' THEN NULL
            WHEN r.habito = 'RITUAL'   THEN (ARRAY['Me sentí con mucha energía','Un poco cansada pero firme','Tranquila y enfocada','Agradecida por empezar el día'])[1 + (extract(day FROM r.fecha)::int % 4)]
            WHEN r.habito = 'PASTILLA' THEN 'Hoy me quedó claro que ' || (ARRAY['el descanso también es disciplina','cuidar mi energía me ordena el día','empezar pequeño me sostiene'])[1 + (extract(day FROM r.fecha)::int % 3)]
            WHEN r.habito = 'AUDIO'    THEN 'Escuché la audioterapia completa: sentí calma en el pecho y ganas de seguir.' END,
       CASE WHEN r.marca = '1' THEN (r.fecha::timestamp + interval '7 hours 30 minutes') AT TIME ZONE 'America/Lima' END,
       (r.fecha::timestamp + interval '5 minutes') AT TIME ZONE 'America/Lima', now()
  FROM reg_tmp r JOIN cuenta c ON c.id = r.participante JOIN hab h ON h.clave = r.habito CROSS JOIN param p;

-- Evidencias de Ana: foto en los obligatorios cumplidos, texto en la audioterapia
INSERT INTO renaser.evidencias (id, participante_id, registro_habito_id, tipo, bucket, ruta_storage, subida_en, estado_validacion, es_principal)
SELECT md5('ev|' || rh.id)::uuid, rh.participante_id, rh.id, 'FOTO', 'renaser-files',
       'evidencia-habitos/' || rh.participante_id || '/' || rh.id || '/' || md5('ev|' || rh.id)::uuid,
       rh.completado_en + interval '2 minutes', 'VALIDA', false
  FROM renaser.registros_habito rh JOIN renaser.habitos h ON h.id = rh.habito_id
 WHERE rh.participante_id = (SELECT id FROM cuenta WHERE caso = 'ANA')
   AND h.clave_sistema IN ('WARM_LEMON_WATER', 'GREEN_JUICE', 'RITUAL_MORNING') AND rh.estado = 'COMPLETADO';
INSERT INTO renaser.evidencias (id, participante_id, registro_habito_id, tipo, contenido_texto, subida_en, estado_validacion, es_principal)
SELECT md5('ev|' || rh.id)::uuid, rh.participante_id, rh.id, 'TEXTO', rh.respuesta_texto, rh.completado_en + interval '1 minute', 'VALIDA', false
  FROM renaser.registros_habito rh JOIN renaser.habitos h ON h.id = rh.habito_id
 WHERE rh.participante_id = (SELECT id FROM cuenta WHERE caso = 'ANA') AND h.clave_sistema = 'AUDIO_THERAPY_WEEKLY' AND rh.estado = 'COMPLETADO';

-- Post diario de Ana: una publicacion del Muro por dia cumplido (la ficha la deriva)
INSERT INTO renaser.publicaciones_muro (id, autor_id, tipo, categoria_clave, texto, creado_en, actualizado_en, dia_programa)
SELECT md5('post|' || rh.id)::uuid, rh.participante_id, 'MANUAL', 'REVELACIONES',
       'Día ' || rh.dia_programa || ': hoy completé mi ritual y me comprometo a seguir. ' || (ARRAY['Gracias, tribu.','Un paso a la vez.','La constancia gana.'])[1 + (rh.dia_programa % 3)],
       (rh.fecha_ejecucion::timestamp + interval '15 hours') AT TIME ZONE 'America/Lima',
       (rh.fecha_ejecucion::timestamp + interval '15 hours') AT TIME ZONE 'America/Lima', rh.dia_programa
  FROM renaser.registros_habito rh JOIN renaser.habitos h ON h.id = rh.habito_id
 WHERE rh.participante_id = (SELECT id FROM cuenta WHERE caso = 'ANA') AND h.clave_sistema = 'COMMUNITY_POST' AND rh.estado = 'COMPLETADO';
INSERT INTO renaser.medias_publicacion (publicacion_id, bucket, ruta_storage, mime, orden)
SELECT p.id, 'wall', 'muro/fotos/' || p.autor_id || '/' || md5('media|' || p.id)::uuid, 'image/jpeg', 0
  FROM renaser.publicaciones_muro p
 WHERE p.autor_id = (SELECT id FROM cuenta WHERE caso = 'ANA') AND (p.creado_en AT TIME ZONE 'America/Lima')::date = DATE '2026-10-14';

-- Acciones del dia de Ana: una cumplida con foto (14/10), una sin cumplir (15/10), una de hoy
INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje, completada, completada_en, puntos_otorgados)
SELECT md5('roca|' || f.fecha)::uuid, c.id, f.fecha, 1, f.titulo, 'VERDE', 8, 'TRABAJO', f.hecha,
       CASE WHEN f.hecha THEN (f.fecha::timestamp + interval '11 hours') AT TIME ZONE 'America/Lima' END, CASE WHEN f.hecha THEN 10 ELSE 0 END
  FROM cuenta c,
       (VALUES (DATE '2026-10-14', 'Llamar a 3 clientes', true), (DATE '2026-10-15', 'Enviar la propuesta a Marta', false),
               (DATE '2026-10-19', 'Preparar la reunión del martes', false)) f(fecha, titulo, hecha)
 WHERE c.caso = 'ANA';
INSERT INTO renaser.acciones_diarias (roca_diaria_id, orden, descripcion)
SELECT r.id, 1, 'Lista de contactos' FROM renaser.rocas_diarias r WHERE r.participante_id = (SELECT id FROM cuenta WHERE caso = 'ANA');
INSERT INTO renaser.evidencias (id, participante_id, roca_diaria_id, tipo, bucket, ruta_storage, subida_en, estado_validacion, es_principal)
SELECT md5('ev|' || r.id)::uuid, r.participante_id, r.id, 'FOTO', 'renaser-files',
       'evidencia-rocas/' || r.participante_id || '/' || r.id || '/' || md5('ev|' || r.id)::uuid,
       r.completada_en + interval '1 minute', 'VALIDA', true
  FROM renaser.rocas_diarias r WHERE r.participante_id = (SELECT id FROM cuenta WHERE caso = 'ANA') AND r.completada;

-- Semaforo: dias cerrados derivados de lo sembrado (la misma cuenta que el barrido)
INSERT INTO renaser.semaforo_dias (participante_id, fecha, habitos_programados, habitos_cumplidos, objetivos_programados, objetivos_cumplidos, calculado_en)
SELECT r.participante_id, r.fecha_ejecucion,
       count(*) - count(*) FILTER (WHERE r.es_opcional AND r.estado <> 'COMPLETADO'),
       count(*) FILTER (WHERE r.estado = 'COMPLETADO'),
       COALESCE((SELECT count(*) FROM renaser.rocas_diarias ro WHERE ro.participante_id = r.participante_id AND ro.fecha = r.fecha_ejecucion), 0),
       COALESCE((SELECT count(*) FROM renaser.rocas_diarias ro WHERE ro.participante_id = r.participante_id AND ro.fecha = r.fecha_ejecucion AND ro.completada), 0),
       now()
  FROM renaser.registros_habito r CROSS JOIN param p
 WHERE r.participante_id IN (SELECT id FROM cuenta) AND r.fecha_ejecucion < p.hoy
 GROUP BY r.participante_id, r.fecha_ejecucion;
INSERT INTO renaser.semaforo_dias (participante_id, fecha, habitos_programados, habitos_cumplidos, objetivos_programados, objetivos_cumplidos, calculado_en)
SELECT c.id, d::date, 0, 0, 0, 0, now() FROM cuenta c CROSS JOIN param p
  CROSS JOIN LATERAL generate_series(c.fecha_inicio::timestamp, (p.hoy - 1)::timestamp, interval '1 day') d
 WHERE c.caso = 'INES';

-- Pausa de Ana el martes 13/10
INSERT INTO renaser.semaforo_pausas (id, usuario_id, motivo, desde, hasta, creada_en)
SELECT gen_random_uuid(), c.id, 'PEDIDA_POR_LA_PERSONA', DATE '2026-10-13', DATE '2026-10-13', timestamptz '2026-10-12 21:00-05'
  FROM cuenta c WHERE c.caso = 'ANA';

SELECT 'registros' dato, count(*)::text FROM renaser.registros_habito WHERE participante_id IN (SELECT id FROM cuenta)
UNION ALL SELECT 'evidencias', count(*)::text FROM renaser.evidencias WHERE participante_id IN (SELECT id FROM cuenta)
UNION ALL SELECT 'publicaciones', count(*)::text FROM renaser.publicaciones_muro WHERE autor_id IN (SELECT id FROM cuenta)
UNION ALL SELECT 'semaforo_dias', count(*)::text FROM renaser.semaforo_dias WHERE participante_id IN (SELECT id FROM cuenta)
UNION ALL SELECT 'dia de Lima', (SELECT hoy::text FROM param);
COMMIT;
