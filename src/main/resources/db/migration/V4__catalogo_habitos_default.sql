-- Primera carga del catalogo de habitos de sistema (docs/MODULO_HABITS.md), migrada desde
-- produccion real (proyecto Supabase qchpxyaiipghayyfmthg, tablas `habits` + `habit_schedules`,
-- dump con `supabase db dump --data-only` el 2026-08-28; fuente completa en
-- docs/db/migracion/datos_origen/dump_completo.sql, no versionada por su tamano).
--
-- Alcance deliberadamente acotado a LOGICA/CATALOGO, no a datos de usuario: se le comunico a los
-- usuarios que el sistema se esta reconstruyendo, asi que no se migra habit_tracks, evidence, ni
-- ningun perfil/progreso de aprendiz. Esta migracion es autocontenida: solo escribe filas nuevas
-- en `habitos` y `horarios_habito`, ninguna FK hacia una tabla de usuarios.
--
-- ── Decisiones tomadas para esta carga (para que quede escrito, no solo en el chat) ──────────
--
-- 1) Se migran SOLO los 18 habitos con is_active=true en el dato viejo. Los 9 inactivos
--    (Registro de intoxicacion consciente, Kilometros diarios, Entrenamiento fisico, Ducha fria
--    diaria, Lectura consciente, Agua con limon+jugo verde noche, Mantra matutino & decreto,
--    Ritual del agua presente, Movimiento libre/baile) se excluyen del catalogo default: no hay
--    historial de usuario que preservar (decision explicita, ver arriba), asi que no hay razon
--    para conservar filas muertas. 4 de esos 9 ni siquiera tenian horario en `habit_schedules`
--    (confirma que ya estaban abandonados en la practica, no es una omision de esta migracion).
--    Si alguno se reactiva, se da de alta como habito nuevo, no como resurreccion de fila vieja.
--
-- 2) Los ids uuid del dato viejo SE CONSERVAN tal cual (politica ya fijada en
--    docs/db/AUDITORIA_REDISENO_BD.md #14.8: "los ids uuid existentes se conservan"), por si
--    algun otro insumo de produccion (ej. `habit_guides`, capturas, comunicaciones internas) los
--    referencia por fuera de esta base.
--
-- 3) Anomalia real encontrada en el dato viejo: el habito 'ESCRITURA LIBRE NOCTURNA'
--    (593b2c17-8acd-49d0-b8f2-199a48469715) tiene habit_type='CHECKBOX' pero
--    journal_entry_type='FREE_WRITING' cargado (probable resto de cuando era JOURNALING y lo
--    cambiaron sin limpiar esa columna). Migrarlo tal cual viola el CHECK
--    `diario_solo_journaling` de `habitos` (tipo_entrada_diario solo si tipo='JOURNALING') y
--    hace fallar el INSERT completo. Se deja `tipo_entrada_diario = NULL` para esa fila -- es
--    ademas el valor con el que el backend ya trabaja hoy, porque `tipo_entrada_diario` no esta
--    mapeado en HabitoJpaEntity (ver punto 4).
--
-- 4) `grupo`, `tipo_entrada_diario` y `orden` SI se cargan en esta migracion (INSERT crudo, no
--    pasa por JPA) pero el backend HOY no los lee ni los expone por API -- gap ya documentado en
--    HabitoJpaEntity.java (javadoc de la clase) y docs/MODULO_HABITS.md (decision D-H5). Cargar
--    estos datos no rompe nada; simplemente no van a verse agrupados/ordenados en la app hasta
--    que se cierre ese hueco (fuera de alcance de esta migracion, no se pidio).
--
-- 5) Categorias e iconos NO se tocan aca: ya estaban sembrados en V1 (`categorias_habito`,
--    `iconos_habito`) y los 18 habitos de esta carga usan exclusivamente valores que ya existen
--    ahi (verificado 1 a 1 contra el dato viejo antes de escribir este archivo).
--
-- 6) Pendiente, fuera de esta migracion: `habit_guides`/`habit_guide_attachments` (contenido de
--    guia: que hacer, mantras, etc. -> `guias_habito`/`adjuntos_guia`), `spirit_audios`
--    (`audios_espiritu`) y `audio_therapies` (`audioterapias`) tambien estan en el dump y son
--    lógica/catálogo igual que esto, pero no bloquean el catálogo funcional del día -- se cargan
--    en una migracion V5 aparte para no mezclar responsabilidades en un solo archivo.
--
-- Mapeo de valores aplicado (old -> new), literal, sin inventar ninguno:
--   category:            BODY->CUERPO, MIND->MENTE, CONSCIENCE->CONSCIENCIA, SPIRIT->ESPIRITU
--   habit_type:           CHECKBOX->CHECKBOX, JOURNALING->JOURNALING (identico)
--   evidence_requirement: REQUIRED->OBLIGATORIA, OPTIONAL->OPCIONAL
--   journal_entry_type:   FREE_WRITING->ESCRITURA_LIBRE, INTOXICATION_LOG->REGISTRO_INTOXICACION
--                         (ninguno de los 18 activos usa este campo tras el punto 3 de arriba)
--   habit_schedules.day_type: ALL->TODOS, SUNDAY->DOMINGO (los 2 unicos valores presentes)

