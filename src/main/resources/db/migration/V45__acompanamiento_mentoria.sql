-- ============================================================================
-- V45 — Acompañamiento: historial temporal de asignaciones y política por cohorte
-- ============================================================================
-- POR QUÉ CADA COSA NUEVA NO PUEDE REUTILIZAR OTRA (exigido por plan.md §3):
--
-- · `asignaciones_celula` es tabla nueva porque `celulas.mentor_id` y
--   `participantes_programa.mentor_id` son PUNTEROS AL PRESENTE. De un puntero no se
--   deduce quién acompañaba en agosto, y la evaluación mensual necesita exactamente eso.
--   No es una tabla de usuarios ni de roles globales: es quién estuvo, en qué grupo, con
--   qué función y entre qué fechas. Los punteros se conservan como proyección.
--
-- · `politicas_mentoria` es tabla nueva y no columnas en `cohortes` porque son parámetros
--   de operación (cupo, cadencia, zona, umbral de aviso) con control de versión para
--   edición concurrente; `cohortes` describe el calendario del programa, no su operación.
--   Se mantiene 1:1 con la cohorte, así que no hay tabla genérica de configuración.
--
-- · `celulas.tipo` es columna y no tabla aparte porque recepción y grupo estable SON la
--   misma entidad con distinta política: comparten conversación, miembros y traslado.
--   Separarlas obligaría a migrar de tabla en el día 4 y a duplicar el chat.
--
-- · `anomalias_acompanamiento` existe porque la reconciliación no puede sobrescribir en
--   silencio datos que se contradicen. Se registra y queda visible.
--
-- Migración ADITIVA: no borra ni reescribe ninguna fila existente.
-- ============================================================================

-- btree_gist habilita EXCLUDE sobre (uuid =, rango &&). Es la única forma de que la base
-- —y no un check-then-insert que pierde carreras— garantice que dos mentores no se solapen.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- La extension queda en public, como pgcrypto y vector (V1:25-26); el resto de la migracion
-- trabaja en renaser. public al final para que los operadores gist se resuelvan.
SET search_path TO renaser, public;

CREATE TYPE tipo_celula            AS ENUM ('RECEPCION','REGULAR');
CREATE TYPE cadencia_rotacion      AS ENUM ('MENSUAL','SEMANAL');
CREATE TYPE funcion_acompanamiento AS ENUM ('APRENDIZ','MENTOR','GUIA','SOPORTE');
CREATE TYPE motivo_asignacion      AS ENUM ('ADMINISTRATIVO','RECEPCION','TRASLADO','ROTACION','MIGRACION');

-- ── Célula: tipo y override de capacidad ────────────────────────────────────
ALTER TABLE celulas
    ADD COLUMN tipo             tipo_celula NOT NULL DEFAULT 'REGULAR',
    ADD COLUMN capacidad_maxima smallint    CHECK (capacidad_maxima BETWEEN 10 AND 15);

COMMENT ON COLUMN celulas.tipo IS
    'RECEPCION no tiene tope comercial (D-05) y puede no tener mentor. REGULAR usa el cupo de la política.';
COMMENT ON COLUMN celulas.capacidad_maxima IS
    'Override por célula. NULL = usar politicas_mentoria.capacidad_celula de su cohorte.';

-- La unicidad de mentor regular que ya existía (celulas.mentor_id UNIQUE) se conserva tal cual.
-- Una célula de recepción con mentor_id NULL no la viola: UNIQUE admite varios NULL.

-- ── Política de mentoría por cohorte ────────────────────────────────────────
CREATE TABLE politicas_mentoria (
    cohorte_id                uuid              PRIMARY KEY REFERENCES cohortes (id) ON DELETE CASCADE,
    capacidad_celula          smallint          NOT NULL DEFAULT 10 CHECK (capacidad_celula BETWEEN 10 AND 15),
    cadencia_rotacion         cadencia_rotacion NOT NULL DEFAULT 'MENSUAL',
    zona_horaria              text              NOT NULL DEFAULT 'America/Lima',
    -- P-01: recepción cubre los días 1..(dia_traslado-1) del programa del participante.
    dia_traslado              smallint          NOT NULL DEFAULT 4  CHECK (dia_traslado BETWEEN 2 AND 15),
    -- P-06: días locales completos sin actividad antes de avisar al mentor.
    dias_sin_actividad_alerta smallint          NOT NULL DEFAULT 3  CHECK (dias_sin_actividad_alerta BETWEEN 1 AND 30),
    celula_recepcion_id       uuid              REFERENCES celulas (id) ON DELETE SET NULL,
    -- Control de versión optimista: dos administradores editando a la vez no se pisan.
    version                   integer           NOT NULL DEFAULT 1 CHECK (version > 0),
    creado_en                 timestamptz       NOT NULL DEFAULT now(),
    actualizado_en            timestamptz       NOT NULL DEFAULT now()
);
COMMENT ON TABLE politicas_mentoria IS
    'Parámetros operativos por cohorte. Los defaults son las propuestas P-01/P-02/P-06 adoptadas como configuración, no como constantes de código: ajustarlas es un UPDATE, no un despliegue.';

