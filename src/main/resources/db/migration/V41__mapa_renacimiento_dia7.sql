-- ============================================================================
-- Mapa de Renacimiento (Dia 7) — se monta sobre el motor de onboarding que YA existe.
--
-- POR QUE SE TOCA LA BASE
-- -----------------------
-- Hoy el Mapa de Renacimiento no persiste en ningun lado: vive entero en el AsyncStorage
-- del telefono (`renaser.mapa-renacimiento.v1.<usuario>`), asi que un aprendiz que borra la
-- app o cambia de equipo PIERDE su mapa completo — los tres objetivos, las acciones, los
-- reemplazos, los nueve hitos y el protocolo de retorno. Ver
-- docs/PLAN_MAPA_RENACIMIENTO_BACKEND.md §0.
--
-- POR QUE NO SE CREA UN MODULO NI CINCO TABLAS NUEVAS
-- --------------------------------------------------
-- `onboarding` NO es "el cuestionario del Dia 0": es un motor de formularios con `flujo`
-- como columna, y V10 ya sembro CINCO flujos en la misma maquinaria (terminos, pacto,
-- ficha_inicial, cuestionario_profundo, diseno_destino). El mapa es el SEXTO. Todo lo que
-- es "una pregunta con una respuesta" —los tres objetivos, los nueve hitos, la prioridad,
-- el retorno y el compromiso— entra en `preguntas_onboarding`/`respuestas_onboarding` sin
-- una linea de Java nueva. Decision del dueno del proyecto, 2026-09-08.
--
-- ESTA MIGRACION NO TOCA UNA SOLA FILA DE LO YA CONSTRUIDO. Solo INSERTA filas con
-- flujo = 'mapa_dia7' y crea tres tablas nuevas. `ficha_inicial`, `terminos`, `pacto`,
-- `cuestionario_profundo` y `diseno_destino` quedan exactamente como estaban.
--
-- POR QUE ESTAS TRES TABLAS Y NO MAS
-- ----------------------------------
-- 1. `acciones_mapa` (+ `dias_accion_mapa`) — `respuestas_onboarding` tiene UNIQUE (usuario_id, pregunta_id): UNA
--    respuesta por pregunta. Las 1-6 acciones motoras son una lista de objetos, no un valor.
--    Ademas cada accion necesita recordar el habito que genero al activar, y eso es una FK
--    REAL a `habitos` — una FK no cabe dentro de un `valor_json`. Es justo lo que hace
--    idempotente la activacion (AC-07 del manual): el UNIQUE (usuario_id, accion_id) hace
--    que el segundo toque de "Activar" choque en vez de crear un habito duplicado.
-- 2. `protocolos_reemplazo_mapa` — misma razon de lista que arriba. Se le da tabla y no un
--    `valor_json` para que el panel del mentor (§6 del manual) pueda leerlos sin parsear
--    JSON en la capa de aplicacion. Sin FK a otra tabla: son cuatro textos.
-- 3. `etapas_onboarding_completadas` — `estado_onboarding` es UNA fila por usuario con UN
--    `flujo_actual` y UN `completado`, y los cinco flujos existentes ya la comparten. Marcar
--    el mapa ahi mezclaria "termino el Dia 0" con "termino el mapa", que es justo lo que la
--    pantalla "Tu proceso completo · 1 de 2 etapas" necesita distinguir. El pendiente ya
--    estaba anotado en docs/PENDIENTES_2026-09-05.md §3.5: "necesita una marca por etapa en
--    el backend (...) las etapas 3, 4 y 5 van a necesitar el mismo mecanismo — conviene
--    hacerlo una vez". Esta es esa vez, y por eso la tabla es POR FLUJO y no una columna
--    `mapa_completado_en`: una columna por etapa obliga a migrar cada vez que aparece una.
--
-- Los maximos del manual (6 acciones, 2 por objetivo, 1-3 patrones) NO van como CHECK:
-- dependen de OTRAS filas, asi que los impone el dominio (regla 04).
--
-- `seccion_id`/`pregunta_id` son IDENTITY: se resuelven con INSERT..SELECT + JOIN por la
-- clave natural, nunca asumiendo el orden de insercion. Mismo criterio que V10.
-- ============================================================================

BEGIN;

SET search_path TO renaser, public;

