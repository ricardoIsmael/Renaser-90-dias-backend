-- V143 — Qué ficha completa cada persona: la estándar o la «con diagnóstico» (spec 026; D-303)
--
-- Por qué se toca la base
-- -----------------------
-- Con la spec 026 hay dos fichas de ingreso: la estándar (la de siempre) y el diagnóstico. Administración decide cuál le toca a cada persona al aceptar su
-- solicitud (spec 025) o, después, a quien ya está dentro (026-9). Esa decisión tiene que vivir en algún lado, y también quién la tomó y cuándo, y con qué
-- versión publicada del diagnóstico empezó la persona (FR-031: quien empezó una ficha la termina con la versión con la que empezó).
--
-- Por qué no una tabla nueva
-- --------------------------
-- `estado_onboarding` ya es UNA fila por persona (PK `usuario_id`), con el estado de su ingreso. La marca «qué ficha me toca» es otro dato de ese mismo estado, así que
-- son cuatro columnas aquí y no una tabla `fichas_de_personas` (decisión del dueño de no crear tablas innecesarias). Que el diagnóstico se terminó se marca,
-- como el Mapa, en `etapas_onboarding_completadas` (V41, una fila por flujo), y que se empezó se deduce de que existan respuestas del flujo `diagnostico`.
--
-- Por qué estos nombres
-- ---------------------
-- `ficha` es el tipo (`ESTANDAR` por defecto, que es lo que tienen todas las personas que existen hoy: nada cambia para ellas). `ficha_version` queda en NULL hasta
-- que la persona abre el diagnóstico por primera vez; entonces se fija a la versión vigente y ya no cambia.

SET search_path TO renaser, public;

ALTER TABLE estado_onboarding
    ADD COLUMN ficha              text        NOT NULL DEFAULT 'ESTANDAR' CHECK (ficha IN ('ESTANDAR', 'CON_DIAGNOSTICO')),
    ADD COLUMN ficha_asignada_por uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    ADD COLUMN ficha_asignada_en  timestamptz,
    ADD COLUMN ficha_version      integer     CHECK (ficha_version IS NULL OR ficha_version > 0);

COMMENT ON COLUMN estado_onboarding.ficha IS
    'ESTANDAR (la ficha de siempre) o CON_DIAGNOSTICO (spec 026). Lo decide Administración al aceptar la solicitud o después (026-9).';
COMMENT ON COLUMN estado_onboarding.ficha_version IS
    'Versión publicada del diagnóstico con la que la persona empezó; NULL hasta que lo abre. No cambia (FR-031).';
