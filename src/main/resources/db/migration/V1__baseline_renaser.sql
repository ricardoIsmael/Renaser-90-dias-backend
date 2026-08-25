-- ============================================================================
-- RENASER OS — BD NUEVA v1 (modelo objetivo)
-- Generado desde la auditoría docs/db/AUDITORIA_REDISENO_BD.md (2026-08-24).
-- PostgreSQL >= 15. Ejecutable de una pieza sobre una BD limpia.
--
-- Convenciones:
--  * Esquema propio `renaser` (no public). Nombres en español, snake_case.
--  * timestamptz para instantes; date para días de calendario; time para horas.
--  * Enums SOLO para máquinas de estado / vocabularios estables (se agregan
--    valores con ALTER TYPE ... ADD VALUE; nunca se renombran). Los catálogos
--    volátiles (categorías, iconos) son TABLAS.
--  * Toda FK declara ON DELETE explícito y tiene índice (o es prefijo de uno).
--  * PK uuid en entidades expuestas por la API (se migran los ids actuales);
--    bigint IDENTITY en logs append-only; PK natural en asociativas y 1:1.
--    NOTA RENDIMIENTO: si la instancia tiene pg_uuidv7, cambiar el DEFAULT de
--    las tablas de alta inserción (registros_habito, evidencias, mensajes,
--    registros_radar, publicaciones_muro) a uuid_generate_v7() — uuid v4
--    fragmenta índices en tablas grandes.
--  * Los enums del backend Java deben espejar estos valores (@Enumerated STRING).
--  * [PENDIENTE-CONFIRMAR] marca decisiones que esperan confirmación de negocio.
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS vector;     -- RAG (base_conocimiento.embedding)
-- Monitoreo (skill postgresql-optimization): habilitar si la instancia lo permite
-- (en Supabase viene activo; en self-hosted requiere shared_preload_libraries):
-- CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

CREATE SCHEMA IF NOT EXISTS renaser;
SET search_path TO renaser, public;  -- public al final: ahí viven las extensiones (vector)

-- ============================================================================
-- ENUMS (máquinas de estado y vocabularios estables)
-- Mapeo de valores viejos→nuevos comentado donde el nombre cambia,
-- para la migración de datos.
-- ============================================================================

