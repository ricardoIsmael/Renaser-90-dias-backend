-- ============================================================================
-- Pasos del teléfono para «Kilómetros diarios» (spec 037, D-310, pedido del dueño del 2026-11-02).
--
-- QUE PROBLEMA RESUELVE
-- ---------------------
-- El dueño pidió que el teléfono cuente los pasos «tipo Pokémon GO»: la app lee de Health Connect (Android 14+) lo que
-- el sistema y los relojes registran, y con la meta alcanzada un toque cierra el hábito. V84 dejó `valor_medido` y
-- `origen_medicion` justamente «para que la fase 2 (lectura automática de la captura, o pasos del teléfono) pueda
-- convivir con lo manual». Esta migración es esa fase 2. Falta dónde guardar de qué fuente salió el número, para que
-- el mentor lo vea y el servidor pueda dudar de él, y dónde vive la meta diaria que Administración configura.
--
-- QUE HACE
-- --------
-- 1. `registros_habito.origen_medicion` admite 'HEALTH_CONNECT' (además de 'MANUAL'). Se reemplaza el CHECK de V84 por
--    otro con el valor nuevo: texto con CHECK y no enum de Postgres, porque un valor nuevo de enum no se puede usar en la
--    transacción que lo crea (E-187) y ampliar un CHECK es una sola migración.
-- 2. Cinco columnas en `registros_habito`, todas atributos DEL REGISTRO DEL DIA (una fila por participante, hábito y
--    fecha: justo la UNIQUE de la tabla). Una tabla `lecturas_de_pasos` repetiría esa clave, el estado y la fecha, y
--    habría que mantener dos filas coherentes; es el mismo razonamiento de V84. Ninguna se reusa de otra columna:
--      - `pasos_medidos integer`          los pasos del día (opcional: un reloj puede dar solo la distancia). No cabe en
--                                         `valor_medido`, que son km con dos decimales.
--      - `fuente_medicion varchar(60)`    etiqueta legible de la fuente («Galaxy Watch», «Tu teléfono»); la lee el mentor.
--                                         60 como el tope del endpoint; no se usa para decidir nada.
--      - `dispositivo_medicion text`      TELEFONO | RELOJ | OTRO. Lo que la ficha dice: «medido por el reloj / el teléfono».
--                                         Texto con CHECK por la misma razón del punto 1.
--      - `medicion_leida_en timestamptz`  cuándo leyó el teléfono; sirve para «leído a las 17:10» y para auditar.
--      - `medicion_por_revisar boolean`   el servidor acepta lo dudoso pero lo marca para el mentor (muy lejos, relación
--                                         pasos/distancia imposible). NOT NULL DEFAULT false: los registros que ya existen
--                                         no se tocan.
-- 3. CHECKs, todos evaluables con los datos de la fila (regla 04): origen, fuente, dispositivo y hora de lectura van
--    juntos (los tres últimos no nulos si y solo si el origen es HEALTH_CONNECT); pasos y «por revisar» solo con
--    HEALTH_CONNECT. Los de V84 (valor >= 0, valor y origen juntos, solo COMPLETADO lleva medición) siguen.
-- 4. `habitos.meta_medicion numeric(7,2)`: la meta diaria del hábito medible, que Administración cambia en «Hábitos del
--    programa». Es un atributo DEL HABITO (no del participante ni global) porque cada formación tiene su propio hábito
--    (spec 023). NULL = la del programa (5 km, constante de `PoliticaKilometros`): por eso NO se siembra, y un hábito
--    copiado a otra formación queda con el valor por defecto, que es lo seguro. No se reusa ninguna columna de `habitos`:
--    `horas_extra_evidencia` y `dia_limite_edicion_libre` son otras reglas. Rango 0,50 a 50: una meta de cero cumpliría
--    el hábito sola, y más de 50 km diarios no es una meta de este programa (el servidor además topa lo automático en 60).
--
-- LO QUE NO HACE
-- --------------
-- No crea tablas, no guarda frecuencia cardiaca, sueño, rutas, series por minuto ni identificadores del dispositivo, y no
-- toca los registros que ya existen (todo lo nuevo es NULL o false). La unidad de la medición sigue viviendo en la
-- política del hábito, no en la base (D-226).
-- ============================================================================

SET search_path TO renaser, public;

ALTER TABLE registros_habito
    ADD COLUMN pasos_medidos        integer      NULL,
    ADD COLUMN fuente_medicion      varchar(60)  NULL,
    ADD COLUMN dispositivo_medicion text         NULL,
    ADD COLUMN medicion_leida_en    timestamptz  NULL,
    ADD COLUMN medicion_por_revisar boolean      NOT NULL DEFAULT false;

ALTER TABLE registros_habito
    DROP CONSTRAINT registros_habito_origen_medicion_valido;

ALTER TABLE registros_habito
    ADD CONSTRAINT registros_habito_origen_medicion_valido
        CHECK (origen_medicion IS NULL OR origen_medicion IN ('MANUAL', 'HEALTH_CONNECT')),
    ADD CONSTRAINT registros_habito_pasos_no_negativos
        CHECK (pasos_medidos IS NULL OR pasos_medidos >= 0),
    ADD CONSTRAINT registros_habito_dispositivo_medicion_valido
        CHECK (dispositivo_medicion IS NULL OR dispositivo_medicion IN ('TELEFONO', 'RELOJ', 'OTRO')),
    ADD CONSTRAINT registros_habito_lectura_automatica_completa
        CHECK ((origen_medicion = 'HEALTH_CONNECT') = (fuente_medicion IS NOT NULL)
           AND (origen_medicion = 'HEALTH_CONNECT') = (dispositivo_medicion IS NOT NULL)
           AND (origen_medicion = 'HEALTH_CONNECT') = (medicion_leida_en IS NOT NULL)),
    ADD CONSTRAINT registros_habito_pasos_solo_automaticos
        CHECK (pasos_medidos IS NULL OR origen_medicion = 'HEALTH_CONNECT'),
    ADD CONSTRAINT registros_habito_por_revisar_solo_automaticos
        CHECK (NOT medicion_por_revisar OR origen_medicion = 'HEALTH_CONNECT');

COMMENT ON COLUMN registros_habito.pasos_medidos IS
    'Pasos del dia leidos de Health Connect (opcional). Solo con origen_medicion HEALTH_CONNECT. Spec 037.';
COMMENT ON COLUMN registros_habito.fuente_medicion IS
    'Etiqueta legible de la fuente (Galaxy Watch, Tu telefono). La lee el mentor; no decide nada. Spec 037.';
COMMENT ON COLUMN registros_habito.dispositivo_medicion IS
    'TELEFONO | RELOJ | OTRO: de que tipo de dispositivo salio la lectura. Spec 037.';
COMMENT ON COLUMN registros_habito.medicion_leida_en IS
    'Instante en que el telefono leyo Health Connect. Spec 037.';
COMMENT ON COLUMN registros_habito.medicion_por_revisar IS
    'El servidor acepto una lectura dudosa (muy lejos, relacion pasos/distancia imposible) y la marco para el mentor. Spec 037.';

ALTER TABLE habitos
    ADD COLUMN meta_medicion numeric(7,2) NULL;

ALTER TABLE habitos
    ADD CONSTRAINT habitos_meta_medicion_valida
        CHECK (meta_medicion IS NULL OR (meta_medicion >= 0.5 AND meta_medicion <= 50));

COMMENT ON COLUMN habitos.meta_medicion IS
    'Meta diaria del habito medible (hoy km de DAILY_KM). NULL = la del programa (5 km). La cambia Administracion. Spec 037.';
