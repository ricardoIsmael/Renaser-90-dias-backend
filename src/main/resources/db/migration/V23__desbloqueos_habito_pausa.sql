-- El interruptor ACTIVO/PAUSADO del aprendiz, que hasta hoy no guardaba nada (D-87).
--
-- Sintoma reportado por el dueno: "me preocupa que el activo y pausado debe funcionar
-- correctamente xq no funciona". Es exacto -- `PlanScreen.toggleHabitDayStatus` solo llamaba a
-- `setHabits(...)`: estado local de React. Apagabas un habito, cerrabas la app, y volvia
-- encendido.
--
-- La causa de fondo (ya documentada en docs/informes/habits-eleccion-y-personales.md §0): NO
-- EXISTIA un flag "activo para MI". `habitos.activo` es del catalogo COMPARTIDO y solo lo
-- escribe el panel admin (`HabitoAdminService`, gateado a ADMIN/ALCHEMIST) -- un aprendiz que
-- lo llamara recibiria 403. O sea: no habia agujero de seguridad, pero tampoco endpoint al que
-- ese boton pudiera llamar.
--
-- Por que una COLUMNA en `desbloqueos_habito` y no una tabla nueva: esa tabla YA es "que
-- habitos lleva este aprendiz en su plan" (PK compuesta participante_id + habito_id, con
-- `elegido_en` = lo eligio el, no el relleno automatico). Pausar no es una relacion nueva, es
-- un atributo de una relacion que ya existe. Crear `habitos_aprendiz` al lado hubiera dejado
-- dos tablas respondiendo la misma pregunta.
--
-- `pausado_en timestamptz` y no un boolean `pausado`: guarda ADEMAS cuando se pauso, que es
-- gratis y sirve para responder "¿desde cuando no lo hace?" sin una tabla de historial. NULL =
-- activo, que es el default correcto para las filas que ya existen.
--
-- Los habitos OBLIGATORIOS (`habitos.desactivable = false`, V18) no se pueden pausar. Eso NO se
-- impone con un CHECK: la invariante cruza dos tablas (`desbloqueos_habito` y `habitos`), y un
-- CHECK solo ve su propia fila. Lo impone el dominio (`DesbloqueoHabito.pausar`), que es donde
-- CLAUDE.MD manda ponerlo cuando la regla depende de otra tabla.
--
-- Cada migracion corre en su propia conexion (mismo preambulo que V13, V18, V20-V22).
SET search_path TO renaser, public;

ALTER TABLE desbloqueos_habito
    ADD COLUMN pausado_en timestamptz;

COMMENT ON COLUMN desbloqueos_habito.pausado_en IS
    'NULL = el habito esta ACTIVO para este aprendiz. Con valor = pausado, y cuando. Ver V23 y D-87.';

-- El barrido nocturno pregunta "que habitos activos tiene este participante" para generar los
-- tracks del dia: parcial sobre los activos, que son la enorme mayoria.
CREATE INDEX desbloqueos_activos_idx
    ON desbloqueos_habito (participante_id) WHERE pausado_en IS NULL;