CREATE TYPE rol_usuario              AS ENUM ('APRENDIZ','MENTOR','LIDER_MENTORES','ADMIN','ALQUIMISTA');
    -- Java: TRAINEE,MENTOR,MENTOR_LEAD,ADMIN,ALCHEMIST (D-21: RBAC es enum Java, este tipo
    -- es solo la representacion en columna — @Enumerated(STRING) no aplica porque los nombres
    -- no coinciden 1:1 (idioma distinto); el mapeo explicito vive en UserPersistenceMapper.
CREATE TYPE estado_usuario          AS ENUM ('ACTIVO','INACTIVO','SUSPENDIDO');            -- ACTIVE,INACTIVE,SUSPENDED
CREATE TYPE estado_solicitud        AS ENUM ('PENDIENTE','APROBADA','RECHAZADA');          -- PENDING,APPROVED,REJECTED
CREATE TYPE fase_programa           AS ENUM ('FASE_1_RENACER','FASE_2_DESARROLLO','FASE_3_GUERRERO_ALQUIMISTA','FASE_4_ASCENSION');
CREATE TYPE tipo_meta               AS ENUM ('FISICA','VENTAS','MIEDO');                   -- PHYSICAL,SALES,FEAR
CREATE TYPE nivel_mentor            AS ENUM ('N0','N1','N2','N3');
CREATE TYPE estado_operativo        AS ENUM ('VERDE','AMARILLO','ROJO');                   -- GREEN,YELLOW,RED
CREATE TYPE estado_cohorte          AS ENUM ('PLANIFICADA','ACTIVA','COMPLETADA');         -- PLANNED,ACTIVE,COMPLETED

CREATE TYPE ambito_habito           AS ENUM ('SISTEMA','PERSONAL');
CREATE TYPE tipo_habito             AS ENUM ('CHECKBOX','JOURNALING','CALIFICACION','BLOQUEO'); -- RATING→CALIFICACION, BLOCKING→BLOQUEO
CREATE TYPE tipo_dia                AS ENUM ('DISCIPLINA','INTOXICACION','TODOS','DOMINGO');    -- ALL→TODOS, SUNDAY→DOMINGO
CREATE TYPE exigencia_evidencia     AS ENUM ('OPCIONAL','OBLIGATORIA');                    -- OPTIONAL,REQUIRED
CREATE TYPE tipo_entrada_diario     AS ENUM ('ESCRITURA_LIBRE','BITACORA_NOCTURNA','ESPEJO_SOMBRA','OBSERVACION_CONDUCTAS','REGISTRO_INTOXICACION','VISUALIZACION_NOCTURNA','CONTROL_DISTRACCIONES','EVALUACION_DIA');
CREATE TYPE seccion_guia            AS ENUM ('QUE_HACER','COMO_HACERLO','CIENCIA','RENASER','ALQUIMIA','RESULTADOS','COMO_VALIDAR');
CREATE TYPE tipo_medio_guia         AS ENUM ('ENLACE','IMAGEN','AUDIO');                   -- LINK,IMAGE,AUDIO
CREATE TYPE estado_registro         AS ENUM ('PENDIENTE','EN_CURSO','COMPLETADO','FALLIDO','EXPIRADO'); -- IN_PROGRESS→EN_CURSO
CREATE TYPE estado_sesion_bloqueo   AS ENUM ('ACTIVA','COMPLETADA','ROTA','CANCELADA');    -- ACTIVE,COMPLETED,BROKEN,CANCELLED
CREATE TYPE motivo_salida_bloqueo   AS ENUM ('SALIDA_TEMPRANA','VIOLACION_APP_USADA','MANUAL'); -- EARLY_EXIT,VIOLATION_APP_USED,MANUAL
CREATE TYPE estado_racha            AS ENUM ('ACTIVA','COMPLETADA','ROTA','EXPIRADA');     -- PhoneFreeRunStatus
CREATE TYPE estado_registro_espiritu AS ENUM ('PENDIENTE','ENTREGADO','PERDIDO');          -- PENDING,SUBMITTED,MISSED
CREATE TYPE plantilla_habito_personal AS ENUM ('GIMNASIO','CORRER','OTRO');                -- GYM,RUNNING,OTHER

CREATE TYPE eje_objetivo            AS ENUM ('CUERPO','TRABAJO','RELACIONES');             -- BODY,WORK,RELATIONSHIPS (antes texto libre)
CREATE TYPE color_pareto            AS ENUM ('VERDE','AMARILLA','ROJA');                   -- GREEN,YELLOW,RED
CREATE TYPE resultado_verdugo       AS ENUM ('COMPLETADO','POSTERGADO','POSPUESTO_30','IGNORADO'); -- COMPLETED,POSTPONED,SNOOZED,IGNORED
CREATE TYPE tipo_evidencia          AS ENUM ('FOTO','VIDEO','AUDIO','TEXTO','CAPTURA');    -- PHOTO,VIDEO,AUDIO,TEXT,SCREENSHOT; NOTE(legacy)→TEXTO
CREATE TYPE estado_validacion       AS ENUM ('PENDIENTE','VALIDA','RECHAZADA','REVISION_MANUAL','ANULADA_ADMIN'); -- reemplaza 4 booleanos (P-14)
CREATE TYPE estado_ia_v90           AS ENUM ('PENDIENTE','PROCESANDO','APROBADA','RECHAZADA','REVISION_MANUAL'); -- antes String libre

CREATE TYPE tipo_publicacion        AS ENUM ('MANUAL','HITO_AUTOMATICO','GUERRERO_CAIDO'); -- MANUAL,MILESTONE_AUTO,GUERRERO_CAIDO
CREATE TYPE tipo_reaccion           AS ENUM ('ME_GUSTA','NO_ME_GUSTA');                    -- LIKE,DISLIKE
CREATE TYPE tipo_audiencia          AS ENUM ('TODOS','NIVEL_MINIMO','CURSO','ROLES','CELULA'); -- ALL_MEMBERS,MIN_LEVEL,COURSE,ROLES,CELL
CREATE TYPE tipo_ubicacion          AS ENUM ('LLAMADA_INTERNA','WEBINAR','ZOOM','MEET','DIRECCION','ENLACE');
CREATE TYPE tipo_evento_calendario  AS ENUM ('MENTORIA_ALQUIMISTA','ESPONTANEO','SEMANA_MANIFESTACION','SESION_ESPECIAL');
CREATE TYPE estado_evento           AS ENUM ('BORRADOR','PUBLICADO','CANCELADO');          -- DRAFT,PUBLISHED,CANCELLED
CREATE TYPE frecuencia_recurrencia  AS ENUM ('DIARIA','SEMANAL','MENSUAL');                -- DAILY,WEEKLY,MONTHLY
CREATE TYPE estado_confirmacion     AS ENUM ('ASISTE','NO_ASISTE','QUIZAS');               -- GOING,NOT_GOING,MAYBE

CREATE TYPE tipo_conversacion       AS ENUM ('CELULA','DIRECTA','GLOBAL');                 -- CELL,DIRECT,GLOBAL
CREATE TYPE tipo_mensaje            AS ENUM ('TEXTO','IMAGEN','AUDIO','VIDEO','SISTEMA');  -- TEXT,IMAGE,AUDIO,VIDEO,SYSTEM
CREATE TYPE tipo_notificacion       AS ENUM ('RECORDATORIO_HABITO','RECORDATORIO_ROCA','RECORDATORIO_RADAR','MENSAJE_MENTOR','ANUNCIO_SISTEMA','RESUMEN_SEMANAL','LOGRO_DESBLOQUEADO','HITO_PROGRAMA','MENSAJE_CHAT','TICKET_RESPONDIDO','TICKET_ABIERTO','SANTUARIO_ROTO','HABITO_PERSONAL_MODIFICADO');
CREATE TYPE plataforma_push         AS ENUM ('IOS','ANDROID');                             -- antes text libre

CREATE TYPE rol_mensaje_renasia     AS ENUM ('USUARIO','ASISTENTE');                       -- USER,ASSISTANT
CREATE TYPE estado_ticket_mentor    AS ENUM ('ABIERTO','RESPONDIDO');                      -- OPEN,ANSWERED
CREATE TYPE categoria_soporte       AS ENUM ('TECNICO','CUENTA','PROGRAMA','FACTURACION','OTRO');
CREATE TYPE estado_ticket_soporte   AS ENUM ('ABIERTO','RESUELTO');                        -- OPEN,RESOLVED
CREATE TYPE motivo_puntos           AS ENUM ('HABITO_COMPLETADO','HABITO_EXTENDIDO','HABITO_PERDIDO','HABITO_TARDE','BONO_RACHA','SANTUARIO_ROTO','EVIDENCIA_INVALIDA','EVIDENCIA_INVALIDA_REVERTIDA','SEMANA_SIN_CELULAR_PERDIDA','ROCA_COMPLETADA','ROCA_EXTENDIDA','AJUSTE_MANUAL');
CREATE TYPE tipo_ranking            AS ENUM ('GENERAL','COHORTE','CELULA','LIGA');
CREATE TYPE tipo_pregunta_onboarding AS ENUM ('TEXTO','AREA_TEXTO','NUMERO','ESCALA','SELECCION_UNICA','SELECCION_MULTIPLE','AUDIO','FIRMA','CASILLA','FECHA','ARCHIVO');
    -- mapeo del enum viejo onboarding_question_type: text,textarea,number,scale,single_select,multi_select,audio,signature,checkbox,date,file_upload
CREATE TYPE tipo_regla_recordatorio AS ENUM ('MINUTOS_ANTES','DIAS_ANTES','HORA_DEL_DIA');
    -- forma REAL de reminderRules verificada en reminders.ts del repo viejo: {kind: minutesBefore|daysBefore|timeOfDay, value}
CREATE TYPE acceso_curso            AS ENUM ('ABIERTO','RESTRINGIDO');                     -- antes text+CHECK
CREATE TYPE tipo_video_leccion      AS ENUM ('YOUTUBE','STORAGE');

-- ============================================================================
-- IDENTIDAD Y RBAC  (§8 de la auditoría)
-- ============================================================================

-- [SUPERADO 2026-08-24, decisión D-21 en docs/MODULOS_A_AVANZAR.md §8]: estas tres tablas
-- contradecían D-13 (RBAC = enum Java + matriz en el constructor, ya decidido 2026-08-22).
-- Se confirmó el enum como fuente de verdad. Quedan pendientes de ajuste (junto con
-- roles_permitidos_curso y roles_destino_evento, mas abajo en este archivo, que referencian
-- roles.id) cuando se construyan los modulos academy/calendar. No se tocan todavia porque
-- ese ajuste excede el alcance de la sesion actual (solo modulo users).
CREATE TABLE roles (
    id          smallint  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave       text      NOT NULL UNIQUE CHECK (clave = upper(clave)),
    nombre      text      NOT NULL,
    descripcion text,
    es_sistema  boolean   NOT NULL DEFAULT true,
    creado_en   timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE roles IS '[SUPERADO — ver D-21] Catálogo RBAC. Los 5 roles del negocio como datos (antes: enum UserRole + matriz en código).';

CREATE TABLE permisos (
    id          smallint  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave       text      NOT NULL UNIQUE,
    recurso     text      NOT NULL,
    accion      text      NOT NULL,
    descripcion text,
    creado_en   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (recurso, accion)
);
COMMENT ON TABLE permisos IS '[SUPERADO — ver D-21] Acción autorizable. La clave es la que usa @RequiresPermission en el backend.';

CREATE TABLE rol_permiso (
    rol_id      smallint NOT NULL REFERENCES roles (id)    ON DELETE RESTRICT,
    permiso_id  smallint NOT NULL REFERENCES permisos (id) ON DELETE CASCADE,
    creado_en   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (rol_id, permiso_id)
);
CREATE INDEX rol_permiso_permiso_idx ON rol_permiso (permiso_id);

CREATE TABLE usuarios (
    id                  uuid        PRIMARY KEY,  -- = auth.users.id (Supabase). SIN default: la identidad viene de Auth.
    email               text        NOT NULL UNIQUE CHECK (position('@' in email) > 1),
    nombre_completo     text        NOT NULL,
    telefono            text,
    ciudad              text,
    pais                text,
    avatar_url          text,
    bio                 text,       -- solo tiene sentido si rol=ALQUIMISTA (decisión 2026-08-24: no amerita tabla propia)
    departamento        text,       -- solo tiene sentido si rol=ADMIN (idem)
    rol                 rol_usuario NOT NULL,  -- D-21: reemplaza rol_id (FK a la tabla roles [SUPERADO])
    estado              estado_usuario NOT NULL DEFAULT 'ACTIVO',
    motivo_estado       text,
    estado_cambiado_en  timestamptz,
    baja_solicitada_en  timestamptz,              -- soft-delete diferido: cron purga a los 14 días (política actual)
    ultima_actividad_en timestamptz,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    actualizado_en      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX usuarios_rol_estado_idx ON usuarios (rol, estado);  -- listados de admin (el (role) suelto era redundante: P-29)
CREATE INDEX usuarios_baja_idx ON usuarios (baja_solicitada_en) WHERE baja_solicitada_en IS NOT NULL;  -- cron de purga
COMMENT ON COLUMN usuarios.id IS 'UUID de Supabase Auth. Nunca autogenerado (regla actual que se conserva).';

CREATE TABLE solicitudes_cuenta (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    supabase_user_id  uuid        NOT NULL UNIQUE,
    email             text        NOT NULL UNIQUE,
    nombre_completo   text        NOT NULL,
    telefono          text        NOT NULL,       -- WhatsApp; aterriza en usuarios.telefono al aprobar
    ciudad            text,
    estado            estado_solicitud NOT NULL DEFAULT 'PENDIENTE',
    motivo_rechazo    text,
    revisada_por      uuid        REFERENCES usuarios (id) ON DELETE SET NULL,  -- P-09: antes columna suelta sin FK
    revisada_en       timestamptz,
    usuario_creado_id uuid        REFERENCES usuarios (id) ON DELETE SET NULL,  -- nuevo: traza la aprobación
    ip_solicitud      inet,                                                     -- antes text
    creado_en         timestamptz NOT NULL DEFAULT now(),
    actualizado_en    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT rechazo_con_motivo CHECK (estado <> 'RECHAZADA' OR motivo_rechazo IS NOT NULL)
);
CREATE INDEX solicitudes_cola_idx        ON solicitudes_cuenta (estado, creado_en);       -- cola del admin
CREATE INDEX solicitudes_rate_limit_idx  ON solicitudes_cuenta (ip_solicitud, creado_en); -- rate limit por IP
CREATE INDEX solicitudes_revisada_por_idx ON solicitudes_cuenta (revisada_por);
CREATE INDEX solicitudes_usuario_creado_idx ON solicitudes_cuenta (usuario_creado_id);

CREATE TABLE auditoria_cambios_rol (
    id              bigint    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      uuid      REFERENCES usuarios (id) ON DELETE SET NULL,  -- sujeto; sobrevive a la baja
    actor_id        uuid      REFERENCES usuarios (id) ON DELETE SET NULL,
    rol_anterior_id smallint  NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,  -- antes: enum copiado (P-04)
    rol_nuevo_id    smallint  NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    motivo          varchar(500),
    creado_en       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX aud_rol_sujeto_idx ON auditoria_cambios_rol (usuario_id, creado_en);
CREATE INDEX aud_rol_actor_idx  ON auditoria_cambios_rol (actor_id, creado_en);
-- rol_anterior_id / rol_nuevo_id SIN índice propio a propósito: roles nunca se borra
-- (RESTRICT + es_sistema) y ninguna consulta filtra el log por rol. Ídem habitos.icono_clave
-- (catálogo de decenas de filas). Omisiones deliberadas, no olvidos.

-- ── Perfiles: 1 tabla por rol SOLO cuando el rol tiene mas de un campo propio ──
-- Decisión 2026-08-24: perfiles_alquimista y perfiles_admin (que solo tenían un
-- campo de texto cada uno) se fusionaron en usuarios.bio / usuarios.departamento —
-- una tabla 1:1 para un solo campo opcional era abstracción prematura sin datos
-- que la justificaran. MENTOR sí conserva tabla propia: tiene 3 campos y ya le
-- sacaron uno (total_trainees_managed, P-17) — es una estructura que crece de verdad.
-- La coherencia rol⇔perfil se garantiza en el caso de uso (transacción única);
-- trigger opcional de defensa al final del script [PENDIENTE-CONFIRMAR].
--
-- perfiles_lider_mentores SE SACÓ (no se fusionó, se eliminó sin reemplazo): el único
-- campo que tenía (bio) era un placeholder sin confirmar (P-04, marcado [PENDIENTE-CONFIRMAR]
-- en el propio script), no un dato real. Modelarlo — como tabla o como columna en usuarios —
-- sería inventar una regla de negocio que todavía nadie definió (CLAUDE.MD §0.6). Se agrega
-- cuando se sepa qué campos lleva de verdad: si termina siendo uno solo, va como columna en
-- usuarios (igual que bio/departamento); si son varios y crecen, tabla propia (igual que mentor).

CREATE TABLE perfiles_mentor (
    usuario_id       uuid PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    nivel            nivel_mentor      NOT NULL DEFAULT 'N0',
    estado_operativo estado_operativo  NOT NULL DEFAULT 'VERDE',
    bio              text,
    creado_en        timestamptz NOT NULL DEFAULT now(),
    actualizado_en   timestamptz NOT NULL DEFAULT now()
    -- total_trainees_managed ELIMINADO (P-17): derivable con COUNT sobre participantes_programa.mentor_id (indexada).
    -- puntos_liga ELIMINADO — DECISIÓN 2026-08-24: en el ranking solo aparece el rol APRENDIZ;
    -- el contador de mentor no se usaba en ninguna pantalla. Si un mentor cursa el programa,
    -- sus puntos viven en puntajes_participante como los de cualquiera.
);

-- ── Organización ────────────────────────────────────────────────────────────

CREATE TABLE cohortes (
    id             uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre         text           NOT NULL,
    fecha_inicio   date           NOT NULL,
    fecha_fin      date,
    estado         estado_cohorte NOT NULL DEFAULT 'PLANIFICADA',
    creado_en      timestamptz    NOT NULL DEFAULT now(),
    actualizado_en timestamptz    NOT NULL DEFAULT now(),
    CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);
CREATE INDEX cohortes_estado_idx ON cohortes (estado);

CREATE TABLE celulas (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre            text        NOT NULL,
    mentor_id         uuid        UNIQUE REFERENCES perfiles_mentor (usuario_id) ON DELETE SET NULL, -- un mentor lidera a lo sumo una célula
    cohorte_id        uuid        NOT NULL REFERENCES cohortes (id) ON DELETE RESTRICT, -- antes CASCADE (P-27): la cohorte no arrastra células con gente
    url_videollamada  text,
    proxima_sesion_en timestamptz,
    creado_en         timestamptz NOT NULL DEFAULT now(),
    actualizado_en    timestamptz NOT NULL DEFAULT now()
    -- coherence_score_group / ranking_position ELIMINADAS (P-18) → ranking_celulas (snapshots)
);
CREATE INDEX celulas_cohorte_idx ON celulas (cohorte_id);

CREATE TABLE participantes_programa (
    -- Antes "perfiles_aprendiz". DECISIÓN 2026-08-24 (Luis/Ricardo): el programa de 90 días
    -- está ABIERTO A TODOS los roles — obligatorio para APRENDIZ (la fila se crea al aprobar
    -- su cuenta, en la misma transacción: invariante de aplicación), opcional para los demás
    -- (la fila se crea al inscribirse). Un participante sube evidencia y opera igual sea cual
    -- sea su rol. Por eso esta tabla NO participa del trigger rol⇔perfil.
    usuario_id                 uuid          PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    mentor_id                  uuid          REFERENCES perfiles_mentor (usuario_id) ON DELETE SET NULL,
    celula_id                  uuid          REFERENCES celulas (id) ON DELETE SET NULL,
    dia_programa               smallint      NOT NULL DEFAULT 0 CHECK (dia_programa BETWEEN 0 AND 90),
    dia_programa_avanzado_el   date,         -- idempotencia del cron nocturno (QA-33), se conserva
    fase                       fase_programa NOT NULL DEFAULT 'FASE_1_RENACER',
    tipo_meta                  tipo_meta,
    fecha_inicio               date          NOT NULL DEFAULT current_date,
    programa_activado_en       timestamptz,  -- NULL = aprobado pero sin Términos firmados (el cron lo saltea)
    fecha_graduacion_esperada  date          GENERATED ALWAYS AS (fecha_inicio + 90) STORED, -- antes columna manual derivable (3FN)
    nombre_reto_personal       text,
    timezone                   text          NOT NULL DEFAULT 'America/Lima',
    habitos_escalonados_en     timestamptz,  -- NULL = padrón anterior al desbloqueo escalonado (comportamiento viejo)
    programa_completado        boolean       NOT NULL DEFAULT false,
    programa_completado_en     timestamptz,
    dia_post_programa          smallint      NOT NULL DEFAULT 0 CHECK (dia_post_programa >= 0),
    creado_en                  timestamptz   NOT NULL DEFAULT now(),
    actualizado_en             timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX participantes_programa_mentor_idx    ON participantes_programa (mentor_id);
CREATE INDEX participantes_programa_celula_idx    ON participantes_programa (celula_id);
CREATE INDEX participantes_programa_activado_idx  ON participantes_programa (programa_activado_en);

CREATE TABLE puntajes_participante (
    -- Fila volátil 1:1 (P-16): los contadores que el cron reescribe a diario,
    -- separados de la identidad estable del perfil. CACHÉ de sus ledgers:
    -- puntos_liga = 100 + Σ(ajustes_puntos_liga.delta_aplicado)  [verificación al final]
    participante_id uuid          PRIMARY KEY REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    coherencia         numeric(5,2)  NOT NULL DEFAULT 100 CHECK (coherencia BETWEEN 0 AND 100),
    puntos_liga        integer       NOT NULL DEFAULT 100 CHECK (puntos_liga >= 0),
    racha_actual       smallint      NOT NULL DEFAULT 0 CHECK (racha_actual >= 0),
    racha_maxima       smallint      NOT NULL DEFAULT 0,
    actualizado_en     timestamptz   NOT NULL DEFAULT now(),
    CHECK (racha_maxima >= racha_actual)
) WITH (fillfactor = 70);  -- fila reescrita a diario: espacio libre para HOT updates (no inflan índices)
CREATE INDEX puntajes_liga_idx       ON puntajes_participante (puntos_liga DESC);   -- ORDER BY del ranking (hoy sin índice)
CREATE INDEX puntajes_coherencia_idx ON puntajes_participante (coherencia DESC);

-- ── Puntos: ledger + series + snapshots (P-06, P-18) ────────────────────────

CREATE TABLE ajustes_puntos_liga (
    id                 bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    participante_id uuid          NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    motivo             motivo_puntos NOT NULL,
    delta              smallint      NOT NULL,
    delta_aplicado     smallint      NOT NULL,  -- tras piso 0 (regla actual)
    saldo_posterior    integer       NOT NULL,  -- permite localizar divergencias del invariante
    nota               text,
    creado_en          timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ajustes_perfil_idx ON ajustes_puntos_liga (participante_id, creado_en);
CREATE INDEX ajustes_motivo_idx ON ajustes_puntos_liga (motivo, creado_en);
COMMENT ON TABLE ajustes_puntos_liga IS 'LEDGER (fuente de verdad de puntos_liga). Regla de app: asiento + saldo en la MISMA transacción (P-06).';

CREATE TABLE historial_coherencia (
    -- NUEVA: hoy el score se sobrescribe sin dejar rastro (P-18)
    participante_id uuid         NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    fecha              date         NOT NULL,
    valor              numeric(5,2) NOT NULL CHECK (valor BETWEEN 0 AND 100),
    PRIMARY KEY (participante_id, fecha)
);

CREATE TABLE ranking_aprendices (
    -- Snapshot del cron nocturno. Reemplaza el full-scan por request (general_ranking_scores()).
    -- DECISIÓN 2026-08-24: en el ranking público aparece SOLO el rol APRENDIZ — el cron filtra
    -- por rol al generar el snapshot; los demás participantes acumulan puntos pero no figuran.
    fecha              date         NOT NULL,
    tipo               tipo_ranking NOT NULL,
    participante_id uuid         NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    posicion           integer      NOT NULL CHECK (posicion > 0),
    puntaje            numeric(10,2) NOT NULL,
    PRIMARY KEY (fecha, tipo, participante_id)
);
CREATE INDEX ranking_apr_lectura_idx ON ranking_aprendices (tipo, fecha, posicion);
CREATE INDEX ranking_apr_perfil_idx  ON ranking_aprendices (participante_id);

CREATE TABLE ranking_celulas (
    fecha         date          NOT NULL,
    celula_id     uuid          NOT NULL REFERENCES celulas (id) ON DELETE CASCADE,
    posicion      integer       NOT NULL CHECK (posicion > 0),
    puntaje_grupo numeric(10,2) NOT NULL,
    PRIMARY KEY (fecha, celula_id)
);
CREATE INDEX ranking_cel_lectura_idx ON ranking_celulas (fecha, posicion);
CREATE INDEX ranking_cel_celula_idx  ON ranking_celulas (celula_id);
-- ============================================================================
-- HÁBITOS UNIFICADOS (catálogo + personales), Santuario, radar, diario, espíritu
-- ============================================================================

-- Catálogos-tabla (P-23): las listas volátiles dejan de ser enums de Postgres.
-- Patrón tomado de wall_categories (el único catálogo bien resuelto del modelo viejo).

CREATE TABLE categorias_habito (
    clave          text        PRIMARY KEY CHECK (clave = upper(clave)),  -- CUERPO, MENTE, CONSCIENCIA, ESPIRITU
    nombre         text        NOT NULL,
    orden          smallint    NOT NULL DEFAULT 0,
    activa         boolean     NOT NULL DEFAULT true,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE iconos_habito (
    clave     text        PRIMARY KEY CHECK (clave = upper(clave)),  -- espeja ICON_MAP de la app
    nombre    text        NOT NULL,
    activo    boolean     NOT NULL DEFAULT true,
    creado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE entradas_diario (
    -- Va antes que registros_habito (FK de consolidación de journaling).
    id                 uuid                PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id uuid                NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    fecha              date                NOT NULL,
    tipo               tipo_entrada_diario NOT NULL,
    contenido_texto    text,
    audio_bucket       text,
    audio_ruta         text,               -- P-03: ruta, no URL
    transcripcion      text,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (participante_id, fecha, tipo)   -- la clave de negocio actual, intacta
);
CREATE INDEX entradas_diario_perfil_fecha_idx ON entradas_diario (participante_id, fecha);

CREATE TABLE habitos (
    -- UNIFICA habits + personal_habits (P-12). El discriminante es `ambito`.
    id                        uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    ambito                    ambito_habito   NOT NULL DEFAULT 'SISTEMA',
    participante_id        uuid            REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    titulo                    text            NOT NULL,
    descripcion               text,
    tipo                      tipo_habito     NOT NULL DEFAULT 'CHECKBOX',
    categoria_clave           text            NOT NULL REFERENCES categorias_habito (clave) ON DELETE RESTRICT,
    icono_clave               text            REFERENCES iconos_habito (clave) ON DELETE SET NULL,
    grupo                     text,
    clave_sistema             text            UNIQUE,      -- identidad funcional estable (integraciones)
    exigencia_evidencia       exigencia_evidencia NOT NULL DEFAULT 'OPCIONAL',
    tipo_entrada_diario       tipo_entrada_diario,         -- solo JOURNALING (CHECK abajo)
    es_opcional               boolean         NOT NULL DEFAULT false,
    obligatorio_en_intoxicacion boolean       NOT NULL DEFAULT false,
    eleccion_dia_semanal      boolean         NOT NULL DEFAULT false,
    horas_extra_evidencia     smallint        CHECK (horas_extra_evidencia >= 0),  -- NULL = usar el global (semántica actual)
    dia_limite_edicion_libre  smallint        CHECK (dia_limite_edicion_libre BETWEEN 1 AND 90),
    plantilla_clave           plantilla_habito_personal,   -- solo PERSONAL
    etiqueta_meta             text,                        -- solo PERSONAL (goalLabel)
    orden                     smallint        NOT NULL DEFAULT 0,
    activo                    boolean         NOT NULL DEFAULT true,   -- BAJA LÓGICA: el reemplazo del CASCADE asesino (P-02)
    creado_en                 timestamptz     NOT NULL DEFAULT now(),
    actualizado_en            timestamptz     NOT NULL DEFAULT now(),
    -- is_blocking ELIMINADA: deprecada y sin lectores (documentado en el schema viejo)
    CONSTRAINT habito_ambito_coherente CHECK (
        (ambito = 'SISTEMA'  AND participante_id IS NULL  AND plantilla_clave IS NULL AND etiqueta_meta IS NULL)
     OR (ambito = 'PERSONAL' AND participante_id IS NOT NULL)
    ),
    CONSTRAINT diario_solo_journaling CHECK (tipo_entrada_diario IS NULL OR tipo = 'JOURNALING')
);
CREATE UNIQUE INDEX habitos_titulo_sistema_uk ON habitos (titulo) WHERE ambito = 'SISTEMA';  -- los personales pueden repetir título entre aprendices
CREATE INDEX habitos_catalogo_idx  ON habitos (activo, orden) WHERE ambito = 'SISTEMA';       -- el catálogo del día (hoy: 0 índices, P-15)
CREATE INDEX habitos_personales_idx ON habitos (participante_id, activo) WHERE ambito = 'PERSONAL';
CREATE INDEX habitos_categoria_idx ON habitos (categoria_clave);

CREATE TABLE horarios_habito (
    id             uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
    habito_id      uuid     NOT NULL REFERENCES habitos (id) ON DELETE CASCADE,  -- configuración: sí muere con el hábito
    dia_inicio     smallint NOT NULL CHECK (dia_inicio BETWEEN 1 AND 90),
    dia_fin        smallint CHECK (dia_fin >= dia_inicio),
    tipo_dia       tipo_dia NOT NULL DEFAULT 'DISCIPLINA',
    hora_disparo   time,    -- antes text "HH:mm" (P-05)
    hora_limite    time,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX horarios_habito_lookup_idx ON horarios_habito (habito_id, dia_inicio);  -- EL índice que faltaba (P-15)

CREATE TABLE guias_habito (
    id                uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
    habito_id         uuid     NOT NULL REFERENCES habitos (id) ON DELETE CASCADE,
    dia_inicio        smallint NOT NULL DEFAULT 1 CHECK (dia_inicio BETWEEN 1 AND 90),
    dia_fin           smallint CHECK (dia_fin >= dia_inicio),
    que_hacer         text,
    como_hacerlo      text,
    ciencia           text,
    renaser           text,
    alquimia          text,
    resultados        text,
    mantra_titulo     text,
    mantra_intro      text,
    mantra_cuerpo     text,
    referencia_fuente text,
    creado_en         timestamptz NOT NULL DEFAULT now(),
    actualizado_en    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (habito_id, dia_inicio)
);

CREATE TABLE adjuntos_guia (
    id             uuid              PRIMARY KEY DEFAULT gen_random_uuid(),
    guia_id        uuid              NOT NULL REFERENCES guias_habito (id) ON DELETE CASCADE,
    seccion        seccion_guia      NOT NULL,
    tipo_medio     tipo_medio_guia   NOT NULL DEFAULT 'ENLACE',
    url            text,             -- solo ENLACE, tal como la pegó el alquimista (no normalizar al guardar)
    ruta_storage   text,             -- solo IMAGEN/AUDIO; bucket habit-guide-media. JAMÁS una URL (regla de oro heredada)
    mime           text,
    tamano_bytes   integer CHECK (tamano_bytes > 0),
    nombre_original text,
    titulo         text,
    orden          smallint          NOT NULL DEFAULT 0,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT adjunto_enlace_xor_archivo CHECK (
        (tipo_medio = 'ENLACE' AND url IS NOT NULL AND ruta_storage IS NULL)
     OR (tipo_medio <> 'ENLACE' AND ruta_storage IS NOT NULL AND url IS NULL)
    )
);
CREATE INDEX adjuntos_guia_idx ON adjuntos_guia (guia_id, seccion, orden);

CREATE TABLE preferencias_horario (
    -- Override del aprendiz. Sirve a AMBOS ámbitos (el horario del hábito personal vive acá — unificación P-12).
    participante_id   uuid     NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    habito_id            uuid     NOT NULL REFERENCES habitos (id) ON DELETE CASCADE,
    hora_disparo         time,    -- NULL = default del horario del hábito
    hora_limite          time,
    recordatorio_activo  boolean  NOT NULL DEFAULT true,
    minutos_recordatorio smallint CHECK (minutos_recordatorio >= 0),
    creado_en            timestamptz NOT NULL DEFAULT now(),
    actualizado_en       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (participante_id, habito_id)
);
CREATE INDEX preferencias_habito_idx ON preferencias_horario (habito_id);

CREATE TABLE cambios_horario_pendientes (
    -- LA ENTIDAD que reemplaza el grupo pending_* (P-13). Su existencia = "hay cambio programado".
    participante_id   uuid     NOT NULL,
    habito_id            uuid     NOT NULL,
    hora_disparo         time,    -- NULL acá = "sin preferencia desde la fecha" (ya sin ambigüedad: la fila existe)
    hora_limite          time,
    recordatorio_activo  boolean,
    minutos_recordatorio smallint,
    fecha_efectiva       date     NOT NULL,
    creado_en            timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (participante_id, habito_id),
    FOREIGN KEY (participante_id, habito_id)
        REFERENCES preferencias_horario (participante_id, habito_id) ON DELETE CASCADE
);

CREATE TABLE historial_cambios_horario (
    -- Fusión de habit_preference_changes + personal_habit_edit_log (con FKs reales — P-09).
    id                 bigint   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    participante_id uuid     NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    habito_id          uuid     NOT NULL REFERENCES habitos (id) ON DELETE CASCADE,
    cambiado_el        date     NOT NULL,   -- el "día" contra el que se mide la cuota semanal
    accion             text,                -- edición de horario, edición/borrado de personal, etc.
    hora_disparo       time,
    hora_limite        time,
    creado_en          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX hist_cambios_cuota_idx ON historial_cambios_horario (participante_id, cambiado_el);
CREATE INDEX hist_cambios_habito_idx ON historial_cambios_horario (habito_id);

CREATE TABLE desbloqueos_habito (
    participante_id uuid     NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    habito_id          uuid     NOT NULL REFERENCES habitos (id) ON DELETE CASCADE,
    dia_desbloqueo     smallint NOT NULL CHECK (dia_desbloqueo BETWEEN 1 AND 90),
    elegido_en         timestamptz,   -- NULL = lo puso el relleno automático (semántica actual, documentada)
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (participante_id, habito_id)
);
CREATE INDEX desbloqueos_cron_idx ON desbloqueos_habito (participante_id, dia_desbloqueo);
CREATE INDEX desbloqueos_habito_idx ON desbloqueos_habito (habito_id);

CREATE TABLE dias_semanales_habito (
    -- Día elegido para hábitos de eleccion_dia_semanal (fecha concreta, no día de semana — decisión actual que se conserva)
    participante_id uuid NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    habito_id          uuid NOT NULL REFERENCES habitos (id) ON DELETE CASCADE,
    fecha_ejecucion    date NOT NULL,
    semana_inicio      date NOT NULL,   -- congelada al elegir (el ancla de semana puede cambiar; la elección histórica no)
    creado_en          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (participante_id, habito_id, fecha_ejecucion)
);
CREATE INDEX dias_semanales_semana_idx ON dias_semanales_habito (participante_id, semana_inicio);
CREATE INDEX dias_semanales_habito_idx ON dias_semanales_habito (habito_id);

CREATE TABLE renombres_habito (
    participante_id uuid NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    habito_id          uuid NOT NULL REFERENCES habitos (id) ON DELETE CASCADE,
    titulo_personal    text NOT NULL,
    motivo             text NOT NULL,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (participante_id, habito_id)
);
CREATE INDEX renombres_habito_idx ON renombres_habito (habito_id);

CREATE TABLE registros_habito (
    -- EL track diario. Fusiona habit_tracks + personal_habit_tracks (P-12).
    id                        uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id        uuid            NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    habito_id                 uuid            NOT NULL REFERENCES habitos (id) ON DELETE RESTRICT,  -- P-02: el catálogo NO arrastra historial
    fecha_ejecucion           date            NOT NULL,
    dia_programa              smallint        NOT NULL CHECK (dia_programa BETWEEN 0 AND 90),  -- snapshot temporal (hecho histórico, no redundancia)
    tipo_dia                  tipo_dia        NOT NULL,
    es_opcional               boolean         NOT NULL DEFAULT false,
    estado                    estado_registro NOT NULL DEFAULT 'PENDIENTE',
    puntos_otorgados          smallint        NOT NULL DEFAULT 0,   -- guarda =0 conserva la idempotencia atómica actual
    respuesta_texto           text,
    calificacion_productividad smallint       CHECK (calificacion_productividad BETWEEN 1 AND 10),
    entrada_diario_id         uuid            REFERENCES entradas_diario (id) ON DELETE SET NULL,  -- consolidación N:1 deliberada
    completado_en             timestamptz,
    creado_en                 timestamptz     NOT NULL DEFAULT now(),
    actualizado_en            timestamptz     NOT NULL DEFAULT now(),
    UNIQUE (participante_id, habito_id, fecha_ejecucion)   -- idempotencia del cron, intacta
) WITH (fillfactor = 85);  -- cada fila se actualiza pocas veces (completar/expirar): margen para HOT updates
CREATE INDEX registros_dia_idx     ON registros_habito (participante_id, fecha_ejecucion);  -- "mi día" (hot path)
CREATE INDEX registros_estado_idx  ON registros_habito (estado, fecha_ejecucion);              -- expiración por lotes del cron
CREATE INDEX registros_habito_idx  ON registros_habito (habito_id);                            -- FK (RESTRICT necesita verificar rápido)
CREATE INDEX registros_diario_idx  ON registros_habito (entrada_diario_id) WHERE entrada_diario_id IS NOT NULL;

CREATE TABLE sesiones_bloqueo (
    -- Santuario. 1:1 con el track → PK=FK; pierde el perfil duplicado (derivable vía registro).
    registro_habito_id   uuid                  PRIMARY KEY REFERENCES registros_habito (id) ON DELETE CASCADE,
    estado               estado_sesion_bloqueo NOT NULL DEFAULT 'ACTIVA',
    iniciada_en          timestamptz           NOT NULL,
    terminada_en         timestamptz,
    duracion_minima_min  smallint              NOT NULL CHECK (duracion_minima_min > 0),
    motivo_salida        motivo_salida_bloqueo,
    evidencia_salida_bucket text,
    evidencia_salida_ruta   text,              -- P-03
    penalizacion_aplicada boolean              NOT NULL DEFAULT false,
    creado_en            timestamptz NOT NULL DEFAULT now(),
    actualizado_en       timestamptz NOT NULL DEFAULT now(),
    CHECK (terminada_en IS NULL OR terminada_en >= iniciada_en)
);

CREATE TABLE rachas_sin_celular (
    id                 uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    registro_habito_id uuid         NOT NULL REFERENCES registros_habito (id) ON DELETE CASCADE,
    -- Desnormalización DECLARADA (§6.2): la racha cruza medianoche; la búsqueda operativa
    -- "racha viva del aprendiz" no puede resolverse por el track del día.
    participante_id uuid         NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    iniciada_en        timestamptz  NOT NULL,
    terminada_en       timestamptz,
    horas_objetivo     smallint     NOT NULL CHECK (horas_objetivo BETWEEN 3 AND 24),
    estado             estado_racha NOT NULL DEFAULT 'ACTIVA',
    duracion_minutos   integer      CHECK (duracion_minutos >= 0),
    motivo_ruptura     text,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    CHECK (terminada_en IS NULL OR terminada_en >= iniciada_en)
);
CREATE INDEX rachas_registro_idx ON rachas_sin_celular (registro_habito_id);
CREATE UNIQUE INDEX rachas_viva_uk ON rachas_sin_celular (participante_id) WHERE estado = 'ACTIVA';  -- a lo sumo UNA racha viva por aprendiz, y lookup O(1)

CREATE TABLE revisiones_semanales_sin_celular (
    participante_id  uuid     NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    semana_inicio       date     NOT NULL,
    rachas_completas    smallint NOT NULL DEFAULT 0 CHECK (rachas_completas >= 0),
    rachas_requeridas   smallint NOT NULL CHECK (rachas_requeridas >= 0),
    puntos_penalizacion smallint NOT NULL DEFAULT 0,
    evaluada_en         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (participante_id, semana_inicio)
);

CREATE TABLE registros_radar (
    -- Check-in "Código Renaser". Cuelga del PERFIL (es actividad del programa), no del usuario.
    id                 uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id uuid     NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    que_hago           text     NOT NULL,
    que_pienso         text     NOT NULL,
    que_siento         text     NOT NULL,
    nivel_energia      smallint NOT NULL CHECK (nivel_energia BETWEEN 1 AND 10),
    que_evito          text     NOT NULL,
    creado_en          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX radar_perfil_fecha_idx ON registros_radar (participante_id, creado_en DESC);  -- reemplaza los 3 índices solapados (P-29)

-- ── Espíritu ────────────────────────────────────────────────────────────────

CREATE TABLE audios_espiritu (
    dia            smallint PRIMARY KEY CHECK (dia BETWEEN 1 AND 90),  -- clave natural pura (antes surrogate + UNIQUE)
    titulo         text     NOT NULL,
    drive_file_id  text     NOT NULL,   -- Google Drive (integración actual, detrás de puerto en Java)
    mime           text     NOT NULL,
    tamano_bytes   integer,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE registros_espiritu (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id uuid        NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    dia                smallint    NOT NULL REFERENCES audios_espiritu (dia) ON DELETE RESTRICT,  -- P-24: FK real (antes join por número sin FK)
    desbloqueado_en    timestamptz NOT NULL,
    fecha_limite       timestamptz NOT NULL,
    entregado_en       timestamptz,
    resumen_texto      text,
    estado             estado_registro_espiritu NOT NULL DEFAULT 'PENDIENTE',
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (participante_id, dia)
);
CREATE INDEX registros_espiritu_dia_idx ON registros_espiritu (dia);

CREATE TABLE audioterapias (
    semana         smallint PRIMARY KEY CHECK (semana BETWEEN 1 AND 13),  -- clave natural pura
    titulo         text     NOT NULL,
    ruta_storage   text     NOT NULL,
    mime           text     NOT NULL,
    tamano_bytes   integer,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);
-- ============================================================================
-- ROCAS, VERDUGO, EVIDENCIAS, ESPEJO SOMBRA, ONBOARDING, CONTRATOS DE FASE
-- ============================================================================

CREATE TABLE rocas_maestras (
    id                 uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id uuid         NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    eje                eje_objetivo NOT NULL,   -- antes texto libre validado solo en TS
    objetivo           text         NOT NULL,
    creado_en          timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (participante_id, eje)
);

CREATE TABLE rocas_semanales (
    id                    uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
    roca_maestra_id       uuid     NOT NULL REFERENCES rocas_maestras (id) ON DELETE CASCADE,
    numero_semana         smallint NOT NULL CHECK (numero_semana BETWEEN 1 AND 13),
    titulo                text     NOT NULL,
    obstaculo             text,
    contingencia          text,
    autoevaluacion_inicio smallint CHECK (autoevaluacion_inicio BETWEEN 1 AND 10),
    autoevaluacion_fin    smallint CHECK (autoevaluacion_fin BETWEEN 1 AND 10),
    bloqueo_principal     text,
    correccion            text,
    creado_en             timestamptz NOT NULL DEFAULT now(),
    actualizado_en        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (roca_maestra_id, numero_semana)
    -- critical_action_1/2/3 ELIMINADAS (P-10) → acciones_criticas
);

CREATE TABLE acciones_criticas (
    roca_semanal_id uuid     NOT NULL REFERENCES rocas_semanales (id) ON DELETE CASCADE,
    orden           smallint NOT NULL CHECK (orden BETWEEN 1 AND 3),
    descripcion     text     NOT NULL,
    PRIMARY KEY (roca_semanal_id, orden)
);
COMMENT ON TABLE acciones_criticas IS '1FN restaurada: antes columnas critical_action_1/2/3 en weekly_rocks.';

CREATE TABLE rocas_diarias (
    id                 uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id uuid         NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    fecha              date         NOT NULL,
    posicion           smallint     NOT NULL CHECK (posicion BETWEEN 1 AND 3),
    titulo             text         NOT NULL,
    descripcion        text,
    color              color_pareto NOT NULL,
    puntaje_impacto    smallint     NOT NULL CHECK (puntaje_impacto BETWEEN 1 AND 10),
    es_delegable       boolean      NOT NULL DEFAULT false,
    eje                eje_objetivo NOT NULL,   -- se conserva pese a la FK anulable a la semanal: integra el UNIQUE de negocio
    roca_semanal_id    uuid         REFERENCES rocas_semanales (id) ON DELETE SET NULL,
    hora_inicio        time,
    hora_fin           time,
    completada         boolean      NOT NULL DEFAULT false,
    completada_en      timestamptz,
    puntos_otorgados   smallint     NOT NULL DEFAULT 0,
    creado_en          timestamptz  NOT NULL DEFAULT now(),
    actualizado_en     timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (participante_id, fecha, eje, posicion)
    -- primary_evidence_id ELIMINADA (P-22) → evidencias.es_principal (índice único parcial)
);
CREATE INDEX rocas_diarias_dia_idx ON rocas_diarias (participante_id, fecha);
CREATE INDEX rocas_diarias_semanal_idx ON rocas_diarias (roca_semanal_id);

CREATE TABLE eventos_verdugo (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id uuid        NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    -- Arco exclusivo (P-08): antes (related_entity_type text, related_entity_id) sin FK.
    registro_habito_id uuid        REFERENCES registros_habito (id) ON DELETE CASCADE,
    roca_diaria_id     uuid        REFERENCES rocas_diarias (id) ON DELETE CASCADE,
    disparado_en       timestamptz NOT NULL,
    resultado          resultado_verdugo,   -- NULL = el aprendiz aún no actuó (el cron de 23:55 lo pasa a IGNORADO)
    resuelto_en        timestamptz,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT verdugo_un_destino CHECK (num_nonnulls(registro_habito_id, roca_diaria_id) = 1)
);
CREATE INDEX verdugo_perfil_idx    ON eventos_verdugo (participante_id, disparado_en);
CREATE INDEX verdugo_pendiente_idx ON eventos_verdugo (disparado_en) WHERE resultado IS NULL;  -- el barrido del cron
CREATE INDEX verdugo_registro_idx  ON eventos_verdugo (registro_habito_id) WHERE registro_habito_id IS NOT NULL;
CREATE INDEX verdugo_roca_idx      ON eventos_verdugo (roca_diaria_id) WHERE roca_diaria_id IS NOT NULL;

CREATE TABLE evidencias (
    id                 uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Desnormalización DECLARADA (§6.2): dueño directo para "mis evidencias" y RLS sin doble join.
    participante_id uuid            NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    -- Arco exclusivo (P-01): antes polimorfismo (related_entity_type, related_entity_id) SIN FK.
    -- El destino PERSONAL_HABIT desaparece: los personales ahora son registros_habito.
    registro_habito_id   uuid          REFERENCES registros_habito (id)   ON DELETE CASCADE,
    roca_diaria_id       uuid          REFERENCES rocas_diarias (id)      ON DELETE CASCADE,
    registro_espiritu_id uuid          REFERENCES registros_espiritu (id) ON DELETE CASCADE,
    tipo               tipo_evidencia  NOT NULL,
    bucket             text,
    ruta_storage       text,           -- P-03: la URL se firma al LEER, jamás se persiste
    contenido_texto    text,
    timestamp_exif     timestamptz,    -- la regla ±15 min vive en la app
    subida_en          timestamptz     NOT NULL DEFAULT now(),
    gps_lat            double precision CHECK (gps_lat  BETWEEN -90 AND 90),
    gps_lng            double precision CHECK (gps_lng BETWEEN -180 AND 180),
    es_principal       boolean         NOT NULL DEFAULT false,   -- reemplaza daily_rocks.primary_evidence_id (P-22)
    estado_validacion  estado_validacion NOT NULL DEFAULT 'PENDIENTE',  -- reemplaza 4 booleanos (P-14)
    notas_validacion   text,
    intentos_ia        smallint        NOT NULL DEFAULT 0 CHECK (intentos_ia BETWEEN 0 AND 3),
    penalizacion_aplicada boolean      NOT NULL DEFAULT false,
    publicada_en_muro  boolean         NOT NULL DEFAULT false,
    creado_en          timestamptz     NOT NULL DEFAULT now(),
    CONSTRAINT evidencia_un_destino CHECK (num_nonnulls(registro_habito_id, roca_diaria_id, registro_espiritu_id) = 1),
    CONSTRAINT evidencia_media_o_texto CHECK (
        (tipo = 'TEXTO' AND contenido_texto IS NOT NULL)
     OR (tipo <> 'TEXTO' AND bucket IS NOT NULL AND ruta_storage IS NOT NULL)
    ),
    CONSTRAINT gps_completo CHECK ((gps_lat IS NULL) = (gps_lng IS NULL)),
    CONSTRAINT principal_solo_en_roca CHECK (NOT es_principal OR roca_diaria_id IS NOT NULL)
);
CREATE INDEX evidencias_perfil_idx    ON evidencias (participante_id, subida_en DESC);
CREATE INDEX evidencias_registro_idx  ON evidencias (registro_habito_id)   WHERE registro_habito_id IS NOT NULL;
CREATE INDEX evidencias_roca_idx      ON evidencias (roca_diaria_id)       WHERE roca_diaria_id IS NOT NULL;
CREATE INDEX evidencias_espiritu_idx  ON evidencias (registro_espiritu_id) WHERE registro_espiritu_id IS NOT NULL;
CREATE UNIQUE INDEX evidencias_principal_uk ON evidencias (roca_diaria_id) WHERE es_principal;      -- una principal por roca
CREATE INDEX evidencias_cola_ia_idx   ON evidencias (subida_en) WHERE estado_validacion = 'PENDIENTE';  -- cola del validador (lote 25)
-- Regla de consumo de la cola (skill lock-skip-locked): el worker toma lotes con
--   SELECT ... WHERE estado_validacion='PENDIENTE' ORDER BY subida_en
--   LIMIT 25 FOR UPDATE SKIP LOCKED;
-- así N instancias no procesan la misma evidencia dos veces ni se bloquean entre sí.
CREATE INDEX evidencias_muro_idx      ON evidencias (subida_en DESC) WHERE publicada_en_muro;

CREATE TABLE informes_espejo_sombra (
    id                 uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id uuid     NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    semana_inicio      date     NOT NULL,   -- antes String
    cantidad_entradas  smallint NOT NULL CHECK (cantidad_entradas >= 0),
    patron_dominante   text     NOT NULL,
    pct_pasado         smallint NOT NULL CHECK (pct_pasado   BETWEEN 0 AND 100),
    pct_presente       smallint NOT NULL CHECK (pct_presente BETWEEN 0 AND 100),
    pct_futuro         smallint NOT NULL CHECK (pct_futuro   BETWEEN 0 AND 100),
    insight            text     NOT NULL,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (participante_id, semana_inicio),
    CONSTRAINT pcts_suman_100 CHECK (pct_pasado + pct_presente + pct_futuro = 100)
);

CREATE TABLE preguntas_confrontacion (
    -- Antes: confrontation_questions Json (lista relacional — regla 10 del encargo)
    informe_id uuid     NOT NULL REFERENCES informes_espejo_sombra (id) ON DELETE CASCADE,
    orden      smallint NOT NULL CHECK (orden BETWEEN 1 AND 10),
    pregunta   text     NOT NULL,
    PRIMARY KEY (informe_id, orden)
);

-- ── ONBOARDING ──────────────────────────────────────────────────────────────

CREATE TABLE estado_onboarding (
    usuario_id           uuid    PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    flujo_actual         text,
    seccion_actual       text,
    paso_actual          smallint,
    progreso_flujo       jsonb,   -- JUSTIFICADO: estado de reanudación de UI, opaco, no se consulta relacionalmente (§5.5)
    terminos_aceptados_en    timestamptz,
    pacto_aceptado_en        timestamptz,
    pacto_firmado_en         timestamptz,
    rocas_sync_aceptado_en   timestamptz,
    iniciado_en              timestamptz,
    ultima_actividad_en      timestamptz,
    completado               boolean NOT NULL DEFAULT false,
    completado_en            timestamptz,
    creado_en                timestamptz NOT NULL DEFAULT now(),
    actualizado_en           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE secciones_onboarding (
    id            smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flujo         text     NOT NULL,
    clave_seccion text     NOT NULL,
    titulo        text     NOT NULL,
    descripcion   text,
    orden         smallint NOT NULL DEFAULT 0,
    creado_en     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (flujo, clave_seccion)
);

CREATE TABLE preguntas_onboarding (
    id                  integer  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seccion_id          smallint NOT NULL REFERENCES secciones_onboarding (id) ON DELETE RESTRICT,  -- antes: (flow, section_key) copiados (P-25)
    clave_pregunta      text     NOT NULL UNIQUE,
    texto               text     NOT NULL,
    tipo                tipo_pregunta_onboarding NOT NULL,   -- enum real verificado en 0001_onboarding_schema.sql (11 valores)
    config_escala       jsonb,               -- solo tipo=ESCALA: rangos/etiquetas (la otra mitad del `options` viejo)
    requerida           boolean  NOT NULL DEFAULT false,
    orden               smallint NOT NULL DEFAULT 0,
    reglas_validacion   jsonb,               -- DSL de validación del motor de formularios: jsonb justificado
    pregunta_padre_id   integer  REFERENCES preguntas_onboarding (id) ON DELETE SET NULL,  -- condicionales (antes clave suelta)
    creado_en           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX preguntas_onb_seccion_idx ON preguntas_onboarding (seccion_id, orden);
CREATE INDEX preguntas_onb_padre_idx   ON preguntas_onboarding (pregunta_padre_id);

CREATE TABLE opciones_pregunta (
    -- Antes: la mitad "select choices" del Json `options` (verificado: 0001 dice
    -- "scale ranges / select choices"). Los rangos de escala van en preguntas.config_escala.
    -- Solo aplica a SELECCION_UNICA / SELECCION_MULTIPLE.
    pregunta_id integer  NOT NULL REFERENCES preguntas_onboarding (id) ON DELETE CASCADE,
    orden       smallint NOT NULL,
    valor       text     NOT NULL,
    etiqueta    text     NOT NULL,
    PRIMARY KEY (pregunta_id, orden)
);

CREATE TABLE medias_onboarding (
    id                bigint  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id        uuid    NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    flujo             text,
    clave_pregunta    text,
    clase             text    NOT NULL,   -- audio | firma | documento (kind actual)
    bucket            text    NOT NULL,
    ruta_storage      text    NOT NULL,
    mime              text,
    tamano_bytes      bigint,
    duracion_segundos numeric(8,2),
    metadatos         jsonb,
    creado_en         timestamptz NOT NULL DEFAULT now(),
    actualizado_en    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, bucket, ruta_storage)
);
CREATE INDEX medias_onb_borrado_idx ON medias_onboarding (usuario_id, flujo);  -- el camino de borrado sin índice de hoy

CREATE TABLE respuestas_onboarding (
    id             bigint  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id     uuid    NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    pregunta_id    integer NOT NULL REFERENCES preguntas_onboarding (id) ON DELETE RESTRICT,  -- P-25: reemplaza 4 columnas copiadas
    valor_texto    text,
    valor_numero   numeric,
    valor_booleano boolean,
    valor_escala   smallint CHECK (valor_escala BETWEEN 1 AND 10),
    valor_json     jsonb,
    media_id       bigint  REFERENCES medias_onboarding (id) ON DELETE SET NULL,  -- P-09: antes sin FK
    aceptada_en    timestamptz,
    respondida_en  timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, pregunta_id),
    -- EAV tipado: exactamente un valor (o solo media adjunta)
    CONSTRAINT un_solo_valor CHECK (num_nonnulls(valor_texto, valor_numero, valor_booleano, valor_escala, valor_json) <= 1)
);
CREATE INDEX respuestas_onb_pregunta_idx ON respuestas_onboarding (pregunta_id);
CREATE INDEX respuestas_onb_media_idx    ON respuestas_onboarding (media_id) WHERE media_id IS NOT NULL;

CREATE TABLE grabaciones_v90 (
    id                bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id        uuid          NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    fase              text          NOT NULL,
    eje               text          NOT NULL,
    indice            smallint      NOT NULL CHECK (indice >= 0),
    clave_pregunta    text,
    grabada           boolean       NOT NULL DEFAULT false,
    media_id          bigint        REFERENCES medias_onboarding (id) ON DELETE RESTRICT,  -- la media no se borra con grabación viva (regla actual)
    duracion_segundos numeric(8,2),
    transcripcion     text,
    estado_ia         estado_ia_v90 NOT NULL DEFAULT 'PENDIENTE',   -- antes String libre
    intentos_ia       smallint      NOT NULL DEFAULT 0 CHECK (intentos_ia BETWEEN 0 AND 3),
    feedback_ia       jsonb,        -- salida semiestructurada del modelo: jsonb justificado
    grabada_en        timestamptz,
    creado_en         timestamptz NOT NULL DEFAULT now(),
    actualizado_en    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, fase, eje, indice)
    -- audio_url ELIMINADA (P-33): la media ya tiene bucket+ruta
);
CREATE INDEX grabaciones_v90_media_idx ON grabaciones_v90 (media_id);

CREATE TABLE contratos_fase (
    id                 uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id uuid          NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    fase               fase_programa NOT NULL,
    bucket             text          NOT NULL DEFAULT 'onboarding-signatures',
    ruta_firma         text          NOT NULL,   -- P-03: ruta dentro del bucket privado, no URL
    firmado_en         timestamptz   NOT NULL DEFAULT now(),
    creado_en          timestamptz   NOT NULL DEFAULT now(),
    UNIQUE (participante_id, fase)
);
-- ============================================================================
-- ACADEMIA (antes fuera de Prisma — se integra al esquema único)
-- Ids `text` = clave natural externa de Skool. Única familia con ON UPDATE CASCADE.
-- ============================================================================

CREATE TABLE cursos (
    id             text        PRIMARY KEY,   -- id de Skool, estable entre imports
    slug           text        NOT NULL,
    titulo         text        NOT NULL,
    descripcion    text,
    portada_ruta   text,                      -- bucket cursos-media
    orden          smallint    NOT NULL DEFAULT 0,
    publicado      boolean     NOT NULL DEFAULT false,
    acceso         acceso_curso NOT NULL DEFAULT 'RESTRINGIDO',
    origen         text        NOT NULL DEFAULT 'skool',
    dia_desbloqueo smallint    CHECK (dia_desbloqueo BETWEEN 0 AND 90),
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
    -- roles_permitidos text[] ELIMINADA (P-20) → roles_permitidos_curso
);

CREATE TABLE roles_permitidos_curso (
    curso_id text     NOT NULL REFERENCES cursos (id) ON DELETE CASCADE ON UPDATE CASCADE,
    rol_id   smallint NOT NULL REFERENCES roles (id)  ON DELETE CASCADE,
    PRIMARY KEY (curso_id, rol_id)
);
CREATE INDEX roles_permitidos_rol_idx ON roles_permitidos_curso (rol_id);  -- "qué cursos ve tal rol"
COMMENT ON TABLE roles_permitidos_curso IS 'Antes array text[] duplicando el concepto rol fuera de RBAC.';

CREATE TABLE secciones_curso (
    id             text     PRIMARY KEY,
    curso_id       text     NOT NULL REFERENCES cursos (id) ON DELETE CASCADE ON UPDATE CASCADE,
    titulo         text     NOT NULL,
    orden          smallint NOT NULL DEFAULT 0,
    dia_desbloqueo smallint CHECK (dia_desbloqueo BETWEEN 0 AND 90)
);
CREATE INDEX secciones_curso_idx ON secciones_curso (curso_id, orden);

CREATE TABLE lecciones (
    id                 text        PRIMARY KEY,
    curso_id           text        NOT NULL REFERENCES cursos (id) ON DELETE CASCADE ON UPDATE CASCADE,
    seccion_id         text        REFERENCES secciones_curso (id) ON DELETE SET NULL ON UPDATE CASCADE,
    titulo             text        NOT NULL,
    orden              smallint    NOT NULL DEFAULT 0,
    cuerpo_html        text,
    cuerpo_md          text,
    video_tipo         tipo_video_leccion,
    video_url          text,       -- YOUTUBE: URL | STORAGE: ruta en cursos-media (discriminada por video_tipo)
    video_miniatura_url text,
    video_duracion_ms  bigint CHECK (video_duracion_ms >= 0),
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT video_coherente CHECK (video_tipo IS NULL OR video_url IS NOT NULL)
);
CREATE INDEX lecciones_curso_idx   ON lecciones (curso_id, orden);
CREATE INDEX lecciones_seccion_idx ON lecciones (seccion_id);

CREATE TABLE recursos_leccion (
    id         bigint   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leccion_id text     NOT NULL REFERENCES lecciones (id) ON DELETE CASCADE ON UPDATE CASCADE,
    nombre     text,
    url        text     NOT NULL,
    orden      smallint NOT NULL DEFAULT 0
);
CREATE INDEX recursos_leccion_idx ON recursos_leccion (leccion_id, orden);

CREATE TABLE grupos (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre    text   NOT NULL UNIQUE,
    creado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE miembros_grupo (
    grupo_id   bigint NOT NULL REFERENCES grupos (id)   ON DELETE CASCADE,
    usuario_id uuid   NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    creado_en  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (grupo_id, usuario_id)
);
CREATE INDEX miembros_grupo_usuario_idx ON miembros_grupo (usuario_id);

CREATE TABLE asignaciones_curso (
    -- El arco exclusivo usuario⊕grupo YA EXISTÍA y estaba bien — se conserva tal cual.
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    curso_id     text        NOT NULL REFERENCES cursos (id) ON DELETE CASCADE ON UPDATE CASCADE,
    usuario_id   uuid        REFERENCES usuarios (id) ON DELETE CASCADE,
    grupo_id     bigint      REFERENCES grupos (id)   ON DELETE CASCADE,
    desde        timestamptz,   -- NULL = desde siempre
    hasta        timestamptz,   -- NULL = sin vencimiento
    revocada_en  timestamptz,   -- NULL = vigente
    asignada_por uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    creado_en    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT asignacion_destino_unico CHECK (num_nonnulls(usuario_id, grupo_id) = 1)
);
CREATE INDEX asignaciones_curso_idx   ON asignaciones_curso (curso_id);
CREATE INDEX asignaciones_usuario_idx ON asignaciones_curso (usuario_id) WHERE usuario_id IS NOT NULL;
CREATE INDEX asignaciones_grupo_idx   ON asignaciones_curso (grupo_id)   WHERE grupo_id IS NOT NULL;
CREATE INDEX asignaciones_asignador_idx ON asignaciones_curso (asignada_por) WHERE asignada_por IS NOT NULL;

CREATE TABLE progreso_lecciones (
    usuario_id    uuid NOT NULL REFERENCES usuarios (id)  ON DELETE CASCADE,
    leccion_id    text NOT NULL REFERENCES lecciones (id) ON DELETE CASCADE ON UPDATE CASCADE,
    completada_en timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, leccion_id)
);
CREATE INDEX progreso_leccion_idx ON progreso_lecciones (leccion_id);

CREATE TABLE recomendaciones_academia (
    -- Cache diaria de la recomendación IA. P-19: FK real; títulos copiados y curso transitivo ELIMINADOS.
    participante_id uuid NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    fecha              date NOT NULL,   -- antes String YYYY-MM-DD
    leccion_id         text NOT NULL REFERENCES lecciones (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    motivo             text NOT NULL,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (participante_id, fecha)
);
CREATE INDEX recomendaciones_leccion_idx ON recomendaciones_academia (leccion_id);

-- ============================================================================
-- COMUNIDAD (muro)
-- ============================================================================

CREATE TABLE categorias_muro (
    clave          text        PRIMARY KEY,
    etiqueta       text        NOT NULL,
    emoji          text        NOT NULL,
    orden          smallint    NOT NULL DEFAULT 0,
    activa         boolean     NOT NULL DEFAULT true,
    es_sistema     boolean     NOT NULL DEFAULT false,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE publicaciones_muro (
    id              uuid             PRIMARY KEY DEFAULT gen_random_uuid(),
    autor_id        uuid             NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo            tipo_publicacion NOT NULL DEFAULT 'MANUAL',
    categoria_clave text             REFERENCES categorias_muro (clave) ON DELETE RESTRICT,
    texto           text             NOT NULL,
    oculta          boolean          NOT NULL DEFAULT false,   -- moderación (semántica propia, distinta de borrar)
    creado_en       timestamptz      NOT NULL DEFAULT now(),
    actualizado_en  timestamptz      NOT NULL DEFAULT now()
    -- media_url / media_mime legacy ELIMINADAS (P-30): la media vive solo en medias_publicacion
);
CREATE INDEX muro_feed_idx      ON publicaciones_muro (creado_en DESC);                    -- keyset del feed (se conserva)
CREATE INDEX muro_categoria_idx ON publicaciones_muro (categoria_clave, creado_en DESC);
CREATE INDEX muro_autor_idx     ON publicaciones_muro (autor_id);

CREATE TABLE medias_publicacion (
    id             uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
    publicacion_id uuid     NOT NULL REFERENCES publicaciones_muro (id) ON DELETE CASCADE,
    bucket         text     NOT NULL DEFAULT 'wall',
    ruta_storage   text     NOT NULL,
    mime           text     NOT NULL,
    orden          smallint NOT NULL DEFAULT 0,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (publicacion_id, orden)
);

CREATE TABLE reacciones_muro (
    publicacion_id uuid          NOT NULL REFERENCES publicaciones_muro (id) ON DELETE CASCADE,
    usuario_id     uuid          NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo           tipo_reaccion NOT NULL,
    creado_en      timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (publicacion_id, usuario_id)   -- antes surrogate + UNIQUE (P-28)
);
CREATE INDEX reacciones_usuario_idx ON reacciones_muro (usuario_id);

CREATE TABLE comentarios_muro (
    id             uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    publicacion_id uuid    NOT NULL REFERENCES publicaciones_muro (id) ON DELETE CASCADE,
    autor_id       uuid    NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    texto          text    NOT NULL,
    oculto         boolean NOT NULL DEFAULT false,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX comentarios_post_idx  ON comentarios_muro (publicacion_id, creado_en);
CREATE INDEX comentarios_autor_idx ON comentarios_muro (autor_id);

CREATE TABLE testimonios (
    id                  uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id          uuid     REFERENCES usuarios (id) ON DELETE SET NULL,
    publicacion_muro_id uuid     REFERENCES publicaciones_muro (id) ON DELETE SET NULL,  -- P-09: antes columna suelta
    nombre              text     NOT NULL,
    rol_texto           text,
    avatar_url          text,
    foto_evento_ruta    text,
    texto               text     NOT NULL,
    estrellas           smallint NOT NULL CHECK (estrellas BETWEEN 1 AND 5),
    destacado           boolean  NOT NULL DEFAULT false,
    creado_en           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX testimonios_destacado_idx ON testimonios (destacado, creado_en DESC);
CREATE INDEX testimonios_post_idx      ON testimonios (publicacion_muro_id);
CREATE INDEX testimonios_usuario_idx   ON testimonios (usuario_id);

-- ============================================================================
-- CALENDARIO (split de calendar_events — P-11)
-- ============================================================================

CREATE TABLE niveles_membresia (
    id                  smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rango               smallint NOT NULL UNIQUE,
    nombre              text     NOT NULL,
    pct_progreso_minimo smallint NOT NULL CHECK (pct_progreso_minimo BETWEEN 0 AND 100),
    creado_en           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE eventos (
    id                uuid                 PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo            text                 NOT NULL,
    descripcion       text,
    portada_ruta      text,                -- bucket calendar-events
    inicia_en         timestamptz          NOT NULL,
    duracion_minutos  integer              CHECK (duracion_minutos > 0),
    timezone          text                 NOT NULL,
    tipo_ubicacion    tipo_ubicacion       NOT NULL,
    valor_ubicacion   text,
    tipo_audiencia    tipo_audiencia       NOT NULL,
    nivel_minimo_id   smallint             REFERENCES niveles_membresia (id) ON DELETE SET NULL,
    curso_id          text                 REFERENCES cursos (id) ON DELETE SET NULL ON UPDATE CASCADE,  -- FK real (antes texto suelto)
    celula_destino_id uuid                 REFERENCES celulas (id) ON DELETE CASCADE,
    estado            estado_evento        NOT NULL DEFAULT 'BORRADOR',
    tipo_evento       tipo_evento_calendario NOT NULL,
    notificar_al_crear boolean             NOT NULL DEFAULT false,
    recordar_por_email boolean             NOT NULL DEFAULT false,
    -- Semántica verificada del modelo viejo (reminders.ts): reminderRules null = hereda
    -- las reglas del TIPO de evento; [] = el admin decidió que no avisa. Con tabla hija,
    -- "0 filas" sería ambiguo — este flag conserva la distinción:
    -- false = hereda del tipo (ignora las filas) | true = rigen las filas (0 filas = no avisa)
    recordatorios_personalizados boolean   NOT NULL DEFAULT false,
    creado_por        uuid                 REFERENCES usuarios (id) ON DELETE SET NULL,  -- P-27: antes CASCADE (borrar staff borraba eventos)
    creado_en         timestamptz          NOT NULL DEFAULT now(),
    actualizado_en    timestamptz          NOT NULL DEFAULT now(),
    -- coherencia audiencia⇔campo (integridad semántica §7.2)
    CONSTRAINT audiencia_coherente CHECK (
        (tipo_audiencia <> 'NIVEL_MINIMO' OR nivel_minimo_id   IS NOT NULL) AND
        (tipo_audiencia <> 'CURSO'        OR curso_id          IS NOT NULL) AND
        (tipo_audiencia <> 'CELULA'       OR celula_destino_id IS NOT NULL)
    )
);
CREATE INDEX eventos_agenda_idx  ON eventos (estado, inicia_en);
CREATE INDEX eventos_inicio_idx  ON eventos (inicia_en);
CREATE INDEX eventos_curso_idx   ON eventos (curso_id);
CREATE INDEX eventos_celula_idx  ON eventos (celula_destino_id);
CREATE INDEX eventos_nivel_idx   ON eventos (nivel_minimo_id);
CREATE INDEX eventos_creador_idx ON eventos (creado_por);

CREATE TABLE recurrencias_evento (
    -- 0..1 por evento: un evento simple no carga 10 columnas NULL de recurrencia
    evento_id    uuid                  PRIMARY KEY REFERENCES eventos (id) ON DELETE CASCADE,
    frecuencia   frecuencia_recurrencia NOT NULL,
    intervalo    smallint              NOT NULL DEFAULT 1 CHECK (intervalo >= 1),
    hasta        timestamptz,
    repeticiones smallint              CHECK (repeticiones > 0),
    CONSTRAINT fin_no_contradictorio CHECK (hasta IS NULL OR repeticiones IS NULL)
);

CREATE TABLE dias_semana_recurrencia (
    -- Antes: recurrence_by_weekday int[] (1FN/4FN)
    evento_id  uuid     NOT NULL REFERENCES recurrencias_evento (evento_id) ON DELETE CASCADE,
    dia_semana smallint NOT NULL CHECK (dia_semana BETWEEN 0 AND 6),  -- 0=domingo (convención actual)
    PRIMARY KEY (evento_id, dia_semana)
);

CREATE TABLE roles_destino_evento (
    -- Antes: target_roles UserRole[] (array de enum) — ahora referencia RBAC real
    evento_id uuid     NOT NULL REFERENCES eventos (id) ON DELETE CASCADE,
    rol_id    smallint NOT NULL REFERENCES roles (id)   ON DELETE CASCADE,
    PRIMARY KEY (evento_id, rol_id)
);
CREATE INDEX roles_destino_rol_idx ON roles_destino_evento (rol_id);  -- "qué eventos ve tal rol" (resolución de audiencia)

CREATE TABLE reglas_recordatorio_evento (
    -- Forma REAL verificada en reminders.ts: lista de {kind, value} con 3 clases de regla.
    -- minutesBefore/daysBefore llevan número; timeOfDay lleva hora ("HH:mm" → time).
    evento_id    uuid                    NOT NULL REFERENCES eventos (id) ON DELETE CASCADE,
    orden        smallint                NOT NULL CHECK (orden BETWEEN 1 AND 10),  -- MAX_REGLAS_POR_EVENTO del código viejo
    tipo_regla   tipo_regla_recordatorio NOT NULL,
    valor_numero integer                 CHECK (valor_numero > 0),
    valor_hora   time,
    PRIMARY KEY (evento_id, orden),
    CONSTRAINT valor_segun_tipo CHECK (
        (tipo_regla IN ('MINUTOS_ANTES','DIAS_ANTES') AND valor_numero IS NOT NULL AND valor_hora IS NULL)
     OR (tipo_regla = 'HORA_DEL_DIA' AND valor_hora IS NOT NULL AND valor_numero IS NULL)
    )
);

CREATE TABLE excepciones_evento (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id         uuid        NOT NULL REFERENCES eventos (id) ON DELETE CASCADE,
    inicio_ocurrencia timestamptz NOT NULL,
    cancelada         boolean     NOT NULL DEFAULT false,
    nuevo_inicio      timestamptz,
    nueva_duracion    integer     CHECK (nueva_duracion > 0),
    nuevo_titulo      text,
    creado_en         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (evento_id, inicio_ocurrencia)
);

CREATE TABLE confirmaciones_evento (
    evento_id         uuid                NOT NULL REFERENCES eventos (id) ON DELETE CASCADE,
    inicio_ocurrencia timestamptz         NOT NULL,
    usuario_id        uuid                NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    estado            estado_confirmacion NOT NULL,
    creado_en         timestamptz NOT NULL DEFAULT now(),
    actualizado_en    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (evento_id, inicio_ocurrencia, usuario_id)   -- antes surrogate + UNIQUE (P-28)
);
CREATE INDEX confirmaciones_usuario_idx ON confirmaciones_evento (usuario_id, inicio_ocurrencia);

CREATE TABLE recordatorios_evento (
    id                bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evento_id         uuid        NOT NULL REFERENCES eventos (id) ON DELETE CASCADE,
    inicio_ocurrencia timestamptz NOT NULL,
    usuario_id        uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    enviar_en         timestamptz NOT NULL,
    enviado_en        timestamptz,
    motivo_cancelacion text,
    creado_en         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (evento_id, inicio_ocurrencia, usuario_id, enviar_en)
);
CREATE INDEX recordatorios_cola_idx    ON recordatorios_evento (enviar_en) WHERE enviado_en IS NULL;  -- el cron de 5 min deja de escanear enviados
-- Consumo: FOR UPDATE SKIP LOCKED (misma regla que la cola de evidencias) — seguro con múltiples instancias.
CREATE INDEX recordatorios_usuario_idx ON recordatorios_evento (usuario_id, inicio_ocurrencia);

-- ============================================================================
-- CHAT
-- ============================================================================

CREATE TABLE conversaciones (
    id            uuid              PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo          tipo_conversacion NOT NULL,
    celula_id     uuid              UNIQUE REFERENCES celulas (id) ON DELETE CASCADE,
    clave_directa text              UNIQUE,   -- clave canónica del par en DMs (patrón actual)
    nombre        text,
    creado_en     timestamptz       NOT NULL DEFAULT now(),
    -- invariantes por tipo (P-34): antes nada impedía estados inválidos
    CONSTRAINT tipo_coherente CHECK (
        (tipo = 'CELULA'  AND celula_id IS NOT NULL AND clave_directa IS NULL)
     OR (tipo = 'DIRECTA' AND clave_directa IS NOT NULL AND celula_id IS NULL)
     OR (tipo = 'GLOBAL'  AND celula_id IS NULL AND clave_directa IS NULL)
    )
);
CREATE UNIQUE INDEX conversacion_global_unica_uk ON conversaciones ((tipo)) WHERE tipo = 'GLOBAL';  -- una sola GLOBAL + lookup O(1)
-- DECISIÓN 2026-08-24: todo usuario nuevo se agrega AUTOMÁTICAMENTE a la conversación GLOBAL
-- (comportamiento actual confirmado). Sin límite de miembros: el costo se controla con la
-- retención de mensajes (12 meses en la GLOBAL) y la paginación keyset, no limitando gente.

CREATE TABLE participantes_conversacion (
    conversacion_id uuid        NOT NULL REFERENCES conversaciones (id) ON DELETE CASCADE,
    usuario_id      uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    ultimo_leido_en timestamptz,
    creado_en       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (conversacion_id, usuario_id)
);
CREATE INDEX participantes_usuario_idx ON participantes_conversacion (usuario_id);

CREATE TABLE mensajes (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversacion_id   uuid         NOT NULL REFERENCES conversaciones (id) ON DELETE CASCADE,
    emisor_id         uuid         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,  -- CASCADE deliberado: purga legal de cuenta borra sus mensajes
    tipo              tipo_mensaje NOT NULL DEFAULT 'TEXTO',
    texto             text,
    media_bucket      text,
    media_ruta        text,
    media_mime        text,
    media_bytes       integer      CHECK (media_bytes > 0),
    media_duracion_s  smallint     CHECK (media_duracion_s > 0),
    oculto            boolean      NOT NULL DEFAULT false,   -- moderación
    eliminado_en      timestamptz,                            -- borrado por el emisor (tombstone)
    respuesta_a_id    uuid         REFERENCES mensajes (id) ON DELETE SET NULL,
    creado_en         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT mensaje_con_contenido CHECK (
        tipo = 'SISTEMA' OR texto IS NOT NULL OR media_ruta IS NOT NULL
    ),
    CONSTRAINT media_completa CHECK ((media_ruta IS NULL) = (media_bucket IS NULL))
);
CREATE INDEX mensajes_conversacion_idx ON mensajes (conversacion_id, creado_en);  -- keyset del chat (se conserva)
-- Retención (DECISIÓN 2026-08-24): chat GLOBAL se purga a los 12 meses (cron); células y
-- directos sin límite (volumen acotado). Los adjuntos del storage se purgan con su mensaje.
CREATE INDEX mensajes_respuesta_idx    ON mensajes (respuesta_a_id) WHERE respuesta_a_id IS NOT NULL;
CREATE INDEX mensajes_emisor_idx       ON mensajes (emisor_id);

CREATE TABLE mensajes_bienvenida (
    usuario_destinatario_id uuid PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,  -- PK natural: "el mensaje de bienvenida DE X"
    mensaje_id              uuid NOT NULL UNIQUE REFERENCES mensajes (id) ON DELETE CASCADE,
    creado_en               timestamptz NOT NULL DEFAULT now()
);

-- ============================================================================
-- NOTIFICACIONES
-- ============================================================================

CREATE TABLE tokens_push (
    id             uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id     uuid            NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token          text            NOT NULL UNIQUE,
    plataforma     plataforma_push,
    creado_en      timestamptz     NOT NULL DEFAULT now(),
    actualizado_en timestamptz     NOT NULL DEFAULT now()
);
CREATE INDEX tokens_push_usuario_idx ON tokens_push (usuario_id);

CREATE TABLE preferencias_notificacion (
    usuario_id     uuid              NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo           tipo_notificacion NOT NULL,
    habilitada     boolean           NOT NULL DEFAULT true,
    actualizado_en timestamptz       NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, tipo)   -- antes surrogate + UNIQUE (P-28)
);

CREATE TABLE notificaciones (
    id         bigint            GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- log de alto volumen
    usuario_id uuid              NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo       tipo_notificacion NOT NULL,
    titulo     text              NOT NULL,
    cuerpo     text              NOT NULL,
    ruta_app   text,
    leida_en   timestamptz,
    creado_en  timestamptz       NOT NULL DEFAULT now()
);
CREATE INDEX notificaciones_bandeja_idx ON notificaciones (usuario_id, creado_en DESC);
-- Retención (P-26): purga > 90 días por cron (la app ya corta a 90 en lectura).

-- ============================================================================
-- RENASIA / RAG
-- ============================================================================

CREATE TABLE conversaciones_renasia (
    usuario_id     uuid PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,  -- 1:1 real: PK=FK (antes surrogate + UNIQUE)
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE mensajes_renasia (
    id                 uuid                PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id         uuid                NOT NULL REFERENCES conversaciones_renasia (usuario_id) ON DELETE CASCADE,
    rol                rol_mensaje_renasia NOT NULL,
    contenido          text                NOT NULL,
    marcado_por_usuario boolean            NOT NULL DEFAULT false,
    nota_marca         text,
    anulado_por_admin  boolean             NOT NULL DEFAULT false,
    creado_en          timestamptz         NOT NULL DEFAULT now()
);
CREATE INDEX mensajes_renasia_conv_idx  ON mensajes_renasia (usuario_id, creado_en);
CREATE INDEX mensajes_renasia_flags_idx ON mensajes_renasia (marcado_por_usuario, anulado_por_admin) WHERE marcado_por_usuario;

CREATE TABLE fuentes_mensaje_renasia (
    -- Antes: source_lesson_ids text[] sin FK (P-20)
    mensaje_id uuid NOT NULL REFERENCES mensajes_renasia (id) ON DELETE CASCADE,
    leccion_id text NOT NULL REFERENCES lecciones (id) ON DELETE CASCADE ON UPDATE CASCADE,
    PRIMARY KEY (mensaje_id, leccion_id)
);
CREATE INDEX fuentes_renasia_leccion_idx ON fuentes_mensaje_renasia (leccion_id);

CREATE TABLE base_conocimiento (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_fuente  text        NOT NULL,
    clase        text,       -- promovida de metadata->>'kind' (P-35)
    documento_id text,       -- promovida de metadata->>'documentId'
    leccion_id   text        REFERENCES lecciones (id) ON DELETE SET NULL ON UPDATE CASCADE,  -- FK real cuando la fuente es una lección
    contenido    text        NOT NULL,
    embedding    vector(768) NOT NULL,
    metadatos    jsonb       NOT NULL DEFAULT '{}',   -- residual no consultado relacionalmente
    creado_en    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX base_conocimiento_fuente_idx ON base_conocimiento (tipo_fuente);
CREATE INDEX base_conocimiento_clase_idx  ON base_conocimiento (clase, documento_id);
CREATE INDEX base_conocimiento_leccion_idx ON base_conocimiento (leccion_id);
CREATE INDEX base_conocimiento_meta_gin   ON base_conocimiento USING gin (metadatos);
CREATE INDEX base_conocimiento_emb_hnsw   ON base_conocimiento USING hnsw (embedding vector_cosine_ops);  -- antes en SQL suelto fuera del schema

-- ============================================================================
-- SOPORTE
-- ============================================================================

CREATE TABLE tickets_mentor (
    id                    uuid                PRIMARY KEY DEFAULT gen_random_uuid(),
    participante_id    uuid                NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    descripcion_bloqueo   text                NOT NULL,
    soluciones_intentadas text                NOT NULL,
    impacto_meta_smart    text                NOT NULL,
    estado                estado_ticket_mentor NOT NULL DEFAULT 'ABIERTO',
    respuesta_mentor      text,
    respondido_en         timestamptz,
    guardado_en_biblioteca boolean            NOT NULL DEFAULT false,
    creado_en             timestamptz         NOT NULL DEFAULT now(),
    CONSTRAINT respondido_coherente CHECK (estado <> 'RESPONDIDO' OR respuesta_mentor IS NOT NULL)
);
CREATE INDEX tickets_mentor_perfil_idx ON tickets_mentor (participante_id);
CREATE INDEX tickets_mentor_estado_idx ON tickets_mentor (estado);
-- Búsqueda de la biblioteca (GET /tickets/library?q=) — full-text en español en vez de ILIKE:
CREATE INDEX tickets_mentor_biblioteca_fts ON tickets_mentor
    USING gin (to_tsvector('spanish', descripcion_bloqueo || ' ' || coalesce(respuesta_mentor, '')))
    WHERE guardado_en_biblioteca;

CREATE TABLE tickets_soporte (
    id             uuid                  PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id     uuid                  NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    categoria      categoria_soporte     NOT NULL,
    asunto         text                  NOT NULL,
    mensaje        text                  NOT NULL,
    log_cliente    text,
    adjunto_bucket text,
    adjunto_ruta   text,                 -- P-03
    estado         estado_ticket_soporte NOT NULL DEFAULT 'ABIERTO',
    notas_admin    text,
    resuelto_en    timestamptz,
    creado_en      timestamptz           NOT NULL DEFAULT now(),
    actualizado_en timestamptz           NOT NULL DEFAULT now()
);
CREATE INDEX tickets_soporte_cola_idx    ON tickets_soporte (estado, creado_en);
CREATE INDEX tickets_soporte_usuario_idx ON tickets_soporte (usuario_id, creado_en);
-- ============================================================================
-- SEGURIDAD POSTGRESQL — roles de conexión, privilegios, RLS (§9)
-- ============================================================================

-- Roles de carril (NOLOGIN, agrupan privilegios). Idempotente: CREATE ROLE es
-- global al clúster, por eso va protegido.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'renaser_migraciones') THEN
        CREATE ROLE renaser_migraciones NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'renaser_escritura') THEN
        CREATE ROLE renaser_escritura NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'renaser_lectura') THEN
        CREATE ROLE renaser_lectura NOLOGIN;
    END IF;
    -- La credencial LOGIN del backend se crea FUERA de este script (secreto por entorno):
    --   CREATE ROLE renaser_app LOGIN PASSWORD '...' IN ROLE renaser_escritura;
END $$;

-- Mínimo privilegio (patrón Supabase): cerrar el default permisivo y abrir por carril.
REVOKE ALL ON SCHEMA renaser FROM PUBLIC;

ALTER SCHEMA renaser OWNER TO renaser_migraciones;   -- solo migraciones hace DDL

GRANT USAGE ON SCHEMA renaser TO renaser_escritura, renaser_lectura;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA renaser TO renaser_escritura;
GRANT SELECT ON ALL TABLES IN SCHEMA renaser TO renaser_lectura;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA renaser TO renaser_escritura;

-- Las tablas que se creen en migraciones futuras heredan el esquema de permisos:
ALTER DEFAULT PRIVILEGES FOR ROLE renaser_migraciones IN SCHEMA renaser
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO renaser_escritura;
ALTER DEFAULT PRIVILEGES FOR ROLE renaser_migraciones IN SCHEMA renaser
    GRANT SELECT ON TABLES TO renaser_lectura;
ALTER DEFAULT PRIVILEGES FOR ROLE renaser_migraciones IN SCHEMA renaser
    GRANT USAGE ON SEQUENCES TO renaser_escritura;

-- ── RLS DE TRANSICIÓN ───────────────────────────────────────────────────────
-- SOLO mientras la app móvil siga escribiendo directo (P-07). Plantilla con el
-- patrón de rendimiento de Supabase: (SELECT auth.uid()) se evalúa UNA vez,
-- no por fila. Activar por tabla al migrar cada dominio; ejemplo:
--
--   ALTER TABLE registros_radar ENABLE ROW LEVEL SECURITY;
--   CREATE POLICY radar_propio ON registros_radar
--       FOR ALL TO authenticated
--       USING (participante_id = (SELECT auth.uid()))
--       WITH CHECK (participante_id = (SELECT auth.uid()));
--
-- En el estado objetivo (todo tráfico por el backend Java con renaser_app),
-- estas policies se retiran; renaser_escritura NO es superusuario pero tampoco
-- pasa por RLS de authenticated. RLS interna adicional: documentada en §9.2,
-- NO activada (decisión explícita).

-- ── TRIGGER OPCIONAL: coherencia rol ⇔ tabla de perfil ─────────────────────
-- [PENDIENTE-CONFIRMAR] §7.2. Desactivado por defecto: la garantía primaria es
-- la transacción del caso de uso. Descomentar para defensa en profundidad.
--
-- [SUPERADO — ver D-21, esto ademas referencia renaser.roles/rol_id que ya no es la fuente
-- de verdad del rol] Se deja como registro historico de la intencion (defensa en profundidad
-- rol<->perfil), a reescribir si/cuando se retome esta tabla de verificacion.
-- CREATE OR REPLACE FUNCTION renaser.verificar_rol_perfil() RETURNS trigger
-- LANGUAGE plpgsql AS $$
-- DECLARE clave_rol text;
-- BEGIN
--     SELECT r.clave INTO clave_rol FROM renaser.roles r
--       JOIN renaser.usuarios u ON u.rol_id = r.id WHERE u.id = NEW.usuario_id;
--     -- participantes_programa queda FUERA del trigger: cualquier rol puede cursar el programa (decisión 2026-08-24)
--     -- perfiles_alquimista/perfiles_admin YA NO EXISTEN (decisión 2026-08-24): bio/departamento
--     -- viven en usuarios, sin tabla propia que verificar acá. perfiles_lider_mentores tampoco
--     -- existe todavia (sus campos no estan confirmados, ver mas arriba).
--     IF (TG_TABLE_NAME = 'perfiles_mentor' AND clave_rol <> 'MENTOR') THEN
--         RAISE EXCEPTION 'El perfil % no corresponde al rol % del usuario', TG_TABLE_NAME, clave_rol;
--     END IF;
--     RETURN NEW;
-- END $$;

-- ============================================================================
-- SEEDS
-- ============================================================================

-- Los 5 roles del negocio (mapeo: ALCHEMIST→ALQUIMISTA, ADMIN→ADMIN,
-- MENTOR_LEAD→LIDER_MENTORES, MENTOR→MENTOR, TRAINEE→APRENDIZ)
INSERT INTO roles (clave, nombre, descripcion) VALUES
    ('ALQUIMISTA',     'Alquimista',        'Dueño del método; control total'),
    ('ADMIN',          'Administrador',     'Operación de la plataforma'),
    ('LIDER_MENTORES', 'Líder de Mentores', 'Coordina mentores — permisos [PENDIENTE-CONFIRMAR]'),
    ('MENTOR',         'Mentor',            'Acompaña a su célula de aprendices'),
    ('APRENDIZ',       'Aprendiz',          'Participante del programa de 90 días');

-- Permisos EVIDENCIADOS en el código/documentación actual. La matriz completa
-- rol×permiso es [PENDIENTE-CONFIRMAR] con negocio — acá solo lo comprobado.
INSERT INTO permisos (clave, recurso, accion, descripcion) VALUES
    ('solicitudes.aprobar',        'solicitudes_cuenta', 'aprobar',   'Aprobar solicitudes de cuenta'),
    ('solicitudes.rechazar',       'solicitudes_cuenta', 'rechazar',  'Rechazar solicitudes (libera el correo)'),
    ('usuarios.invitar',           'usuarios',           'invitar',   'Alta directa por invitación'),
    ('usuarios.cambiar_rol',       'usuarios',           'cambiar_rol','Cambiar el rol de un usuario'),
    ('usuarios.suspender',         'usuarios',           'suspender', 'Suspender/reactivar cuentas'),
    ('habitos.gestionar_catalogo', 'habitos',            'gestionar', 'Editar catálogo, horarios y guías'),
    ('evidencias.revisar',         'evidencias',         'revisar',   'Revisión manual / anular veredicto IA'),
    ('celulas.gestionar',          'celulas',            'gestionar', 'Crear células y asignar mentores'),
    ('cursos.gestionar',           'cursos',             'gestionar', 'Administrar cursos y asignaciones'),
    ('eventos.gestionar',          'eventos',            'gestionar', 'Crear/editar eventos del calendario'),
    ('soporte.gestionar',          'tickets_soporte',    'gestionar', 'Atender tickets de soporte');

-- Matriz mínima comprobada (ADMIN y ALQUIMISTA comparten la administración — docs/PERMISSIONS.md).
-- El resto de asignaciones NO se inventa: se completa al confirmar la matriz.
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permisos p
WHERE r.clave IN ('ALQUIMISTA', 'ADMIN');

-- Catálogo de categorías (mapeo del enum viejo: BODY→CUERPO, MIND→MENTE,
-- CONSCIENCE→CONSCIENCIA, SPIRIT→ESPIRITU — la 4ª entró 2026-08-04)
INSERT INTO categorias_habito (clave, nombre, orden) VALUES
    ('CUERPO', 'Cuerpo', 1), ('MENTE', 'Mente', 2),
    ('CONSCIENCIA', 'Emociones', 3), ('ESPIRITU', 'Espíritu', 4);

-- Catálogo de iconos (espeja HabitIcon actual / ICON_MAP de la app)
INSERT INTO iconos_habito (clave, nombre) VALUES
    ('WATER','Agua'), ('FAST_START','Inicio de ayuno'), ('FAST_END','Fin de ayuno'),
    ('RITUAL_MORNING','Ritual mañana'), ('RITUAL_MIDDAY','Ritual mediodía'), ('RITUAL_NIGHT','Ritual noche'),
    ('SLEEP','Dormir'), ('PHONE_OFF','Celular apagado'), ('SCREEN_LIMIT','Límite de pantalla'),
    ('PODCAST','Podcast'), ('COMMUNITY_POST','Post en comunidad'), ('JOURNALING','Journaling'),
    ('LEARNING','Aprendizaje'), ('READING','Lectura'), ('WORKOUT','Entrenamiento'),
    ('COLD_SHOWER','Ducha fría'), ('MEDITATION','Meditación'), ('WALKING','Caminata'),
    ('NUTRITION','Nutrición'), ('GRATITUDE','Gratitud');

-- ============================================================================
-- VERIFICACIÓN DEL INVARIANTE DE PUNTOS (P-06) — para dashboard/CI
-- ============================================================================

CREATE VIEW verificacion_puntos_liga AS
SELECT p.participante_id,
       p.puntos_liga                                   AS saldo_cacheado,
       100 + COALESCE(SUM(a.delta_aplicado), 0)        AS saldo_segun_ledger,
       p.puntos_liga - (100 + COALESCE(SUM(a.delta_aplicado), 0)) AS divergencia
FROM puntajes_participante p
LEFT JOIN ajustes_puntos_liga a ON a.participante_id = p.participante_id
GROUP BY p.participante_id, p.puntos_liga
HAVING p.puntos_liga <> 100 + COALESCE(SUM(a.delta_aplicado), 0);
COMMENT ON VIEW verificacion_puntos_liga IS 'Debe devolver 0 filas. Cada fila = un aprendiz cuyo saldo divergió de su ledger.';

GRANT SELECT ON verificacion_puntos_liga TO renaser_lectura;

COMMIT;

-- ============================================================================
-- FIN. Resumen: 93 tablas + 1 vista, 46 enums, 234 índices (283 sentencias validadas contra PG16+pgvector 2026-08-24), 3 carriles de conexión,
-- RLS de transición documentada, seeds de RBAC y catálogos.
-- Pendientes de negocio marcados [PENDIENTE-CONFIRMAR] (7).
-- ============================================================================