BEGIN;

-- Ver V3 linea 14-18: search_path explicito porque esta migracion puede correr sola en un
-- despliegue posterior a un baseline ya aplicado.
SET search_path TO renaser, public;

-- ============================================================================
-- HABITOS DE CATALOGO (ambito='SISTEMA'), 18 filas, ids de produccion preservados
-- ============================================================================

INSERT INTO habitos (
    id, ambito, participante_id, titulo, descripcion, tipo, categoria_clave, icono_clave,
    grupo, clave_sistema, exigencia_evidencia, tipo_entrada_diario, es_opcional,
    obligatorio_en_intoxicacion, eleccion_dia_semanal, horas_extra_evidencia,
    dia_limite_edicion_libre, orden, activo, creado_en, actualizado_en
) VALUES
    ('5449b2b9-a5b4-441f-8304-2c37c425a114', 'SISTEMA', NULL, 'PRIMERA COMIDA (ROMPO EL AYUNO)', 'Foto de la primera comida del día', 'CHECKBOX', 'CUERPO', 'FAST_END', 'AYUNO INTERMITENTE', NULL, 'OBLIGATORIA', NULL, false, false, false, NULL, 90, 7, true, '2026-07-31 15:32:38.95', '2026-08-16 02:41:37.827'),
    ('3a8a82a0-787d-46bc-a69e-b29c110a37bc', 'SISTEMA', NULL, 'ÚLTIMA COMIDA DEL DÍA', 'Foto de la última comida del día 6pm', 'CHECKBOX', 'CUERPO', 'FAST_START', 'AYUNO INTERMITENTE', NULL, 'OBLIGATORIA', NULL, false, false, false, NULL, 90, 9, true, '2026-07-31 15:32:38.95', '2026-08-16 02:42:21.04'),
    ('830c3d76-888a-4aef-bb30-fb0f0cc7ca73', 'SISTEMA', NULL, 'POST DIARIO EN COMUNIDAD', 'Publicación en comunidad', 'CHECKBOX', 'CONSCIENCIA', 'COMMUNITY_POST', NULL, NULL, 'OPCIONAL', NULL, false, true, false, NULL, 90, 12, true, '2026-07-31 15:32:38.95', '2026-08-16 02:43:14.442'),
    ('60d6c870-df03-4d36-8a88-8c7411ae406b', 'SISTEMA', NULL, 'RITUAL DE MAÑANA (domingo)', 'Foto/video del ritual', 'CHECKBOX', 'ESPIRITU', 'RITUAL_MORNING', NULL, NULL, 'OPCIONAL', NULL, false, false, false, NULL, NULL, 30, true, '2026-07-31 15:32:38.95', '2026-08-04 22:29:38.341'),
    ('358caf57-519f-4b12-8cbe-c7094d0523d8', 'SISTEMA', NULL, 'DESCANSO PROFUNDO', 'Descanso completo', 'CHECKBOX', 'CUERPO', 'SLEEP', NULL, NULL, 'OPCIONAL', NULL, false, false, false, NULL, NULL, 29, true, '2026-07-31 15:32:38.95', '2026-08-03 21:54:42.515'),
    ('3a922b73-6d11-48f9-89c2-abc453ca6ef7', 'SISTEMA', NULL, 'AGUA E HIDRATACIÓN (domingo)', 'Foto del vaso', 'CHECKBOX', 'CUERPO', 'WATER', NULL, NULL, 'OPCIONAL', NULL, false, false, false, NULL, NULL, 31, true, '2026-07-31 15:32:38.95', '2026-08-03 21:54:42.515'),
    ('bb0b1cd0-18df-4bcd-9a4e-9e1c63905c0d', 'SISTEMA', NULL, 'AUDIOTERAPIA SEMANAL', 'Enviar resumen de la audioterapia', 'JOURNALING', 'ESPIRITU', 'PODCAST', NULL, 'AUDIO_THERAPY_WEEKLY', 'OPCIONAL', NULL, false, false, false, NULL, NULL, 19, true, '2026-07-31 15:32:38.95', '2026-08-04 17:50:28.456'),
    ('899a2151-e98c-4b61-a46c-b55134240d17', 'SISTEMA', NULL, 'DESPERTAR', 'Marcar como completado el habito', 'CHECKBOX', 'CUERPO', 'SLEEP', NULL, NULL, 'OPCIONAL', NULL, false, false, false, NULL, 90, 1, true, '2026-08-04 17:48:16.375', '2026-08-16 02:39:48.803'),
    ('66507383-7219-43ab-aa42-2fbc76152b82', 'SISTEMA', NULL, 'AGUA TIBIA CON LIMÓN', 'Foto del vaso', 'CHECKBOX', 'CUERPO', 'WATER', NULL, 'WARM_LEMON_WATER', 'OBLIGATORIA', NULL, false, false, false, NULL, 90, 2, true, '2026-07-31 15:32:38.95', '2026-08-16 02:40:01.668'),
    ('4dee0fa3-e285-4b7d-b062-ad0001dde314', 'SISTEMA', NULL, 'RITUAL TIERRA - AGUA - FUEGO (mañana)', 'Foto/video del ritual', 'CHECKBOX', 'ESPIRITU', 'RITUAL_MORNING', 'RITUAL TIERRA - AGUA - FUEGO', NULL, 'OBLIGATORIA', NULL, false, false, false, NULL, 90, 3, true, '2026-07-31 15:32:38.95', '2026-08-16 02:40:17.187'),
    ('d2d58e66-db7f-4226-9c95-30b380f68b73', 'SISTEMA', NULL, 'DÍA SIN CELULAR', 'Declaración y desintoxicación digital de 24 horas', 'CHECKBOX', 'MENTE', 'PHONE_OFF', NULL, 'PHONE_FREE_DAY', 'OPCIONAL', NULL, true, false, false, NULL, 90, 4, true, '2026-07-31 15:32:38.95', '2026-08-16 02:40:32.118'),
    ('feeea74a-d811-47a2-a175-34f2527c1d32', 'SISTEMA', NULL, 'Clase diaria', 'Escuchar la lección de mentoría diaria (10-15 min) y mandar resumen', 'CHECKBOX', 'MENTE', 'READING', NULL, 'DAILY_CLASS', 'OBLIGATORIA', NULL, false, false, false, NULL, 90, 5, true, '2026-07-31 15:32:38.95', '2026-08-16 02:40:53.904'),
    ('00006bd5-ab74-4317-b022-ae2e3a878d55', 'SISTEMA', NULL, 'JUGO VERDE', 'Foto del vaso con Jugo Verde', 'CHECKBOX', 'CUERPO', 'NUTRITION', NULL, 'GREEN_JUICE', 'OBLIGATORIA', NULL, false, false, false, NULL, 90, 6, true, '2026-08-04 17:48:16.375', '2026-08-16 02:41:22.949'),
    ('63acbd12-9792-495c-be22-6280ecba53b3', 'SISTEMA', NULL, 'RITUAL TIERRA - AGUA - FUEGO (mediodía)', 'Foto/video del ritual', 'CHECKBOX', 'ESPIRITU', 'RITUAL_MIDDAY', 'RITUAL TIERRA - AGUA - FUEGO', NULL, 'OBLIGATORIA', NULL, false, false, false, NULL, 90, 8, true, '2026-07-31 15:32:38.95', '2026-08-16 02:41:56.631'),
    ('679188b9-7c1d-48ec-ae09-8a76b87badbf', 'SISTEMA', NULL, 'RITUAL TIERRA - AGUA - FUEGO (noche)', 'Foto/video del ritual', 'CHECKBOX', 'ESPIRITU', 'RITUAL_NIGHT', 'RITUAL TIERRA - AGUA - FUEGO', NULL, 'OBLIGATORIA', NULL, false, false, false, NULL, 90, 10, true, '2026-07-31 15:32:38.95', '2026-08-16 02:42:37.279'),
    ('593b2c17-8acd-49d0-b8f2-199a48469715', 'SISTEMA', NULL, 'ESCRITURA LIBRE NOCTURNA', 'Foto de la página escrita', 'CHECKBOX', 'MENTE', 'JOURNALING', NULL, NULL, 'OPCIONAL', NULL, false, false, false, NULL, 90, 11, true, '2026-07-31 15:32:38.95', '2026-08-16 02:42:55.469'),
    ('a344681d-c7a7-4324-a9a0-1b7502e52d80', 'SISTEMA', NULL, 'Pastilla Renacer', 'Audio diario de Espíritu — escúchalo y manda tu resumen antes del mediodía.', 'JOURNALING', 'ESPIRITU', 'PODCAST', 'PASTILLA RENACER', 'PASTILLA_RENACER', 'OPCIONAL', NULL, false, false, false, NULL, 90, 12, true, '2026-08-05 17:43:06.682', '2026-08-16 02:43:36.501'),
    ('48d68c12-72a9-428c-a8c6-02b9b01bc2fe', 'SISTEMA', NULL, 'DORMIR', 'Dormir en la hora acordada.', 'CHECKBOX', 'CUERPO', 'SLEEP', NULL, NULL, 'OPCIONAL', NULL, false, false, false, NULL, 90, 13, true, '2026-08-04 17:48:16.375', '2026-08-28 14:38:18.231');

