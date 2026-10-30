-- Mi Radar: desde el dia 8 la persona enciende el Codigo Renaser por su cuenta y elige a que horas se lo preguntan.
-- Spec 012, decision D-286.
--
-- QUE PROBLEMA RESUELVE. Hasta hoy el Codigo Renaser (`registros_radar`) es de los dias 1 a 7: doce preguntas fijas por
-- dia, de 08:00 a 19:00, que la app abre sola. El dia 8 se apaga y no hay manera de seguir. Pedido del dueno
-- (2026-10-29): "la persona puede activarlo individualmente y que le aparezca". Para eso el servidor tiene que saber
-- dos cosas nuevas, y las dos son datos de la cuenta y no del telefono (el mentor, el lider y Administracion las leen,
-- y la persona entra desde otro telefono o desde la web):
--   1. SI Mi Radar esta encendido y A QUE HORAS pregunta: unas horas para todos los dias y, por dia de la semana,
--      horas propias o "Sin Radar ese dia" (pedido del dueno, el mismo 2026-10-29, despues de ver las capturas: "no
--      haran la accion todos los dias... igual que los habitos, pueden ser diferentes").
--   2. A QUE HORA PREVISTA respondia cada registro, para poder decir "prevista 14:00 - respondida 14:06" y para que
--      "sin registro" signifique "una hora elegida que no se respondio" y no "una hora cualquiera".
--
-- POR QUE NO ALCANZA EL MODELO ACTUAL. `registros_radar` solo guarda el instante real (`creado_en`) y las respuestas:
-- no dice a que hora estaba prevista la respuesta. Deducirla despues, mirando las horas elegidas AHORA, daria resultados
-- falsos en cuanto la persona cambie sus horas (las horas elegidas cambian con el tiempo y lo ya registrado no debe
-- moverse: constitucion IV). Y `participantes_programa` no tiene ningun lugar para las horas.
--
-- POR QUE COLUMNAS Y NO UNA TABLA NUEVA. Pedido expreso del dueno: "no crear una nueva tabla sino reutilizar".
-- `participantes_programa` ya es UNA FILA POR PERSONA y ya tiene su zona horaria (`timezone`) y su dia del programa,
-- que es todo lo que hace falta para decidir si una hora elegida corresponde; `registros_radar` ya cuelga de esa fila
-- (`participante_id`). Una tabla `mi_radar` repetiria la clave de la persona para guardar una sola lista de numeros.
--
-- POR QUE `dias_mi_radar` ES jsonb Y NO OTRA TABLA (una fila por persona y dia). Son a lo sumo SIETE elementos
-- ({dia 1..7, horas}) que siempre se leen y se escriben juntos, por la persona y para toda la persona: nadie consulta
-- "quienes tienen Radar los sabados" ni une estos dias con otra tabla. Una tabla hija `mi_radar_dia` traeria su clave,
-- su FK, su indice y siete filas por persona para guardar lo mismo, y el dueno pidio no crear tablas. El precio es que
-- la base no valida el contenido del arreglo (que cada dia este entre 1 y 7, sin repetir, con horas validas): lo impone
-- el dominio (`MiRadar`) y la base solo asegura que sea un arreglo. Si algun dia hay que consultar por dia de la
-- semana, ese dia se normaliza a una tabla; hoy seria sobreingenieria.
--
--   participantes_programa.horas_mi_radar  smallint[]  Las horas de TODOS los dias. NULL = Mi Radar APAGADO. Si no es NULL:
--                                                       de 1 a 12 horas en punto, cada una entre 3 y 23 (03:00 a 23:00 del
--                                                       mismo dia: nada despues de la medianoche). NULL y no un arreglo
--                                                       vacio: un arreglo vacio no seria "encendido sin horas" ni "apagado"
--                                                       con claridad, y el CHECK de abajo lo rechaza. Apagar guarda NULL,
--                                                       asi que apagado no deja horas viejas esperando.
--   participantes_programa.dias_mi_radar   jsonb       Los dias de la semana con horas PROPIAS:
--                                                       [{"dia": 6, "horas": [11]}, {"dia": 7, "horas": []}], con lunes = 1
--                                                       ... domingo = 7. Un dia con "horas": [] es "Sin Radar ese dia". Un
--                                                       dia que no esta sigue `horas_mi_radar`. NULL = ningun dia propio
--                                                       (todos siguen las de todos los dias). Solo existe con Mi Radar
--                                                       encendido: apagar tambien lo deja en NULL.
--   registros_radar.hora_prevista          smallint    NULL = el registro no respondia a una hora prevista (un registro
--                                                       libre, o uno anterior a esta migracion). Si no, la hora local
--                                                       (0-23) de la hora prevista, que calcula el servidor al
--                                                       registrar con las horas vigentes en ese momento y el dia de la
--                                                       semana LOCAL de la persona.
--
-- LOS CHECK miran solo la fila (regla 04). El de las horas valida el rango y la cantidad; que no se repitan lo impone el
-- dominio (`MiRadar`), porque detectar repetidos en un CHECK exigiria una subconsulta, que Postgres no permite ahi.
-- `<@` ("esta contenido en") tambien rechaza un NULL dentro del arreglo, porque un NULL nunca esta contenido. El de los
-- dias pide solo que sea un arreglo JSON y que haya horas para todos los dias (los dias propios se apoyan en ellas):
-- las dos cosas se ven con los datos de la propia fila. El resto de la forma (dia 1..7, sin repetir, horas validas por
-- dia) es del dominio.
--
-- COMPATIBILIDAD. Las filas existentes quedan con las tres columnas en NULL: Mi Radar apagado, sin dias propios y los
-- registros viejos sin hora prevista. NO se rellena nada: rellenar la hora prevista de 08 a 19 adivinaria los
-- registros que SER hizo fuera de hora. Los dias 1 a 7 no cambian. El indice `radar_un_registro_por_hora` (V52) no se
-- toca.

SET search_path TO renaser, public;

ALTER TABLE participantes_programa
    ADD COLUMN horas_mi_radar smallint[] NULL;

ALTER TABLE participantes_programa
    ADD CONSTRAINT participantes_programa_horas_mi_radar_validas
        CHECK (horas_mi_radar IS NULL
               OR (cardinality(horas_mi_radar) BETWEEN 1 AND 12
                   AND horas_mi_radar <@ ARRAY[3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]::smallint[]));

ALTER TABLE participantes_programa
    ADD COLUMN dias_mi_radar jsonb NULL;

ALTER TABLE participantes_programa
    ADD CONSTRAINT participantes_programa_dias_mi_radar_validos
        CHECK (dias_mi_radar IS NULL
               OR (jsonb_typeof(dias_mi_radar) = 'array' AND horas_mi_radar IS NOT NULL));

ALTER TABLE registros_radar
    ADD COLUMN hora_prevista smallint NULL;

ALTER TABLE registros_radar
    ADD CONSTRAINT registros_radar_hora_prevista_valida
        CHECK (hora_prevista IS NULL OR hora_prevista BETWEEN 0 AND 23);