-- ── Guías y soporte de la recepción ─────────────────────────────────────────
-- No es una tabla de roles: apunta a usuarios que YA existen y les da una función con
-- fechas. Por eso vive en asignaciones_celula, no acá; esta tabla solo guarda el reemplazo
-- atómico de la lista configurada por el administrador.

-- ── Asignaciones temporales ─────────────────────────────────────────────────
CREATE TABLE asignaciones_celula (
    id              uuid                   PRIMARY KEY DEFAULT gen_random_uuid(),
    celula_id       uuid                   NOT NULL REFERENCES celulas (id)  ON DELETE CASCADE,
    usuario_id      uuid                   NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    funcion         funcion_acompanamiento NOT NULL,
    -- Intervalo semiabierto [inicio, fin). fin NULL = vigente.
    inicio          timestamptz            NOT NULL,
    fin             timestamptz,
    motivo          motivo_asignacion      NOT NULL,
    -- NULL = lo ejecutó un job. No se inventa un usuario técnico para llenar la columna.
    actor_id        uuid                   REFERENCES usuarios (id) ON DELETE SET NULL,
    clave_operacion text                   NOT NULL CHECK (length(clave_operacion) BETWEEN 1 AND 200),
    creado_en       timestamptz            NOT NULL DEFAULT now(),
    CHECK (fin IS NULL OR fin > inicio)
);

-- Repetir el mismo comando no abre otro intervalo. La clave es por fila afectada porque una
-- rotación A→B toca varias filas bajo una sola operación.
CREATE UNIQUE INDEX asignaciones_celula_operacion_uk
    ON asignaciones_celula (clave_operacion, celula_id, usuario_id, funcion);

-- Las invariantes que el dominio comprueba antes, repetidas acá porque un check-then-insert
-- pierde la carrera contra otra transacción (plan.md §3).
ALTER TABLE asignaciones_celula
    ADD CONSTRAINT asignaciones_un_mentor_por_celula
        EXCLUDE USING gist (celula_id WITH =, tstzrange(inicio, fin) WITH &&)
        WHERE (funcion = 'MENTOR'),
    ADD CONSTRAINT asignaciones_una_celula_por_mentor
        EXCLUDE USING gist (usuario_id WITH =, tstzrange(inicio, fin) WITH &&)
        WHERE (funcion = 'MENTOR'),
    ADD CONSTRAINT asignaciones_un_grupo_por_aprendiz
        EXCLUDE USING gist (usuario_id WITH =, tstzrange(inicio, fin) WITH &&)
        WHERE (funcion = 'APRENDIZ');
-- tstzrange(inicio, fin) es '[)' por defecto: el relevo exacto (A cierra donde B abre) NO
-- solapa, que es justo lo que el dominio modela en PeriodoAsignacion.

-- Guía y soporte no llevan exclusión: pueden cubrir varios grupos a la vez y ser varios por
-- grupo (plan.md §3). El índice único de operación ya evita duplicarlos por repetición.

-- Todos los indices llevan el nombre completo de la tabla: `asignaciones_usuario_idx` a secas
-- ya esta tomado por asignaciones_curso (V1:1042) y los nombres de indice son unicos por esquema.
CREATE INDEX asignaciones_celula_funcion_idx ON asignaciones_celula (celula_id, funcion, inicio DESC);
CREATE INDEX asignaciones_celula_usuario_idx        ON asignaciones_celula (usuario_id, inicio DESC);
CREATE INDEX asignaciones_celula_vigentes_idx       ON asignaciones_celula (celula_id, funcion) WHERE fin IS NULL;
CREATE INDEX asignaciones_celula_rango_idx          ON asignaciones_celula USING gist (tstzrange(inicio, fin));

COMMENT ON TABLE asignaciones_celula IS
    'Historial de acompañamiento. Fuente de verdad de quién estuvo con quién y cuándo; celulas.mentor_id y participantes_programa.mentor_id quedan como proyección del intervalo vigente.';

