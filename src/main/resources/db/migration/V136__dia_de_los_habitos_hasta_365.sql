-- V136 — El día de inicio y de fin de un horario de hábito, y el día de desbloqueo, pueden llegar a 365 (spec 023; D-300)
--
-- Por qué se toca la base
-- -----------------------
-- La V128 (spec 022) dejó afuera estas columnas porque guardan días de CONTENIDO del catálogo, y dijo que las specs 023 y 024 decidirían cómo se reparten.
-- La 023 lo decide así:
--   · `horarios_habito.dia_inicio` / `dia_fin` de un hábito del CATÁLOGO están en la escala base de 90 días (el catálogo se escribe una vez y vale para el
--     Diplomado de 3 meses y para el de 1 año; el día de la persona se lleva a esa escala con `DuracionDelPrograma.enEscalaBase`). Ahí 90 sigue bastando.
--   · Pero un hábito PROPIO nace con `dia_inicio` = el día de la persona ese día (`MisHabitosService.crear`), y en un programa de 1 año ese día llega a 365:
--     con el CHECK en 90 el alta de un hábito propio falla a partir del día 91.
--   · `desbloqueos_habito.dia_desbloqueo` es el día de la persona en que entra un hábito a su plan: también llega a 365.
-- Las invariantes que dependen de la duración de CADA persona (hasta dónde llega SU programa) las impone el dominio; en la base el CHECK pasa al máximo
-- posible (regla 04). No cambia ninguna fila.
--
-- Qué NO se toca
-- --------------
-- `guias_habito.dia_inicio`: es contenido del catálogo en la escala base de 90 días, igual que los horarios del catálogo.

SET search_path TO renaser, public;

ALTER TABLE horarios_habito
    DROP CONSTRAINT horarios_habito_dia_inicio_check,
    ADD CONSTRAINT horarios_habito_dia_inicio_check CHECK (dia_inicio BETWEEN 1 AND 365);

ALTER TABLE desbloqueos_habito
    DROP CONSTRAINT desbloqueos_habito_dia_desbloqueo_check,
    ADD CONSTRAINT desbloqueos_habito_dia_desbloqueo_check CHECK (dia_desbloqueo BETWEEN 1 AND 365);
