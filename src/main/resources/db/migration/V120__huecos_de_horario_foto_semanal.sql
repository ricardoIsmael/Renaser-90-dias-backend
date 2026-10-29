-- =====================================================================================
-- Huecos de horario: la foto de cada semana de una persona (spec 015, D-289).
--
-- QUE PROBLEMA RESUELVE
--
-- El dueno (2026-10-29) pidio calcular, con lo que la persona planifica, los huecos de horario que le
-- quedan, para que su acompanamiento los vea y se pueda recapitular. Los huecos se CALCULAN al pedirlos
-- (nada de eso se guarda por dia), pero hay una cosa que no se puede reconstruir despues: el horario que
-- regia en una semana ya vivida. Las horas de los habitos viven en `preferencias_horario`,
-- `horario_semanal_habito`, `horarios_habito_por_fecha` y `cambios_horario_pendientes`, y se SOBRESCRIBEN
-- cuando la persona las cambia (`historial_cambios_horario` solo guarda los cambios que cuentan para el
-- cupo semanal). Sin esta tabla, la recapitulacion de una semana pasada se haria con el horario de hoy y
-- diria algo que la persona nunca vio. El dueno eligio guardar la foto (pregunta 2, opcion B) y confirmo
-- que la tabla nueva es la forma correcta.
--
-- QUE SE GUARDA
--
-- UNA fila por persona y semana (el lunes), al terminar su domingo en SU zona: los minutos ocupados y
-- libres de la semana, lo cumplido a tiempo, tarde y lo no cumplido, los tramos que se repiten libres y el
-- horario que regia (los siete dias con su ventana, lo ocupado y los huecos, en minutos del dia).
-- Se guarda lo que se MOSTRO: si manana cambia el minimo de un hueco util, la foto sigue diciendo lo que
-- decia. Los totales van en columnas (se consultan y se suman); el detalle de los siete dias y los tramos
-- que se repiten son un documento (jsonb) porque siempre se leen y se escriben enteros, nunca por campo, y
-- tienen forma de lista de largo variable. No hay etiqueta de nada: ni titulo de habito ni de evento, solo
-- tramos de tiempo.
--
-- POR QUE NO SE REUSA OTRA TABLA
--
-- `semaforo_semanas` (V68) es la mas parecida y es el molde (una foto por persona y semana, solo se
-- agrega), pero guarda otra cosa: el porcentaje del semaforo, de sabado a viernes. Estas semanas son de
-- lunes a domingo (D-203) y guardan horas; mezclarlas obligaria a dos significados de "semana" en una
-- fila. `agenda_ocupada` (V64) es lo que SER guarda de lo que la persona dice y es de `rag`.
--
-- APPEND-ONLY (constitucion IV)
--
-- Como `semaforo_semanas` y `ajustes_dia_programa`: el codigo inserta con ON CONFLICT DO NOTHING y no hay
-- UPDATE ni DELETE. Si despues cambia un horario, la foto no cambia: es historia. La unica baja es la de la
-- cuenta (D-243), por `BorradoDeCuentaEnMentoringAdapter` y, de red de seguridad, por la cascada.
--
-- POR QUE ESTE NOMBRE Y ESTE NUMERO
--
-- `huecos_semana`: lo que guarda es la semana de huecos de una persona, igual que `semaforo_semanas` es la
-- semana del semaforo. V120: V119 la tiene `mi_radar_desde_el_dia_8` (spec 012, rama 012-mi-radar-en-yo,
-- verificado el 2026-10-29 contra todas las ramas y worktrees); Flyway no necesita numeros contiguos.
--
-- Que `semana_inicio` sea lunes y que los totales no sean negativos son invariantes de ESTA fila y van como
-- CHECK. Que los siete dias del documento sean coherentes con los totales lo impone el dominio
-- (`SemanaDeHuecos`), porque depende de lo que hay dentro del jsonb (regla 04).
--
-- ON DELETE CASCADE contra `participantes_programa`: si se borra el programa, sus fotos se van con el.
-- =====================================================================================

CREATE TABLE renaser.huecos_semana (
    participante_id       uuid        NOT NULL REFERENCES renaser.participantes_programa (usuario_id) ON DELETE CASCADE,
    semana_inicio         date        NOT NULL,
    zona                  text        NOT NULL,
    minutos_ocupados      integer     NOT NULL,
    minutos_libres        integer     NOT NULL,
    cumplidos_a_tiempo    integer     NOT NULL,
    cumplidos_tarde       integer     NOT NULL,
    no_cumplidos          integer     NOT NULL,
    huecos_que_se_repiten jsonb       NOT NULL DEFAULT '[]'::jsonb,
    horario_que_regia     jsonb       NOT NULL,
    cerrada_en            timestamptz NOT NULL,
    PRIMARY KEY (participante_id, semana_inicio),
    CONSTRAINT huecos_semana_es_lunes CHECK (EXTRACT(ISODOW FROM semana_inicio) = 1),
    CONSTRAINT huecos_semana_totales_validos CHECK (
        minutos_ocupados >= 0 AND minutos_libres >= 0
        AND cumplidos_a_tiempo >= 0 AND cumplidos_tarde >= 0 AND no_cumplidos >= 0)
);

COMMENT ON TABLE renaser.huecos_semana IS
    'Foto de la semana (lunes a domingo) de huecos de horario de una persona, guardada al terminar su domingo en su zona. Solo se agrega (spec 015, D-289).';
COMMENT ON COLUMN renaser.huecos_semana.horario_que_regia IS
    'Los siete dias de la semana tal como se calcularon: fecha, ventana, tramos ocupados y huecos, en minutos del dia.';
