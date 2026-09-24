-- =====================================================================================
-- Agenda ocupada del aprendiz: las horas en que suele estar ocupado cada dia de la semana.
--
-- QUE PROBLEMA RESUELVE
--
-- D-160 dio al acompanante `buscar_huecos_para_habitos`: la persona dice "trabajo de 9 a 6" y el
-- codigo le calcula en que hueco hacer cada habito. Sin guardar esas horas, el acompanante tiene que
-- volver a preguntarlas en cada conversacion y no puede sugerir por su cuenta (al planificar la
-- semana, o cuando un habito choca con su trabajo). El dueno pidio hacerlo "de la manera senior" y
-- aprobo una tabla si es la correcta para sugerir (2026-09-23, D-161).
--
-- Solo se guarda con el boton: el acompanante PROPONE guardar (propuestas_acompanante, V63) y la
-- persona confirma. Nunca se escribe desde el texto del chat.
--
-- POR QUE NO SE REUSA OTRA TABLA
--
-- Los horarios de habitos (`habits`) dicen CUANDO toca cada habito; esto dice cuando la persona NO
-- puede. Son datos distintos, con otro dueno (el acompanante los usa para sugerir; `habits` no los
-- lee). `mensajes_renasia` no sirve: es texto conversacional, no se consulta por dia ni hora.
--
-- QUE SE GUARDA Y QUE NO (minimo necesario)
--
-- Dia de la semana y tramo, nada mas. No hay etiqueta ("trabajo", "clases", "terapia"): no hace
-- falta para calcular huecos y seria informacion personal de mas.
--
-- POR QUE MINUTOS Y NO `time`
--
-- Un tramo termina, a veces, en el fin del dia (18:00-24:00). Postgres acepta '24:00' en `time`,
-- pero java.time.LocalTime no lo representa y Hibernate lo romperia. En minutos del dia es exacto:
-- `desde_minuto` de 0 a 1439 y `hasta_minuto` de 1 a 1440. El tramo es [desde, hasta).
--
-- UNA FILA POR TRAMO
--
-- Se consulta por participante y se reemplaza entera al guardar (delete + insert en una
-- transaccion), asi que no hace falta mas. Un tramo nocturno se guarda partido: lunes 23:00-24:00 y
-- martes 00:00-06:00, cada uno con su dia. Que los tramos de un mismo dia no se pisen lo impone el
-- dominio (AgendaOcupada los une), porque depende de otras filas (regla 04).
--
-- ON DELETE CASCADE: si se borra la cuenta, su agenda se va con ella.
-- =====================================================================================

CREATE TABLE renaser.agenda_ocupada (
    id               uuid     PRIMARY KEY,
    participante_id  uuid     NOT NULL REFERENCES renaser.usuarios (id) ON DELETE CASCADE,
    dia_semana       smallint NOT NULL,
    desde_minuto     smallint NOT NULL,
    hasta_minuto     smallint NOT NULL,
    CONSTRAINT agenda_ocupada_dia_valido CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT agenda_ocupada_desde_valido CHECK (desde_minuto BETWEEN 0 AND 1439),
    CONSTRAINT agenda_ocupada_hasta_valido CHECK (hasta_minuto BETWEEN 1 AND 1440),
    CONSTRAINT agenda_ocupada_tramo_ordenado CHECK (desde_minuto < hasta_minuto)
);

COMMENT ON TABLE renaser.agenda_ocupada IS
    'Horas en que el aprendiz suele estar ocupado, por dia de la semana (D-161). Solo se escribe al confirmar una propuesta del acompanante.';
COMMENT ON COLUMN renaser.agenda_ocupada.dia_semana IS 'ISO-8601: 1 = lunes ... 7 = domingo.';

CREATE INDEX agenda_ocupada_participante_idx ON renaser.agenda_ocupada (participante_id);
