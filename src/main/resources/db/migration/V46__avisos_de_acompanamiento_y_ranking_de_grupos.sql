-- ============================================================================
-- V46 — Avisos de acompañamiento y ranking mensual entre grupos
-- ============================================================================
-- POR QUÉ CADA COSA (plan.md §3 exige justificar que no se pueda reutilizar otra):
--
-- · `tipo_notificacion` gana un valor en vez de una tabla de alertas propia. El SDD es
--   explícito: "no tablas paralelas de alertas por feature". La bandeja, las preferencias, la
--   deduplicación por `origen_evento_id` y la purga a 90 días ya existen y se reutilizan tal
--   cual.
--
-- · `ranking_celulas` se AMPLÍA, no se reemplaza. La tabla existe desde el baseline y hoy no la
--   escribe nadie: su fórmula nunca se decidió (lo dice el javadoc de
--   ConsultarRankingAgregadoUseCase). Este SDD la decide, así que se le agregan los metadatos
--   que hacen auditable un snapshot —con qué fórmula, sobre cuánta muestra y hasta qué corte—
--   en vez de crear una segunda tabla de ranking que diría lo mismo con otro nombre.
--
-- Migración ADITIVA. No borra ni reescribe ninguna fila.
-- ============================================================================

-- Un solo valor nuevo. ADD VALUE no puede USARSE en la misma transacción que lo crea, y no se
-- usa: acá solo se declara (mismo patrón que V42 con plataforma_push.WEB).
ALTER TYPE renaser.tipo_notificacion ADD VALUE IF NOT EXISTS 'ACOMPANAMIENTO_ALUMNO';

SET search_path TO renaser, public;

-- ── Snapshot mensual del ranking entre grupos ───────────────────────────────
ALTER TABLE ranking_celulas
    ADD COLUMN cohorte_id      uuid        REFERENCES cohortes (id) ON DELETE CASCADE,
    ADD COLUMN version_formula text,
    -- Alumnos que efectivamente entraron al promedio. Un grupo de 10 con 2 evaluables no
    -- compara igual que uno con 10, y ocultarlo haría parecer comparables dos números que no lo son.
    ADD COLUMN muestra         smallint    CHECK (muestra IS NULL OR muestra >= 0),
    ADD COLUMN entregadas      integer     CHECK (entregadas IS NULL OR entregadas >= 0),
    ADD COLUMN esperadas       integer     CHECK (esperadas IS NULL OR esperadas >= 0),
    ADD COLUMN corte_en        timestamptz;

COMMENT ON COLUMN ranking_celulas.cohorte_id IS
    'Se compara DENTRO de la cohorte (P-08). Desnormalizado desde celulas para no unir en cada lectura del ranking.';
COMMENT ON COLUMN ranking_celulas.version_formula IS
    'Con qué reglas se calculó. Una corrección futura recalcula explícitamente en vez de reescribir historia en silencio.';
COMMENT ON COLUMN ranking_celulas.muestra IS
    'Alumnos con obligaciones vencidas en el período. 0 = grupo sin muestra: aparece en la lista, sin calificación.';

-- Lectura del ranking de un mes dentro de una cohorte, ya ordenada.
CREATE INDEX ranking_cel_cohorte_idx ON ranking_celulas (cohorte_id, fecha, posicion);

-- Empates: `posicion` deja de ser única por fecha a propósito. Dos grupos con el mismo
-- porcentaje comparten posición (P-08), así que la PK sigue siendo (fecha, celula_id) y nada
-- impide que dos filas tengan posicion = 3. Ya era así; se deja escrito para que nadie
-- "arregle" eso agregando una restricción de unicidad.