-- ----------------------------------------------------------------------------
-- 1. Las secciones del flujo (una por vista del manual que capture datos)
-- ----------------------------------------------------------------------------
INSERT INTO secciones_onboarding (flujo, clave_seccion, titulo, descripcion, orden) VALUES
    ('mapa_dia7', 'prioridad',           'Tu prioridad',                 'V02 — el area que manda, sin excluir a las otras', 0),
    ('mapa_dia7', 'objetivo_salud',      'Objetivo de Salud',            'V03', 1),
    ('mapa_dia7', 'objetivo_negocio',    'Objetivo de Negocio y Dinero', 'V04', 2),
    ('mapa_dia7', 'objetivo_relaciones', 'Objetivo de Relaciones',       'V05', 3),
    ('mapa_dia7', 'sistema_ejecucion',   'Tu sistema de ejecucion',      'V06 — las acciones motoras viven en acciones_mapa', 4),
    ('mapa_dia7', 'reemplazos',          'Protocolos de reemplazo',      'V07 — viven en protocolos_reemplazo_mapa', 5),
    ('mapa_dia7', 'hitos',               'Hitos 30 / 60 / 90',           'V08', 6),
    ('mapa_dia7', 'retorno',             'Protocolo de retorno',         'V09', 7),
    ('mapa_dia7', 'activacion',          'Activacion',                   'V10', 8);