-- ── Reconciliación visible ──────────────────────────────────────────────────
CREATE TABLE anomalias_acompanamiento (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    detectado_en timestamptz NOT NULL DEFAULT now(),
    origen       text        NOT NULL,
    clase        text        NOT NULL,
    celula_id    uuid,
    usuario_id   uuid,
    detalle      text        NOT NULL
);
CREATE INDEX anomalias_acompanamiento_clase_idx ON anomalias_acompanamiento (clase, detectado_en DESC);
COMMENT ON TABLE anomalias_acompanamiento IS
    'Contradicciones encontradas al reconciliar punteros. Se registran en vez de resolverse en silencio: quien opera decide, no la migración.';

-- ============================================================================
-- BACKFILL — el historial ARRANCA acá. No se deduce el pasado.
-- ============================================================================
-- `mentor_id` no dice desde cuándo. Fabricar un inicio a partir de `actualizado_en` sería
-- inventar historia, y `actualizado_en` también cambia al renombrar la célula. Por eso todo
-- intervalo migrado empieza en el instante de esta migración, con motivo MIGRACION, y los
-- meses anteriores quedan SIN_HISTORIAL (clarifications.md, dependencias de puesta en marcha).

INSERT INTO politicas_mentoria (cohorte_id)
SELECT id FROM cohortes
ON CONFLICT (cohorte_id) DO NOTHING;

-- Mentores vigentes: un intervalo abierto por célula que hoy tiene mentor.
INSERT INTO asignaciones_celula (celula_id, usuario_id, funcion, inicio, motivo, actor_id, clave_operacion)
SELECT c.id, c.mentor_id, 'MENTOR', now(), 'MIGRACION', NULL,
       'v45-mentor:' || c.id::text
FROM celulas c
WHERE c.mentor_id IS NOT NULL;

-- Aprendices vigentes, EXCEPTO quien mentorea esa misma célula: un mentor que además cursa
-- no es alumno de su propio grupo ni entra en sus KPI (plan.md §3).
INSERT INTO asignaciones_celula (celula_id, usuario_id, funcion, inicio, motivo, actor_id, clave_operacion)
SELECT p.celula_id, p.usuario_id, 'APRENDIZ', now(), 'MIGRACION', NULL,
       'v45-aprendiz:' || p.usuario_id::text
FROM participantes_programa p
JOIN celulas c ON c.id = p.celula_id
WHERE p.celula_id IS NOT NULL
  AND (c.mentor_id IS NULL OR c.mentor_id <> p.usuario_id);

-- Anomalía 1: el participante apunta a un mentor distinto del de su célula. Los dos punteros
-- se contradicen; no se toca ninguno.
INSERT INTO anomalias_acompanamiento (origen, clase, celula_id, usuario_id, detalle)
SELECT 'V45', 'PUNTERO_MENTOR_INCOHERENTE', p.celula_id, p.usuario_id,
       'participantes_programa.mentor_id=' || COALESCE(p.mentor_id::text, 'NULL')
       || ' pero celulas.mentor_id=' || COALESCE(c.mentor_id::text, 'NULL')
FROM participantes_programa p
JOIN celulas c ON c.id = p.celula_id
WHERE p.celula_id IS NOT NULL
  AND p.mentor_id IS DISTINCT FROM c.mentor_id;

-- Anomalía 2: alguien mentorea la célula en la que figura como participante.
INSERT INTO anomalias_acompanamiento (origen, clase, celula_id, usuario_id, detalle)
SELECT 'V45', 'MENTOR_PARTICIPANTE_DE_SU_CELULA', c.id, c.mentor_id,
       'No se creó asignación APRENDIZ: su participación personal es independiente de su célula'
FROM celulas c
JOIN participantes_programa p ON p.usuario_id = c.mentor_id AND p.celula_id = c.id
WHERE c.mentor_id IS NOT NULL;

-- Anomalía 3: participante con mentor_id pero sin célula. El puntero no alcanza para saber
-- a qué grupo pertenecía.
INSERT INTO anomalias_acompanamiento (origen, clase, celula_id, usuario_id, detalle)
SELECT 'V45', 'MENTOR_SIN_CELULA', NULL, p.usuario_id,
       'participantes_programa.mentor_id=' || p.mentor_id::text || ' con celula_id NULL'
FROM participantes_programa p
WHERE p.celula_id IS NULL AND p.mentor_id IS NOT NULL;
