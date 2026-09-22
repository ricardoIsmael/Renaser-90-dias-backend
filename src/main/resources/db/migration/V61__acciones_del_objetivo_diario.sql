-- =====================================================================================
-- Las acciones pasan a colgar del objetivo DIARIO.
--
-- QUE PROBLEMA RESUELVE
--
-- El dueno describio la cadena del plan asi, textual: "objetivo de los 90 dias, luego objetivo
-- mensual, luego objetivo semanal, y luego objetivo diario, y estos objetivos diarios tienen
-- acciones para hacerlo". Los tres primeros niveles ya estaban (`rocas_maestras`,
-- `rocas_mensuales`, `rocas_semanales`) y el cuarto tambien (`rocas_diarias`). Lo que estaba en el
-- lugar equivocado eran las acciones: `acciones_criticas` cuelga de `rocas_semanales`, o sea que
-- para armar la semana habia que escribir nueve acciones (tres por eje) antes de saber siquiera que
-- dia se iban a hacer.
--
-- El efecto practico lo reporto el propio padron: doce campos minimos de una sentada el domingo, y
-- una lista de acciones escrita sin contexto de dia que despues habia que volver a agendar una por
-- una. Con las acciones en el dia, la semana queda en lo que es —un objetivo— y las acciones se
-- escriben cuando se planifica el dia, que es cuando la persona sabe con que cuenta.
--
-- POR QUE UNA TABLA NUEVA Y NO UN ALTER DE `acciones_criticas`
--
-- Porque cambia la clave primaria: `(roca_semanal_id, orden)` no se puede reapuntar a otra tabla
-- padre sin reescribir la PK y la FK, y eso sobre una tabla con datos es una operacion que no se
-- puede deshacer con otra migracion. Una tabla nueva deja las dos formas conviviendo el tiempo que
-- haga falta.
--
-- POR QUE NO SE BORRA `acciones_criticas`
--
-- Porque no se puede verificar desde aca que este vacia en produccion. En la base local tiene cero
-- filas, pero cero local no es cero en produccion, y `DROP TABLE` no tiene vuelta atras. Queda sin
-- escritores nuevos y documentada como historica; si el dueno confirma que quedo vacia, se borra en
-- una migracion posterior de una linea. La regla 04 pide justificar por que se toca la base: este
-- comentario tambien justifica por que NO se toca.
--
-- POR QUE NO LLEVA `completada`
--
-- Se evaluo y se descarto. El objetivo diario ya se completa por evidencia y solo por evidencia
-- (R-02, no hay PATCH que lo marque hecho); darle a cada accion su propio tilde abriria un segundo
-- camino para decir "esto ya esta" sin evidencia detras, que es justo lo que R-02 evita. Las
-- acciones describen COMO se logra el objetivo del dia; el que se cierra es el objetivo.
--
-- EL TOPE DE TRES
--
-- Mismo que tenian las criticas de la semana, y por el mismo motivo de negocio (Pareto: si son mas
-- de tres, ninguna es critica). Va como CHECK porque se puede evaluar con los datos de la fila; que
-- no haya huecos en el orden lo impone el dominio, que si puede mirar las hermanas (regla 04).
-- =====================================================================================

CREATE TABLE renaser.acciones_diarias (
    roca_diaria_id uuid     NOT NULL REFERENCES renaser.rocas_diarias (id) ON DELETE CASCADE,
    orden          smallint NOT NULL CHECK (orden BETWEEN 1 AND 3),
    descripcion    text     NOT NULL,
    PRIMARY KEY (roca_diaria_id, orden),
    CONSTRAINT accion_diaria_descripcion_no_vacia
        CHECK (btrim(descripcion) <> '')
);

COMMENT ON TABLE renaser.acciones_diarias IS
    'Las acciones con las que se logra un objetivo diario. Reemplazan a acciones_criticas, que colgaba de la semana.';

COMMENT ON TABLE renaser.acciones_criticas IS
    'HISTORICA (2026-09-22): las acciones pasaron al objetivo diario, en acciones_diarias. Sin escritores nuevos.';
