-- Catalogo de onboarding (secciones, preguntas, opciones de seleccion unica y config de escala).
-- El modulo Java `onboarding` ya esta construido completo (LoadCuestionarioPort, dominio de
-- cuestionario/respuesta/media/grabacion-v90) pero su propia documentacion deja explicito que el
-- catalogo queda vacio "fase futura de migracion de datos" (docs/MODULO_ONBOARDING.md, decision
-- D-O9) -- esta migracion es exactamente esa fase.
--
-- Perfilado antes de cargar (misma disciplina que V4-V9):
--   - 27 secciones / 192 preguntas en origen, en 7 flujos: terminos, pacto, ficha_inicial,
--     cuestionario_profundo, las_90_variables, diseno_destino, cierre_dia_1.
--   - 0 preguntas huerfanas (todas su (flow,section_key) matchea una seccion real).
--   - 0 clave_pregunta duplicadas, 0 usan parent_question_key (condicionales) ni
--     validation_rules -- no hizo falta resolver remapeo de claves a ids ni reglas jsonb.
--   - 18 preguntas con `options`: 13 son escalas 1-10 (van a config_escala) y 5 son
--     single_select reales con opciones de texto (van a opciones_pregunta).
--   - Se verifico uso real cruzando contra `onboarding_answers`/`variables_90_recordings` del
--     dump (no se migran esas tablas, son dato de usuario -- el cruce fue solo para decidir que
--     migrar): terminos, pacto, ficha_inicial, cuestionario_profundo, diseno_destino y
--     cierre_dia_1 tienen uso real casi al 100%. `las_90_variables` (90 de las 192 preguntas,
--     el 47% del catalogo) SI tiene uso real -- 221 grabaciones de 17 usuarios distintos, las 90
--     claves cubiertas -- pero **se excluye de esta carga a pedido explicito del dueno del
--     proyecto**: el cliente movil tiene esa pantalla hardcodeada (no lee `preguntas_onboarding`
--     para armar esos 90 pasos) y solo sube la grabacion a `variables_90_recordings`; cargar esas
--     90 filas seria catalogo que el cliente actual no consulta. Si el cliente cambia a leer el
--     catalogo para esa pantalla, es una migracion aparte.
--
-- Mapeo de tipos aplicado (confirmado 1:1 por el propio comentario del schema nuevo, columna
-- `tipo_pregunta_onboarding` en BD_NUEVA_V1.sql -- no inventado):
--   text->TEXTO, textarea->AREA_TEXTO, number->NUMERO, scale->ESCALA,
--   single_select->SELECCION_UNICA, audio->AUDIO, signature->FIRMA, checkbox->CASILLA,
--   date->FECHA, file_upload->ARCHIVO. (multi_select->SELECCION_MULTIPLE no tiene filas de
--   origen en el alcance migrado, no se usa aca.)
--
-- `seccion_id`/`pregunta_id` son IDENTITY: se resuelven con INSERT..SELECT + JOIN por la clave
-- natural (flujo+clave_seccion / clave_pregunta), nunca asumiendo que el id autogenerado
-- coincide con el orden de insercion.

BEGIN;

SET search_path TO renaser, public;

-- ============================================================================
-- SECCIONES_ONBOARDING (18 de 27 en origen -- ver exclusion de las_90_variables arriba)
-- ============================================================================