-- ----------------------------------------------------------------------------
-- 2. Las preguntas. `clave_pregunta` es UNIQUE GLOBAL en la tabla, de ahi el prefijo `map_`.
--    Vocabulario en ingles: es la convencion del modulo (wire en ingles, dominio en espaniol).
-- ----------------------------------------------------------------------------
INSERT INTO preguntas_onboarding (clave_pregunta, texto, tipo, config_escala, requerida, orden, seccion_id)
SELECT v.clave_pregunta, v.texto, v.tipo, v.config_escala, v.requerida, v.orden, s.id
FROM (VALUES
    -- V02
    ('map_priority_area'::text, '¿Que area manda en tus proximos 90 dias?'::text, 'SELECCION_UNICA'::tipo_pregunta_onboarding, NULL::jsonb, true::boolean, 0::smallint, 'prioridad'::text),
    -- V03 · salud
    ('map_health_result_type',      '¿Que resultado de salud vas a mover?',                'SELECCION_UNICA', NULL, true,  0, 'objetivo_salud'),
    ('map_health_baseline',         'Tu linea base HOY',                                    'TEXTO',           NULL, true,  1, 'objetivo_salud'),
    ('map_health_target_day90',     'Tu resultado al Dia 90',                               'TEXTO',           NULL, true,  2, 'objetivo_salud'),
    ('map_health_unit',             'Unidad de medida',                                     'TEXTO',           NULL, true,  3, 'objetivo_salud'),
    ('map_health_evidence',         '¿Con que lo vas a evidenciar?',                        'TEXTO',           NULL, true,  4, 'objetivo_salud'),
    ('map_health_reason',           '¿Por que este resultado y no otro?',                   'AREA_TEXTO',      NULL, true,  5, 'objetivo_salud'),
    ('map_health_goal_text',        'Tu meta redactada',                                    'AREA_TEXTO',      NULL, false, 6, 'objetivo_salud'),
    ('map_health_goal_edited',      'La meta redactada fue editada a mano',                 'CASILLA',         NULL, false, 7, 'objetivo_salud'),
    -- V04 · negocio y dinero
    ('map_business_result_type',    '¿Que resultado de negocio o dinero vas a mover?',      'SELECCION_UNICA', NULL, true,  0, 'objetivo_negocio'),
    ('map_business_baseline',       'Tu linea base HOY',                                    'TEXTO',           NULL, true,  1, 'objetivo_negocio'),
    ('map_business_target_day90',   'Tu resultado al Dia 90',                               'TEXTO',           NULL, true,  2, 'objetivo_negocio'),
    ('map_business_currency',       'Moneda',                                               'TEXTO',           NULL, true,  3, 'objetivo_negocio'),
    ('map_business_period',         'Periodo de medicion',                                  'SELECCION_UNICA', NULL, true,  4, 'objetivo_negocio'),
    ('map_business_evidence',       '¿Con que lo vas a evidenciar?',                        'TEXTO',           NULL, true,  5, 'objetivo_negocio'),
    ('map_business_reason',         '¿Por que este resultado y no otro?',                   'AREA_TEXTO',      NULL, true,  6, 'objetivo_negocio'),
    ('map_business_goal_text',      'Tu meta redactada',                                    'AREA_TEXTO',      NULL, false, 7, 'objetivo_negocio'),
    ('map_business_goal_edited',    'La meta redactada fue editada a mano',                 'CASILLA',         NULL, false, 8, 'objetivo_negocio'),
    -- V05 · relaciones. Escala 1-10, mismo formato de config_escala que V10.
    ('map_relations_bond',            '¿Que vinculo vas a trabajar?',                        'SELECCION_UNICA', NULL, true,  0, 'objetivo_relaciones'),
    ('map_relations_baseline_scale',  'Como esta ese vinculo HOY (1-10)',                    'ESCALA', '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, true, 1, 'objetivo_relaciones'),
    ('map_relations_target_scale',    'Como quiere estar al Dia 90 (1-10)',                  'ESCALA', '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, true, 2, 'objetivo_relaciones'),
    ('map_relations_observable_change', '¿Que vas a hacer VOS distinto? (conducta propia)',   'AREA_TEXTO',      NULL, true,  3, 'objetivo_relaciones'),
    ('map_relations_evidence',        '¿Con que lo vas a evidenciar?',                       'TEXTO',           NULL, true,  4, 'objetivo_relaciones'),
    ('map_relations_reason',          '¿Por que este vinculo y no otro?',                    'AREA_TEXTO',      NULL, true,  5, 'objetivo_relaciones'),
    ('map_relations_goal_text',       'Tu meta redactada',                                   'AREA_TEXTO',      NULL, false, 6, 'objetivo_relaciones'),
    ('map_relations_goal_edited',     'La meta redactada fue editada a mano',                'CASILLA',         NULL, false, 7, 'objetivo_relaciones'),
    -- V08 · nueve hitos: tres areas x tres cortes
    ('map_milestone_health_30',     'Hito de salud al dia 30',                              'TEXTO', NULL, false, 0, 'hitos'),
    ('map_milestone_health_60',     'Hito de salud al dia 60',                              'TEXTO', NULL, false, 1, 'hitos'),
    ('map_milestone_health_90',     'Hito de salud al dia 90',                              'TEXTO', NULL, false, 2, 'hitos'),
    ('map_milestone_business_30',   'Hito de negocio al dia 30',                            'TEXTO', NULL, false, 3, 'hitos'),
    ('map_milestone_business_60',   'Hito de negocio al dia 60',                            'TEXTO', NULL, false, 4, 'hitos'),
    ('map_milestone_business_90',   'Hito de negocio al dia 90',                            'TEXTO', NULL, false, 5, 'hitos'),
    ('map_milestone_relations_30',  'Hito de relaciones al dia 30',                         'TEXTO', NULL, false, 6, 'hitos'),
    ('map_milestone_relations_60',  'Hito de relaciones al dia 60',                         'TEXTO', NULL, false, 7, 'hitos'),
    ('map_milestone_relations_90',  'Hito de relaciones al dia 90',                         'TEXTO', NULL, false, 8, 'hitos'),
    -- V09 y V10
    ('map_return_protocol',         '¿Cual es tu accion minima para volver en menos de 24 h?', 'AREA_TEXTO', NULL, true, 0, 'retorno'),
    ('map_followup_commitment',     'Me comprometo a reportar mi avance y a dejarme mentorear', 'CASILLA',    NULL, true, 0, 'activacion')
) AS v (clave_pregunta, texto, tipo, config_escala, requerida, orden, clave_seccion)
JOIN secciones_onboarding s ON s.flujo = 'mapa_dia7' AND s.clave_seccion = v.clave_seccion;

-- ----------------------------------------------------------------------------
-- 3. Opciones de las preguntas de seleccion unica. Los valores son los del cliente
--    (`tipos.ts`), para que la app no tenga que traducir nada.
-- ----------------------------------------------------------------------------
INSERT INTO opciones_pregunta (pregunta_id, orden, valor, etiqueta)
SELECT p.id, v.orden, v.valor, v.etiqueta
FROM (VALUES
    ('map_priority_area'::text, 0::smallint, 'salud'::text,            'Salud'::text),
    ('map_priority_area', 1, 'negocio_dinero',   'Negocio y dinero'),
    ('map_priority_area', 2, 'relaciones',       'Relaciones'),

    ('map_health_result_type', 0, 'peso',              'Peso'),
    ('map_health_result_type', 1, 'medidas',           'Medidas'),
    ('map_health_result_type', 2, 'energia',           'Energia'),
    ('map_health_result_type', 3, 'fuerza',            'Fuerza'),
    ('map_health_result_type', 4, 'resistencia',       'Resistencia'),
    ('map_health_result_type', 5, 'sueno',             'Sueno'),
    ('map_health_result_type', 6, 'condicion_clinica', 'Condicion clinica'),
    ('map_health_result_type', 7, 'otro',              'Otro'),

    ('map_business_result_type', 0, 'facturacion',      'Facturacion'),
    ('map_business_result_type', 1, 'utilidad',         'Utilidad'),
    ('map_business_result_type', 2, 'ventas',           'Ventas'),
    ('map_business_result_type', 3, 'clientes',         'Clientes'),
    ('map_business_result_type', 4, 'ahorro',           'Ahorro'),
    ('map_business_result_type', 5, 'deuda',            'Deuda'),
    ('map_business_result_type', 6, 'ingreso_personal', 'Ingreso personal'),
    ('map_business_result_type', 7, 'otro',             'Otro'),

    ('map_business_period', 0, 'semanal',           'Semanal'),
    ('map_business_period', 1, 'mensual',           'Mensual'),
    ('map_business_period', 2, 'acumulado_dia_90',  'Acumulado al dia 90'),

    ('map_relations_bond', 0, 'pareja',    'Pareja'),
    ('map_relations_bond', 1, 'hijos',     'Hijos'),
    ('map_relations_bond', 2, 'padres',    'Padres'),
    ('map_relations_bond', 3, 'familia',   'Familia'),
    ('map_relations_bond', 4, 'socios',    'Socios'),
    ('map_relations_bond', 5, 'equipo',    'Equipo'),
    ('map_relations_bond', 6, 'amistades', 'Amistades'),
    ('map_relations_bond', 7, 'otro',      'Otro')
) AS v (clave_pregunta, orden, valor, etiqueta)
JOIN preguntas_onboarding p ON p.clave_pregunta = v.clave_pregunta;

-- ----------------------------------------------------------------------------
-- 4. Las acciones motoras (V06). Lista, y con FK real al habito que generan al activar.
-- ----------------------------------------------------------------------------
CREATE TABLE acciones_mapa (
    id                  uuid        PRIMARY KEY,
    usuario_id          uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    -- El id que ya genera el cliente para cada accion. Es la mitad util del UNIQUE de abajo:
    -- sin el, reintentar "Activar" tras un timeout de red duplicaria los habitos.
    accion_id           text        NOT NULL,
    area                text        NOT NULL CHECK (area IN ('salud', 'negocio_dinero', 'relaciones')),
    texto               text        NOT NULL CHECK (char_length(texto) BETWEEN 5 AND 100),
    -- Los que la persona DECLARO hacer por semana. Se guarda ademas de `dias_accion_mapa` porque
    -- son dos hechos distintos: cuantos se propuso y cuales eligio. Que coincidan lo verifica el
    -- dominio (depende de otras filas, no es un CHECK).
    frecuencia_semanal  smallint    NOT NULL CHECK (frecuencia_semanal BETWEEN 1 AND 7),
    momento             text        CHECK (momento IS NULL OR momento IN ('manana', 'tarde', 'noche')),
    evidencia           text        CHECK (evidencia IS NULL OR evidencia IN ('check', 'foto', 'video', 'registro', 'documento', 'otro')),
    -- Se llena SOLO al activar. NULL = la accion existe en el mapa pero todavia no es un habito.
    habito_id           uuid        REFERENCES habitos (id) ON DELETE SET NULL,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    actualizado_en      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, accion_id)
);

CREATE INDEX idx_acciones_mapa_usuario ON acciones_mapa (usuario_id);

-- Los dias en que corre cada accion, UNA FILA POR DIA y no un `text[]`. El baseline ya habia
-- rechazado los arrays por este mismo motivo (ver el COMMENT de `roles_permitidos_curso`: "Antes
-- array text[] duplicando el concepto"), y todo el esquema modela el dia de semana igual:
-- `smallint` 1..7 en la PK, como `horario_semanal_habito` (V39). 1 = lunes, 7 = domingo (ISO).
CREATE TABLE dias_accion_mapa (
    accion_mapa_id uuid     NOT NULL REFERENCES acciones_mapa (id) ON DELETE CASCADE,
    dia_semana     smallint NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    PRIMARY KEY (accion_mapa_id, dia_semana)
);

-- ----------------------------------------------------------------------------
-- 5. Los protocolos de reemplazo (V07). "Cuando [disparador], en lugar de [conducta],
--    hare [respuesta]." Cuatro textos, sin FK: tabla y no jsonb para que el panel del
--    mentor los lea en SQL.
-- ----------------------------------------------------------------------------
CREATE TABLE protocolos_reemplazo_mapa (
    id                    uuid        PRIMARY KEY,
    usuario_id            uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    protocolo_id          text        NOT NULL,
    patron                text        NOT NULL,
    disparador            text        NOT NULL,
    conducta_actual       text        NOT NULL,
    respuesta_alternativa text        NOT NULL,
    creado_en             timestamptz NOT NULL DEFAULT now(),
    actualizado_en        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, protocolo_id)
);

CREATE INDEX idx_protocolos_reemplazo_mapa_usuario ON protocolos_reemplazo_mapa (usuario_id);

-- ----------------------------------------------------------------------------
-- 6. La marca de etapa completada, POR FLUJO. Sirve al mapa y a las etapas 3, 4 y 5 que
--    vengan despues, sin una migracion por etapa (docs/PENDIENTES_2026-09-05.md §3.5).
-- ----------------------------------------------------------------------------
CREATE TABLE etapas_onboarding_completadas (
    usuario_id    uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    flujo         text        NOT NULL,
    completado_en timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, flujo)
);

COMMIT;
