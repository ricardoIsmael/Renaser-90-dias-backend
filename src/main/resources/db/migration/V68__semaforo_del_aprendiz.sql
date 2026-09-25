-- =====================================================================================
-- Semaforo de cumplimiento del aprendiz: el resultado de cada dia, la foto de cada semana y las
-- pausas del staff (D-168, docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md).
--
-- POR QUE V68 Y NO V65
--
-- Se escribio como V65 en paralelo con la V67 de la memoria del acompanante (otra rama). Cuando se
-- integraron, la base local ya tenia la V67 aplicada, y con out-of-order apagado (el default de
-- Flyway) una V65 pendiente por debajo de la ultima aplicada se rechaza al validar. V65 y V66
-- quedan sin usar; Flyway no necesita numeros contiguos.
--
-- QUE PROBLEMA RESUELVE
--
-- El punto 9 del Excel de problemas de formacion pide mandar a los alumnos activos un semaforo
-- verde/amarillo/rojo que diga si cumplen. La especificacion del cliente lo tenia como RF-25 y nunca
-- se construyo. El dueno (2026-09-25) fijo la regla: % del dia = (habitos cumplidos + objetivos
-- cumplidos) / (habitos que contaban + objetivos planificados); el semaforo es el promedio de los
-- ultimos 7 dias cerrados; verde >= 80, amarillo 60-79, rojo < 60; la semana va de sabado a viernes y
-- se cierra el sabado 00:00 hora local, con un aviso.
--
-- Y pidio expresamente que NO se calcule en cada subida de habito. Por eso el resultado se GUARDA: un
-- barrido por hora calcula una vez por persona y por dia cerrado (dos consultas en lote para todo el
-- padron) y leer el semaforo es leer filas. Sin procedimiento almacenado: la regla de que cuenta como
-- cumplido sigue en un solo lugar (habits y rocks) y el calculo en el dominio de points (D-43, D-63).
--
-- POR QUE NO SE REUSA `historial_coherencia`
--
-- Guarda un solo numero por dia y ese numero ya significa otra cosa: desde D-128 la "coherencia" es
-- solo objetivos (rocas diarias), y es lo que muestra Hoy. El semaforo mezcla habitos y objetivos y
-- necesita los conteos para mostrar "7 de 9" (nunca un porcentaje sin denominador).
--
-- `semaforo_dias` — UN DIA DE UNA PERSONA
--
-- Guarda los CONTEOS, no el porcentaje ni el color: los dos se derivan de los conteos en un solo
-- lugar del dominio (la leccion de V22: un dato derivable guardado se desincroniza en cuanto la regla
-- cambia). Una fila existe solo si el dia se midio; un dia pausado o fuera del programa no tiene fila.
-- Los conteos en cero son un dato ("ese dia no habia nada programado"), no un hueco.
--
-- `semaforo_semanas` — LA FOTO DEL SABADO
--
-- Lo que se le informo a la persona al cerrar la semana. APPEND-ONLY, como `ajustes_dia_programa`:
-- el codigo nunca la actualiza ni la borra. Si despues cambia un dia (hoy la app deja completar dias
-- pasados), la foto no cambia: es historia. `version_formula` permite recalcular a proposito si algun
-- dia cambia la regla, sin confundir filas de dos formulas (precedente: `ranking_celulas`, V46).
-- `porcentaje` NULL = la semana no tuvo ningun dia con algo programado ("Sin datos", nunca verde).
-- `dias_medidos` cuenta los dias que no estaban pausados; una semana entera pausada queda con 0.
--
-- `semaforo_pausas` — EL INTERRUPTOR DEL STAFF
--
-- Mentor, lider, admin y alquimista con programa propio pueden pausar su semaforo hasta una fecha
-- (respuesta del dueno, 2026-09-25). Tabla aparte y no un flag en `participantes_programa`: una
-- pausa tiene rango de fechas y queda como historia. `reanudada_el` es el dia local en que la persona
-- volvio a encenderlo antes de tiempo: desde ese dia se vuelve a medir. NO se reusa
-- `DELETE /mentor/activate-tracking`, que borra la participacion y con ella ~25 tablas en cascada.
-- Que un aprendiz no pueda pausar, y que no haya dos pausas vigentes a la vez, lo impone el dominio
-- porque depende del rol y de otras filas (regla 04).
--
-- ON DELETE CASCADE contra `participantes_programa`: si se borra el programa, su semaforo se va con el.
-- =====================================================================================

