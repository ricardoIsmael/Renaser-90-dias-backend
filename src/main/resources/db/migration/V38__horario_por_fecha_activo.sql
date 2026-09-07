-- El aprendiz puede APAGAR un habito para una fecha concreta, no solo cambiarle la hora.
--
-- QUE PROBLEMA RESUELVE. Hasta ahora apagar un habito era todo o nada: `desbloqueos_habito` guarda
-- una pausa que es un rango de fechas ("hasta el domingo"), y `horarios_habito.tipo_dia` es del
-- catalogo COMPARTIDO, que escribe el panel admin y afecta a todos los aprendices a la vez. No
-- habia forma de decir "el jueves no". Pedido del dueno (2026-09-07): el interruptor tiene que
-- estar al costado de cada habito y guardarse POR DIA.
--
-- POR QUE UNA COLUMNA Y NO UNA TABLA NUEVA. `horarios_habito_por_fecha` (V37) YA es "que hace este
-- aprendiz con este habito ESE dia": su PK es (participante, habito, fecha), que es exactamente la
-- identidad de la pregunta. Que el habito vaya o no ese dia es un ATRIBUTO de esa relacion, no una
-- relacion nueva -- el mismo razonamiento con el que V23 metio la pausa en `desbloqueos_habito` en
-- vez de crearle tabla. En 3FN da igual una tabla 1:1, pero una tabla nueva agregaria un JOIN y una
-- consulta en lote mas por participante en el barrido nocturno, que recorre TODO el padron; la
-- columna viaja en la consulta que el adaptador ya hace.
--
-- POR QUE `hora_disparo` PASA A SER NULABLE. V37 la hizo NOT NULL porque entonces una fila solo
-- podia significar "ese dia a otra hora", y sin hora no significaba nada. Ahora una fila puede
-- significar "ese dia apagado", y ahi la hora no aporta: obligar a repetirla congelaria el horario
-- de ese dia si despues cambia el general. La invariante no desaparece, se vuelve condicional y se
-- expresa en el CHECK de abajo: con `activo = true` la hora sigue siendo obligatoria.
--
-- SEMANTICA DE UNA FILA (la impone el dominio, ver `HorarioPorFecha`):
--   activo = true,  hora_disparo con valor -> ese dia, a esa hora.
--   activo = false, hora_disparo NULL      -> ese dia NO va; la hora se sigue heredando.
--   activo = false, hora_disparo con valor -> ese dia NO va, y ademas quedo anotada una hora.
--
-- DEFAULT true: las filas que V37 ya escribio significan "ese dia a otra hora", que es exactamente
-- `activo = true`. Ninguna fila existente cambia de significado al migrar.

SET search_path TO renaser, public;

ALTER TABLE horarios_habito_por_fecha
    ADD COLUMN activo boolean NOT NULL DEFAULT true;

ALTER TABLE horarios_habito_por_fecha
    ALTER COLUMN hora_disparo DROP NOT NULL;

ALTER TABLE horarios_habito_por_fecha
    ADD CONSTRAINT horarios_fecha_activo_exige_hora
    CHECK (NOT activo OR hora_disparo IS NOT NULL);

COMMENT ON COLUMN horarios_habito_por_fecha.activo IS
    'false = el aprendiz apago este habito ESE dia. Solo RESTA: nunca hace que un habito corra un '
    'dia en que el catalogo (horarios_habito.tipo_dia) no lo pone. Ver V38.';
COMMENT ON COLUMN horarios_habito_por_fecha.hora_disparo IS
    'Obligatoria cuando activo = true. NULL solo tiene sentido en una fila que apaga el dia sin '
    'tocarle la hora, que se sigue heredando de la preferencia general o del catalogo. Ver V38.';