-- ============================================================================
-- HORARIOS DE LOS 18 HABITOS DE ARRIBA (uno por habito, 1:1 en el dato de origen)
-- ============================================================================

INSERT INTO horarios_habito (
    habito_id, dia_inicio, dia_fin, tipo_dia, hora_disparo, hora_limite, creado_en, actualizado_en
) VALUES
    ('5449b2b9-a5b4-441f-8304-2c37c425a114', 1, 90, 'TODOS', '11:00', NULL, '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('3a8a82a0-787d-46bc-a69e-b29c110a37bc', 1, 90, 'TODOS', '18:00', NULL, '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('830c3d76-888a-4aef-bb30-fb0f0cc7ca73', 1, 90, 'TODOS', '22:00', NULL, '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('60d6c870-df03-4d36-8a88-8c7411ae406b', 35, NULL, 'DOMINGO', '07:00', NULL, '2026-07-31 15:32:38.95', '2026-08-01 18:03:09.273'),
    ('358caf57-519f-4b12-8cbe-c7094d0523d8', 35, NULL, 'DOMINGO', '08:00', NULL, '2026-07-31 15:32:38.95', '2026-08-01 18:03:09.273'),
    ('3a922b73-6d11-48f9-89c2-abc453ca6ef7', 35, NULL, 'DOMINGO', '07:30', NULL, '2026-07-31 15:32:38.95', '2026-08-01 18:03:09.273'),
    ('bb0b1cd0-18df-4bcd-9a4e-9e1c63905c0d', 11, 90, 'TODOS', '07:00', '23:55', '2026-08-04 17:50:28.456', '2026-08-05 20:37:14.717'),
    ('899a2151-e98c-4b61-a46c-b55134240d17', 1, 90, 'TODOS', NULL, NULL, '2026-08-04 17:48:47.531', '2026-08-18 01:24:10.992'),
    ('66507383-7219-43ab-aa42-2fbc76152b82', 1, 90, 'TODOS', '06:00', NULL, '2026-08-04 17:48:47.531', '2026-08-10 23:28:53.876'),
    ('4dee0fa3-e285-4b7d-b062-ad0001dde314', 1, 90, 'TODOS', '06:00', NULL, '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('d2d58e66-db7f-4226-9c95-30b380f68b73', 1, 90, 'TODOS', '06:30', '23:59', '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('feeea74a-d811-47a2-a175-34f2527c1d32', 1, 90, 'TODOS', '14:59', NULL, '2026-08-04 17:48:47.531', '2026-08-07 19:58:00.03'),
    ('00006bd5-ab74-4317-b022-ae2e3a878d55', 1, 90, 'TODOS', '09:00', NULL, '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('63acbd12-9792-495c-be22-6280ecba53b3', 1, 90, 'TODOS', '13:00', NULL, '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('679188b9-7c1d-48ec-ae09-8a76b87badbf', 1, 90, 'TODOS', '21:00', NULL, '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('593b2c17-8acd-49d0-b8f2-199a48469715', 1, 90, 'TODOS', '21:30', NULL, '2026-08-04 17:48:47.531', '2026-08-04 17:48:47.531'),
    ('a344681d-c7a7-4324-a9a0-1b7502e52d80', 8, NULL, 'TODOS', '07:00', '12:00', '2026-08-05 17:43:07.392', '2026-08-05 20:45:44.997'),
    ('48d68c12-72a9-428c-a8c6-02b9b01bc2fe', 1, 90, 'TODOS', '22:30', NULL, '2026-08-04 17:48:47.531', '2026-08-10 23:26:16.089');

COMMIT;
