-- El aprendiz puede apagar un habito para UN dia de la semana, todas las semanas.
--
-- QUE PROBLEMA RESUELVE. Pedido del dueno (2026-09-07), el mismo dia que V39: "si un dia no lo
-- quiere hacer deberia haber la opcion para inactivarlo ese dia". Hasta ahora apagar era por FECHA
-- (`horarios_habito_por_fecha.activo`, V38), que sirve para "este martes no" y no se repite: la
-- semana siguiente el habito vuelve.
--
-- POR QUE V39 NO LO TRAIA. Su cabecera lo justifico asi: "apagar un dia YA se puede, por fecha,
-- desde V38; dos mecanismos para lo mismo serian dos respuestas posibles a '¿va hoy?'". **Ese
-- razonamiento confundia dos preguntas distintas.** "Este martes no" y "los martes nunca" no son
-- el mismo pedido, y ninguna de las dos tablas puede expresar la otra: una tiene `fecha` en la
-- clave y no se repite; la otra tiene `dia_semana` y se repite siempre. No hay duplicacion.
--
-- LA OBJECION DE V31 SIGUE EN PIE, y por eso se acota. V31 rechazo los patrones semanales para la
-- PAUSA con el argumento de que "crean un agujero PERMANENTE y silencioso en un programa de 90
-- dias". Para la hora ese argumento no aplicaba -- cambiar a que hora hacés algo no te saltea nada
-- --, pero para apagar aplica entero. Se acota igual que la pausa: un habito con
-- `habitos.desactivable = false` (V18) no acepta `activo = false`. Esa invariante cruza dos tablas,
-- asi que vive en el dominio y NO puede ser un CHECK.
--
-- POR QUE `hora_disparo` PASA A SER NULABLE, igual que en V38: una fila que solo apaga el dia no
-- trae hora, y obligarla a repetirla congelaria el horario de ese dia si despues cambia el general.
-- La invariante no desaparece, se vuelve condicional: con `activo = true` la hora sigue siendo
-- obligatoria.
--
-- SEMANTICA DE UNA FILA (la impone el dominio, ver `HorarioSemanal`):
--   activo = true,  hora_disparo con valor -> ese dia, a esa hora, todas las semanas.
--   activo = false, hora_disparo NULL      -> ese dia NO va, ninguna semana.
--   activo = true,  hora_disparo NULL      -> imposible, lo corta el CHECK.
--
-- DEFAULT true: las filas que V39 escribio significan "ese dia a esta hora", que es `activo =
-- true`. Ninguna cambia de significado al migrar.
--
-- PRECEDENCIA de "¿va hoy?", de mas especifico a mas general:
--   `horarios_habito_por_fecha.activo` de ESA fecha  >  `horario_semanal_habito.activo` de ESE dia
--   de semana  >  la pausa por rango de `desbloqueos_habito`  >  el catalogo.

SET search_path TO renaser, public;

ALTER TABLE horario_semanal_habito
    ADD COLUMN activo boolean NOT NULL DEFAULT true;

ALTER TABLE horario_semanal_habito
    ALTER COLUMN hora_disparo DROP NOT NULL;

ALTER TABLE horario_semanal_habito
    ADD CONSTRAINT horario_semanal_activo_exige_hora
    CHECK (NOT activo OR hora_disparo IS NOT NULL);

COMMENT ON COLUMN horario_semanal_habito.activo IS
    'false = el aprendiz apago este habito ESE dia de la semana, todas las semanas. Solo RESTA '
    'dias del conjunto que habilita el catalogo (horarios_habito.tipo_dia); nunca agrega. Ver V40.';