CREATE TABLE renaser.semaforo_dias (
    participante_id        uuid        NOT NULL REFERENCES renaser.participantes_programa (usuario_id) ON DELETE CASCADE,
    fecha                  date        NOT NULL,
    habitos_programados    smallint    NOT NULL,
    habitos_cumplidos      smallint    NOT NULL,
    objetivos_programados  smallint    NOT NULL,
    objetivos_cumplidos    smallint    NOT NULL,
    calculado_en           timestamptz NOT NULL,
    PRIMARY KEY (participante_id, fecha),
    CONSTRAINT semaforo_dias_habitos_validos
        CHECK (habitos_programados >= 0 AND habitos_cumplidos BETWEEN 0 AND habitos_programados),
    CONSTRAINT semaforo_dias_objetivos_validos
        CHECK (objetivos_programados >= 0 AND objetivos_cumplidos BETWEEN 0 AND objetivos_programados)
);

COMMENT ON TABLE renaser.semaforo_dias IS
    'Conteos de un dia cerrado para el semaforo del aprendiz (D-168). El % y el color se derivan en el dominio.';

CREATE TABLE renaser.semaforo_semanas (
    participante_id  uuid         NOT NULL REFERENCES renaser.participantes_programa (usuario_id) ON DELETE CASCADE,
    semana_hasta     date         NOT NULL,
    semana_desde     date         NOT NULL,
    porcentaje       numeric(4,1),
    dias_con_datos   smallint     NOT NULL,
    dias_medidos     smallint     NOT NULL,
    version_formula  text         NOT NULL,
    cerrada_en       timestamptz  NOT NULL,
    PRIMARY KEY (participante_id, semana_hasta),
    CONSTRAINT semaforo_semanas_siete_dias CHECK (semana_hasta = semana_desde + 6),
    CONSTRAINT semaforo_semanas_cierra_en_viernes CHECK (EXTRACT(ISODOW FROM semana_hasta) = 5),
    CONSTRAINT semaforo_semanas_porcentaje_valido CHECK (porcentaje BETWEEN 0 AND 100),
    CONSTRAINT semaforo_semanas_dias_validos
        CHECK (dias_medidos BETWEEN 0 AND 7 AND dias_con_datos BETWEEN 0 AND dias_medidos),
    CONSTRAINT semaforo_semanas_sin_datos_sin_porcentaje CHECK ((porcentaje IS NULL) = (dias_con_datos = 0))
);

COMMENT ON TABLE renaser.semaforo_semanas IS
    'Foto del cierre semanal (sabado 00:00 local) del semaforo del aprendiz. Append-only (D-168).';

CREATE TABLE renaser.semaforo_pausas (
    id            uuid        PRIMARY KEY,
    usuario_id    uuid        NOT NULL REFERENCES renaser.participantes_programa (usuario_id) ON DELETE CASCADE,
    desde         date        NOT NULL,
    hasta         date        NOT NULL,
    reanudada_el  date,
    creada_en     timestamptz NOT NULL,
    reanudada_en  timestamptz,
    CONSTRAINT semaforo_pausas_rango CHECK (hasta >= desde),
    CONSTRAINT semaforo_pausas_reanudada_en_rango CHECK (reanudada_el IS NULL OR reanudada_el >= desde),
    CONSTRAINT semaforo_pausas_reanudada_completa CHECK ((reanudada_el IS NULL) = (reanudada_en IS NULL))
);

COMMENT ON TABLE renaser.semaforo_pausas IS
    'Pausas del semaforo del staff con programa propio (D-168). Esos dias no se miden. Nunca de un aprendiz.';

CREATE INDEX semaforo_pausas_usuario_idx ON renaser.semaforo_pausas (usuario_id, desde);