INSERT INTO secciones_onboarding (flujo, clave_seccion, titulo, descripcion, orden, creado_en) VALUES
    ('terminos', 'aceptacion', 'Aceptación', NULL, 0, '2026-05-20 17:04:23.448229+00'),
    ('pacto', 'firma', 'Firma del Pacto', NULL, 0, '2026-05-20 17:04:23.448229+00'),
    ('ficha_inicial', 'identidad_operativa', 'Identidad Operativa', NULL, 0, '2026-05-20 17:04:23.448229+00'),
    ('ficha_inicial', 'cuerpo', 'Cuerpo', NULL, 1, '2026-05-20 17:04:23.448229+00'),
    ('ficha_inicial', 'mente_y_patrones', 'Mente y Patrones', NULL, 2, '2026-05-20 17:04:23.448229+00'),
    ('ficha_inicial', 'alma_heridas_vinculos', 'Alma, Heridas y Vínculos', NULL, 3, '2026-05-20 17:04:23.448229+00'),
    ('ficha_inicial', 'negocio_y_dinero', 'Negocio y Dinero', NULL, 4, '2026-05-20 17:04:23.448229+00'),
    ('ficha_inicial', 'compromiso_y_cierre', 'Compromiso y Cierre', NULL, 5, '2026-05-20 17:04:23.448229+00'),
    ('cuestionario_profundo', 'energia_vital', 'Energía Vital', NULL, 0, '2026-05-20 17:04:23.448229+00'),
    ('cuestionario_profundo', 'guardianes_emocionales', 'Los 3 Guardianes', NULL, 1, '2026-05-20 17:04:23.448229+00'),
    ('cuestionario_profundo', 'estado_mental', 'Estado Mental Profundo', NULL, 2, '2026-05-20 17:04:23.448229+00'),
    ('cuestionario_profundo', 'estado_somatico', 'Estado Somático', NULL, 3, '2026-05-20 17:04:23.448229+00'),
    ('cuestionario_profundo', 'estado_emocional', 'Estado Emocional Profundo', NULL, 4, '2026-05-20 17:04:23.448229+00'),
    ('cuestionario_profundo', 'manifestacion', 'Cierre y Manifestación', NULL, 5, '2026-05-20 17:04:23.448229+00'),
    ('diseno_destino', 'rocas_maestras', 'Tus 3 Rocas Maestras', NULL, 0, '2026-05-20 17:04:23.448229+00'),
    ('diseno_destino', 'destino_90d', 'Destino a 90 días', NULL, 1, '2026-05-20 17:04:23.448229+00'),
    ('cierre_dia_1', 'bautizo', 'Bautizo del Proceso', NULL, 0, '2026-05-20 17:04:23.448229+00'),
    ('cierre_dia_1', 'sincronizacion', 'Sincronización de Rocas', NULL, 1, '2026-05-20 17:04:23.448229+00');

-- ============================================================================
-- PREGUNTAS_ONBOARDING (102 de 192 en origen)
-- ============================================================================

