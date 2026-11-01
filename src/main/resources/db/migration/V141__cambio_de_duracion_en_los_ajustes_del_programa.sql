-- V141 — Cambiar la duración del programa de quien ya empezó queda en la misma bitácora que mover su día (spec 025; D-302)
--
-- Por qué se toca la base
-- -----------------------
-- Decisión del dueño (31/10, 025-2 A): Administración puede cambiar la duración del programa de una persona que ya empezó (por ejemplo de 3 meses a 1 año en el
-- Diplomado) escribiendo un motivo; la persona conserva su día y se mueve el último. Eso es una corrección de su reloj hecha por alguien, con un motivo, y tiene
-- que dejar rastro de quién, cuándo y de cuánto a cuánto.
--
-- Por qué no alcanza el modelo actual / qué se reutiliza
-- ------------------------------------------------------
-- `ajustes_dia_programa` (V21) ya es la bitácora append-only de las correcciones manuales del reloj de una persona (quién, cuándo, por qué, día antes y
-- después). Un cambio de duración es otra corrección de ese mismo reloj, así que se anota ahí: no nace una tabla nueva. Solo faltan dos columnas, nulas en
-- los ajustes de día de siempre (el día no cambia en un cambio de duración: `dia_anterior = dia_nuevo`).
--
-- El día no se toca: el CHECK `participantes_programa_dia_dentro_de_la_duracion` (V125) exige `dia_programa <= duracion_dias`, y el dominio no deja bajar la
-- duración por debajo del día en que va la persona.

SET search_path TO renaser, public;

ALTER TABLE ajustes_dia_programa
    ADD COLUMN duracion_anterior smallint CHECK (duracion_anterior BETWEEN 1 AND 365),
    ADD COLUMN duracion_nueva    smallint CHECK (duracion_nueva BETWEEN 1 AND 365),
    ADD CONSTRAINT ajustes_dia_programa_duracion_completa
        CHECK ((duracion_anterior IS NULL) = (duracion_nueva IS NULL));

COMMENT ON COLUMN ajustes_dia_programa.duracion_anterior IS
    'Duración del programa antes del ajuste (V141, spec 025). NULL en los ajustes que solo movieron el día.';
COMMENT ON COLUMN ajustes_dia_programa.duracion_nueva IS
    'Duración del programa después del ajuste (V141, spec 025). NULL en los ajustes que solo movieron el día.';