INSERT INTO preguntas_onboarding (clave_pregunta, texto, tipo, config_escala, requerida, orden, seccion_id, creado_en)
SELECT v.clave_pregunta, v.texto, v.tipo, v.config_escala, v.requerida, v.orden, s.id, v.creado_en
FROM (VALUES
    ('accepted_terms'::text, 'He leído y acepto los Términos y Condiciones'::text, 'CASILLA'::tipo_pregunta_onboarding, NULL, true::boolean, 1::smallint, 'terminos'::text, 'aceptacion'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('participant_name'::text, 'Tu nombre completo'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 1::smallint, 'pacto'::text, 'firma'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('signature'::text, 'Firma con tu dedo'::text, 'FIRMA'::tipo_pregunta_onboarding, NULL, true::boolean, 2::smallint, 'pacto'::text, 'firma'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('accepted_pacto'::text, 'He leído el Pacto de Renacimiento en su totalidad. Lo firmo en pleno uso de mi consciencia'::text, 'CASILLA'::tipo_pregunta_onboarding, NULL, true::boolean, 3::smallint, 'pacto'::text, 'firma'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('country'::text, 'País'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 4::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('profession'::text, 'Profesión actual'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 6::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('expectations'::text, '¿Qué esperas concretamente de RENASER y de tu mentor?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 7::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('fears'::text, '¿Qué temes que NO funcione en este programa?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 8::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('weight_kg'::text, 'Peso (kg)'::text, 'NUMERO'::tipo_pregunta_onboarding, NULL, true::boolean, 9::smallint, 'ficha_inicial'::text, 'cuerpo'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('height_cm'::text, 'Estatura (cm)'::text, 'NUMERO'::tipo_pregunta_onboarding, NULL, true::boolean, 10::smallint, 'ficha_inicial'::text, 'cuerpo'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('sleep_hours'::text, 'Horas promedio de sueño'::text, 'NUMERO'::tipo_pregunta_onboarding, NULL, true::boolean, 11::smallint, 'ficha_inicial'::text, 'cuerpo'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('sleep_quality'::text, 'Calidad de tu sueño'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, true::boolean, 12::smallint, 'ficha_inicial'::text, 'cuerpo'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('medication'::text, 'Medicación regular'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 13::smallint, 'ficha_inicial'::text, 'cuerpo'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('body_smart_goal'::text, 'Objetivo SMART de Cuerpo a 90 días'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 14::smallint, 'ficha_inicial'::text, 'cuerpo'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('inner_critic_voice'::text, 'Cuando fallas, ¿qué te dice tu crítico interno?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 16::smallint, 'ficha_inicial'::text, 'mente_y_patrones'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('limiting_belief'::text, 'Tu creencia limitante #1 — la que cargas hace años'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 17::smallint, 'ficha_inicial'::text, 'mente_y_patrones'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('self_definition_today'::text, '¿Cómo te defines hoy, en una sola frase?'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 18::smallint, 'ficha_inicial'::text, 'mente_y_patrones'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('parental_phrase'::text, 'Frase de tu padre o madre que aún hoy te marca'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 21::smallint, 'ficha_inicial'::text, 'alma_heridas_vinculos'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('bond_father'::text, 'Tu vínculo HOY con tu padre (1-10)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, true::boolean, 22::smallint, 'ficha_inicial'::text, 'alma_heridas_vinculos'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('bond_mother'::text, 'Tu vínculo HOY con tu madre (1-10)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, true::boolean, 23::smallint, 'ficha_inicial'::text, 'alma_heridas_vinculos'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('money_childhood_phrase'::text, 'Frase sobre el dinero en tu infancia'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 24::smallint, 'ficha_inicial'::text, 'alma_heridas_vinculos'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('money_deserving_score'::text, '¿Sientes que mereces ganar mucho dinero? (1-10)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, true::boolean, 25::smallint, 'ficha_inicial'::text, 'alma_heridas_vinculos'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('money_deserving_reason'::text, '¿Por qué?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 26::smallint, 'ficha_inicial'::text, 'alma_heridas_vinculos'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('revenue_target_90d_usd'::text, 'Meta de facturación a 90 días (USD)'::text, 'NUMERO'::tipo_pregunta_onboarding, NULL, true::boolean, 27::smallint, 'ficha_inicial'::text, 'negocio_y_dinero'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('flagship_product'::text, 'Tu producto / servicio estrella'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 28::smallint, 'ficha_inicial'::text, 'negocio_y_dinero'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('ideal_client'::text, 'Tu cliente ideal'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 29::smallint, 'ficha_inicial'::text, 'negocio_y_dinero'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('business_enemy'::text, 'Tu Enemigo Público #1 del negocio'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 30::smallint, 'ficha_inicial'::text, 'negocio_y_dinero'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('business_smart_goal'::text, 'Objetivo SMART de Negocio a 90 días'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 31::smallint, 'ficha_inicial'::text, 'negocio_y_dinero'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('one_success_metric'::text, 'Si lograras UNA SOLA cosa que haga de esta formación un éxito, ¿cuál sería?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 33::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('process_baptism'::text, 'Bautizo del proceso'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 35::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('commitment_90days'::text, 'Me comprometo a completar los 90 días'::text, 'CASILLA'::tipo_pregunta_onboarding, NULL, true::boolean, 37::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('energy_drains'::text, '¿Qué actividad te DRENA más energía hoy?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 6::smallint, 'cuestionario_profundo'::text, 'energia_vital'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('energy_recharges'::text, '¿Qué actividad te RECARGA más?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 7::smallint, 'cuestionario_profundo'::text, 'energia_vital'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('last_fully_alive'::text, '¿Cuándo fue la última vez que te sentiste plenamente vivo/a?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 8::smallint, 'cuestionario_profundo'::text, 'energia_vital'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('whatsapp'::text, 'WhatsApp principal'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 2::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('full_name'::text, 'Nombre completo'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 1::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('desired_self'::text, '¿Quién quieres ser realmente?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 20::smallint, 'ficha_inicial'::text, 'mente_y_patrones'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('failure_cost'::text, 'El Costo del Fracaso'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 34::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('fear_intensity'::text, '¿Qué tan presente está el MIEDO en tu día a día? (1-10)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, true::boolean, 9::smallint, 'cuestionario_profundo'::text, 'guardianes_emocionales'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('fear_about'::text, '¿De qué tienes más miedo en este momento?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 10::smallint, 'cuestionario_profundo'::text, 'guardianes_emocionales'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('guilt_intensity'::text, '¿Qué tan presente está la CULPA en tu día a día? (1-10)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, true::boolean, 11::smallint, 'cuestionario_profundo'::text, 'guardianes_emocionales'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('guilt_about'::text, '¿Por qué cargas culpa? ¿Con quién o por qué?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 12::smallint, 'cuestionario_profundo'::text, 'guardianes_emocionales'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('shame_intensity'::text, '¿Qué tan presente está la VERGÜENZA en tu día a día? (1-10)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, true::boolean, 13::smallint, 'cuestionario_profundo'::text, 'guardianes_emocionales'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('shame_about'::text, '¿De qué te avergüenzas?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 14::smallint, 'cuestionario_profundo'::text, 'guardianes_emocionales'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('anxiety_level'::text, 'Nivel de ANSIEDAD diaria (1-10)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, true::boolean, 15::smallint, 'cuestionario_profundo'::text, 'estado_mental'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('postponed_decision'::text, 'Decisión importante que llevas postergando hace meses'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 16::smallint, 'cuestionario_profundo'::text, 'estado_mental'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('postponed_reason'::text, '¿Por qué no la has tomado?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 17::smallint, 'cuestionario_profundo'::text, 'estado_mental'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('rock_body'::text, 'Roca de Cuerpo'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 1::smallint, 'diseno_destino'::text, 'rocas_maestras'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('rock_mind'::text, 'Roca de Mente'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 2::smallint, 'diseno_destino'::text, 'rocas_maestras'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('rock_business'::text, 'Roca de Negocio/Acción'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 3::smallint, 'diseno_destino'::text, 'rocas_maestras'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('master_goal_90d'::text, 'Meta Maestra a 90 días'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 4::smallint, 'diseno_destino'::text, 'destino_90d'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('day_90_visualization'::text, 'Visualización del Día 90'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 5::smallint, 'diseno_destino'::text, 'destino_90d'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('baptism_audio'::text, 'Audio del Bautizo del Proceso'::text, 'AUDIO'::tipo_pregunta_onboarding, NULL, true::boolean, 1::smallint, 'cierre_dia_1'::text, 'bautizo'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('rocks_sync_acceptance'::text, 'Entiendo que debo completar estas 3 Rocas todos los días durante 90 días'::text, 'CASILLA'::tipo_pregunta_onboarding, NULL, true::boolean, 2::smallint, 'cierre_dia_1'::text, 'sincronizacion'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('email'::text, 'Correo electrónico'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 3::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('city'::text, 'Ciudad'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 5::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('family_status'::text, 'Estado familiar'::text, 'SELECCION_UNICA'::tipo_pregunta_onboarding, NULL, true::boolean, 38::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('timezone_auto'::text, 'Zona horaria (capturada automáticamente)'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 39::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('how_found_us'::text, '¿Cómo nos conociste?'::text, 'SELECCION_UNICA'::tipo_pregunta_onboarding, NULL, true::boolean, 40::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('children_info'::text, 'Cantidad y edades de hijos'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 41::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('dni_photo_front'::text, 'Foto del DNI (anverso)'::text, 'ARCHIVO'::tipo_pregunta_onboarding, NULL, true::boolean, 42::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('instagram_business'::text, 'Instagram del negocio (URL)'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 43::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('client_photo_left'::text, 'Foto del cliente — perfil izquierdo'::text, 'ARCHIVO'::tipo_pregunta_onboarding, NULL, true::boolean, 44::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('business_name'::text, 'Nombre del negocio'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 45::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('client_photo_right'::text, 'Foto del cliente — perfil derecho'::text, 'ARCHIVO'::tipo_pregunta_onboarding, NULL, true::boolean, 46::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('years_in_activity'::text, 'Años en la actividad'::text, 'NUMERO'::tipo_pregunta_onboarding, NULL, false::boolean, 47::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('tiktok_url'::text, 'TikTok (URL)'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 48::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('age'::text, 'Edad'::text, 'NUMERO'::tipo_pregunta_onboarding, NULL, true::boolean, 49::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('identity_document'::text, 'Documento de identidad (DNI o pasaporte)'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 50::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('main_problem'::text, 'Problema principal por el que decidiste ingresar a Renaser'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 51::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('birth_date'::text, 'Fecha de nacimiento'::text, 'FECHA'::tipo_pregunta_onboarding, NULL, true::boolean, 52::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('district'::text, 'Distrito / Barrio'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 53::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('dni_photo_back'::text, 'Foto del DNI (reverso)'::text, 'ARCHIVO'::tipo_pregunta_onboarding, NULL, true::boolean, 54::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('facebook_url'::text, 'Facebook (URL)'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 55::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('data_consent'::text, 'Declaración de Consentimiento y Uso de Datos'::text, 'SELECCION_UNICA'::tipo_pregunta_onboarding, NULL, true::boolean, 56::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('instagram_personal'::text, 'Instagram personal (URL)'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 57::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('client_photo_front'::text, 'Foto del cliente — frente'::text, 'ARCHIVO'::tipo_pregunta_onboarding, NULL, true::boolean, 58::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('follow_time'::text, '¿Hace cuánto sigues a RENASER?'::text, 'SELECCION_UNICA'::tipo_pregunta_onboarding, NULL, false::boolean, 59::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('business_industry'::text, 'Industria / Sector'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 60::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('address_reference'::text, 'Dirección exacta + referencia'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, true::boolean, 61::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-29 03:30:47.630602+00'::timestamptz),
    ('terms_signature'::text, 'Firma manuscrita de aceptación de los Términos y Condiciones'::text, 'FIRMA'::tipo_pregunta_onboarding, NULL, true::boolean, 2::smallint, 'terminos'::text, 'aceptacion'::text, '2026-07-30 20:59:26.454154+00'::timestamptz),
    ('sex'::text, 'Sexo'::text, 'SELECCION_UNICA'::tipo_pregunta_onboarding, NULL, true::boolean, 62::smallint, 'ficha_inicial'::text, 'identidad_operativa'::text, '2026-07-31 23:56:11.539603+00'::timestamptz),
    ('body_stress_signal'::text, '¿Cómo te avisa tu cuerpo cuando estás estresado/a?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 18::smallint, 'cuestionario_profundo'::text, 'estado_somatico'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('chest_cm'::text, 'Pecho (cm)'::text, 'NUMERO'::tipo_pregunta_onboarding, NULL, false::boolean, 21::smallint, 'cuestionario_profundo'::text, 'estado_somatico'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('daily_practice_1'::text, 'Práctica diaria 1'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 26::smallint, 'cuestionario_profundo'::text, 'manifestacion'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('daily_practice_2'::text, 'Práctica diaria 2'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 27::smallint, 'cuestionario_profundo'::text, 'manifestacion'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('daily_practice_3'::text, 'Práctica diaria 3'::text, 'TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 28::smallint, 'cuestionario_profundo'::text, 'manifestacion'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('day_90_message'::text, '¿Qué te dirá tu yo del Día 90 que NO te dijiste hoy?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 25::smallint, 'cuestionario_profundo'::text, 'manifestacion'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('energy_afternoon'::text, 'Energía en la tarde (4-6 PM)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, false::boolean, 4::smallint, 'cuestionario_profundo'::text, 'energia_vital'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('energy_general'::text, 'Tu nivel de energía vital promedio HOY (1-10)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, false::boolean, 1::smallint, 'cuestionario_profundo'::text, 'energia_vital'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('energy_midday'::text, 'Energía al mediodía (12-2 PM)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, false::boolean, 3::smallint, 'cuestionario_profundo'::text, 'energia_vital'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('energy_morning'::text, 'Energía al despertar (6-8 AM)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, false::boolean, 2::smallint, 'cuestionario_profundo'::text, 'energia_vital'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('energy_night'::text, 'Energía en la noche (9-11 PM)'::text, 'ESCALA'::tipo_pregunta_onboarding, '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]'::jsonb, false::boolean, 5::smallint, 'cuestionario_profundo'::text, 'energia_vital'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('hip_cm'::text, 'Cadera (cm)'::text, 'NUMERO'::tipo_pregunta_onboarding, NULL, false::boolean, 20::smallint, 'cuestionario_profundo'::text, 'estado_somatico'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('open_cycle'::text, 'Cierre de ciclos pendientes'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 24::smallint, 'cuestionario_profundo'::text, 'estado_emocional'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('pending_parents_conversation'::text, 'Conversación pendiente con tus padres (vivos o no)'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 23::smallint, 'cuestionario_profundo'::text, 'estado_emocional'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('self_if_no_change'::text, '¿En qué tipo de persona te conviertes si NO cambias?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 19::smallint, 'ficha_inicial'::text, 'mente_y_patrones'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('self_sabotage_thought'::text, 'El pensamiento recurrente que más te boicotea'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 15::smallint, 'ficha_inicial'::text, 'mente_y_patrones'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('unresolved_grief'::text, 'Duelos no resueltos'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 22::smallint, 'cuestionario_profundo'::text, 'estado_emocional'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('waist_cm'::text, 'Cintura (cm)'::text, 'NUMERO'::tipo_pregunta_onboarding, NULL, false::boolean, 19::smallint, 'cuestionario_profundo'::text, 'estado_somatico'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('why_now'::text, '¿Por qué AHORA y no antes?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 32::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-05-20 17:04:23.448229+00'::timestamptz),
    ('willing_to_release'::text, '¿Qué estás dispuesto/a a soltar para que esto funcione?'::text, 'AREA_TEXTO'::tipo_pregunta_onboarding, NULL, false::boolean, 36::smallint, 'ficha_inicial'::text, 'compromiso_y_cierre'::text, '2026-05-20 17:04:23.448229+00'::timestamptz)
) AS v(clave_pregunta, texto, tipo, config_escala, requerida, orden, flujo, clave_seccion, creado_en)
JOIN secciones_onboarding s ON s.flujo = v.flujo AND s.clave_seccion = v.clave_seccion;

-- ============================================================================
-- OPCIONES_PREGUNTA (23 filas: las 5 preguntas SELECCION_UNICA)
-- ============================================================================

INSERT INTO opciones_pregunta (pregunta_id, orden, valor, etiqueta)
SELECT p.id, v.orden, v.valor, v.etiqueta
FROM (VALUES
    ('family_status'::text, 0::smallint, 'Soltero/a sin hijos'::text, 'Soltero/a sin hijos'::text),
    ('family_status'::text, 1::smallint, 'Soltero/a con hijos'::text, 'Soltero/a con hijos'::text),
    ('family_status'::text, 2::smallint, 'En pareja sin hijos'::text, 'En pareja sin hijos'::text),
    ('family_status'::text, 3::smallint, 'Casado/a con hijos'::text, 'Casado/a con hijos'::text),
    ('family_status'::text, 4::smallint, 'Divorciado/a'::text, 'Divorciado/a'::text),
    ('family_status'::text, 5::smallint, 'Viudo/a'::text, 'Viudo/a'::text),
    ('how_found_us'::text, 0::smallint, 'Reels'::text, 'Reels'::text),
    ('how_found_us'::text, 1::smallint, 'Post'::text, 'Post'::text),
    ('how_found_us'::text, 2::smallint, 'Stories'::text, 'Stories'::text),
    ('how_found_us'::text, 3::smallint, 'Podcast'::text, 'Podcast'::text),
    ('how_found_us'::text, 4::smallint, 'Recomendación'::text, 'Recomendación'::text),
    ('how_found_us'::text, 5::smallint, 'Evento presencial'::text, 'Evento presencial'::text),
    ('how_found_us'::text, 6::smallint, 'Webinar'::text, 'Webinar'::text),
    ('how_found_us'::text, 7::smallint, 'Otro'::text, 'Otro'::text),
    ('data_consent'::text, 0::smallint, 'Sí, autorizo el uso responsable de mis datos personales.'::text, 'Sí, autorizo el uso responsable de mis datos personales.'::text),
    ('data_consent'::text, 1::smallint, 'No autorizo el uso de mis datos.'::text, 'No autorizo el uso de mis datos.'::text),
    ('follow_time'::text, 0::smallint, '< 1 mes'::text, '< 1 mes'::text),
    ('follow_time'::text, 1::smallint, '1-3 meses'::text, '1-3 meses'::text),
    ('follow_time'::text, 2::smallint, '4-6 meses'::text, '4-6 meses'::text),
    ('follow_time'::text, 3::smallint, '7-12 meses'::text, '7-12 meses'::text),
    ('follow_time'::text, 4::smallint, '+ 1 año'::text, '+ 1 año'::text),
    ('sex'::text, 0::smallint, 'Masculino'::text, 'Masculino'::text),
    ('sex'::text, 1::smallint, 'Femenino'::text, 'Femenino'::text)
) AS v(clave_pregunta, orden, valor, etiqueta)
JOIN preguntas_onboarding p ON p.clave_pregunta = v.clave_pregunta;

COMMIT;
