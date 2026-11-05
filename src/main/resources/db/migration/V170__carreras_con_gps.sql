-- ============================================================================
-- Kilómetros en vivo con GPS (spec 042, D-323, decisiones del dueño del 2026-11-05).
--
-- QUE PROBLEMA RESUELVE
-- ---------------------
-- La persona sale a correr o caminar desde Training: el teléfono graba la carrera (GPS filtrado, tramos, ruta) sin hablar con
-- el servidor y, al terminar, manda UN solo pedido con una `carrera_id` que generó al empezar. El servidor tiene que guardar esa
-- carrera de forma idempotente (un reintento, un doble toque o «sin red y después con red» no pueden crear otra ni pagar dos
-- veces), y en la misma transacción aplicar sus km al hábito KILÓMETROS DIARIOS con la regla «el mayor entre carreras y Health
-- Connect».
--
-- QUE HACE
-- --------
-- 1. Tabla `carreras`. Es la ÚNICA tabla nueva y hace falta porque una persona puede tener VARIAS carreras el mismo día, cada una
--    con su clave de idempotencia, su huella, sus tramos, su ruta y su resultado en el hábito: no caben en la fila única
--    (participante, hábito, fecha) de `registros_habito`, que guarda solo el número del día. No se reusa `evidencias` (es un
--    archivo de un registro, no una actividad con su clave) ni `registros_habito` (una fila por día).
--    - `UNIQUE (participante_id, carrera_id)`: la idempotencia la resuelve la base. Dos pedidos iguales a la vez: el segundo
--      `INSERT … ON CONFLICT DO NOTHING` espera al primero, no inserta y lee lo guardado.
--    - `huella char(64)`: SHA-256 del cuerpo canónico. Misma `carrera_id` con otra huella es un 409 y no pisa nada.
--    - `fecha`: el día de la carrera en la zona de la persona (el de `iniciada_en`, aunque termine pasada la medianoche).
--    - `tramos jsonb`: segundos por km. Se guardan porque sin ruta (apagada, o borrada a los 180 días) no se pueden recalcular.
--    - `ruta_polyline` / `ruta_puntos`: la ruta ya simplificada y sin los primeros ni los últimos 200 m (la recorta el teléfono).
--      Van juntas o ninguna; un barrido las pone en NULL a los 180 días y queda el resumen.
--    - `cuenta_para_el_habito`, `motivo_no_cuenta`, `por_revisar`, `motivo_revision`: lo que decidió el servidor (límites
--      anti-trampa del dueño: más de 25 km/h de media o más de 60 km no cuentan; un km a más de 22 km/h queda por revisar).
--    - `resultado_habito`, `registro_habito_id`, `km_del_dia`, `origen_del_dia`, `puntos_otorgados`: qué pasó con el hábito. Se
--      guardan para que una respuesta repetida sea LA MISMA que la primera, sin recalcular.
--    Los CHECK son todos evaluables con la fila (regla 04). Los topes son los de entrada de la spec (FR-026): la base los repite
--    para que ninguna escritura que no pase por el servicio deje una carrera imposible.
-- 2. `registros_habito.origen_medicion` admite 'GPS'. Texto con CHECK y no enum (E-187), como en V84 y V161. Con 'GPS' las
--    columnas de lectura de Health Connect quedan en NULL: el CHECK `registros_habito_lectura_automatica_completa` de V161 ya las
--    exige solo con 'HEALTH_CONNECT', así que no se toca. El detalle de una carrera vive en `carreras`.
-- 3. `consultas_de_ficha.tipo` admite 'RUTA_DE_CARRERA': cada lectura de la ruta de OTRA persona (su mentor o guía, el líder,
--    Administración o Alquimista) deja constancia, igual que el diagnóstico (V157). No es de una semana y no entra al resumen de
--    las 20:00 del mentor (ese lee solo `tipo = 'SEMANA'`).
--
-- LO QUE NO HACE
-- --------------
-- No guarda el peso de nadie (las calorías llegan calculadas por el teléfono con el peso de Plan o 70 kg), no guarda puntos GPS
-- crudos ni velocidades por punto, y no toca ningún registro que ya existe.
-- ============================================================================

SET search_path TO renaser, public;

CREATE TABLE carreras (
    id                     uuid          PRIMARY KEY,
    participante_id        uuid          NOT NULL REFERENCES usuarios (id),
    carrera_id             uuid          NOT NULL,
    huella                 char(64)      NOT NULL CHECK (huella ~ '^[0-9a-f]{64}$'),
    fecha                  date          NOT NULL,
    iniciada_en            timestamptz   NOT NULL,
    terminada_en           timestamptz   NOT NULL,
    duracion_activa_seg    integer       NOT NULL CHECK (duracion_activa_seg BETWEEN 60 AND 43200),
    distancia_m            numeric(8,1)  NOT NULL CHECK (distancia_m BETWEEN 100 AND 100000),
    subida_m               integer       NULL CHECK (subida_m IS NULL OR subida_m BETWEEN 0 AND 5000),
    pasos                  integer       NULL CHECK (pasos IS NULL OR pasos BETWEEN 0 AND 150000),
    calorias_aprox         integer       NULL CHECK (calorias_aprox IS NULL OR calorias_aprox BETWEEN 0 AND 6000),
    metros_sin_gps         integer       NULL CHECK (metros_sin_gps IS NULL OR metros_sin_gps >= 0),
    tramos                 jsonb         NOT NULL CHECK (jsonb_typeof(tramos) = 'array' AND jsonb_array_length(tramos) <= 61),
    ruta_polyline          text          NULL CHECK (ruta_polyline IS NULL OR octet_length(ruta_polyline) <= 102400),
    ruta_puntos            integer       NULL CHECK (ruta_puntos IS NULL OR ruta_puntos BETWEEN 2 AND 3000),
    cuenta_para_el_habito  boolean       NOT NULL,
    motivo_no_cuenta       text          NULL,
    por_revisar            boolean       NOT NULL DEFAULT false,
    motivo_revision        text          NULL,
    resultado_habito       text          NOT NULL CHECK (resultado_habito IN
                               ('CERRADO', 'ACTUALIZADO', 'SIN_CAMBIO', 'YA_CON_CAPTURA', 'DIA_CERRADO', 'SIN_HABITO', 'NO_CUENTA')),
    registro_habito_id     uuid          NULL REFERENCES registros_habito (id) ON DELETE SET NULL,
    km_del_dia             numeric(7,2)  NULL CHECK (km_del_dia IS NULL OR km_del_dia >= 0),
    origen_del_dia         text          NULL CHECK (origen_del_dia IS NULL OR origen_del_dia IN ('MANUAL', 'HEALTH_CONNECT', 'GPS')),
    puntos_otorgados       integer       NOT NULL DEFAULT 0 CHECK (puntos_otorgados >= 0),
    app_version            varchar(20)   NULL,
    creada_en              timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT carreras_una_por_clave UNIQUE (participante_id, carrera_id),
    CONSTRAINT carreras_termina_despues CHECK (terminada_en > iniciada_en),
    CONSTRAINT carreras_dura_hasta_12_horas CHECK (terminada_en - iniciada_en <= interval '12 hours'),
    CONSTRAINT carreras_ruta_completa CHECK ((ruta_polyline IS NULL) = (ruta_puntos IS NULL)),
    CONSTRAINT carreras_sin_gps_dentro_de_la_distancia CHECK (metros_sin_gps IS NULL OR metros_sin_gps <= distancia_m),
    CONSTRAINT carreras_motivo_si_no_cuenta CHECK (cuenta_para_el_habito = (motivo_no_cuenta IS NULL)),
    CONSTRAINT carreras_motivo_si_por_revisar CHECK (por_revisar = (motivo_revision IS NOT NULL)),
    CONSTRAINT carreras_no_cuenta_segun_resultado CHECK (cuenta_para_el_habito OR resultado_habito = 'NO_CUENTA'),
    CONSTRAINT carreras_puntos_solo_al_cerrar CHECK (puntos_otorgados = 0 OR resultado_habito = 'CERRADO')
);

-- La agenda del día y la suma de km del día: por persona y fecha.
CREATE INDEX carreras_participante_fecha_idx ON carreras (participante_id, fecha);
-- El barrido de retención de la ruta (180 días): solo las que todavía tienen ruta.
CREATE INDEX carreras_con_ruta_idx ON carreras (iniciada_en) WHERE ruta_polyline IS NOT NULL;

COMMENT ON TABLE carreras IS
    'Una carrera con GPS terminada (spec 042). Idempotente por (participante_id, carrera_id); la huella detecta otro contenido.';
COMMENT ON COLUMN carreras.carrera_id IS 'UUID v4 que generó el teléfono al empezar: la clave de idempotencia del envío.';
COMMENT ON COLUMN carreras.huella IS 'SHA-256 (hex) del cuerpo canónico del envío: misma carrera_id con otra huella es un 409.';
COMMENT ON COLUMN carreras.fecha IS 'Día de la carrera en la zona de la persona: el de iniciada_en (FR-021).';
COMMENT ON COLUMN carreras.tramos IS 'Arreglo de {km, segundos} por kilómetro completo; el último puede ser parcial.';
COMMENT ON COLUMN carreras.ruta_polyline IS 'Ruta codificada (algoritmo de Google), sin los primeros ni últimos 200 m. Se borra a los 180 días.';
COMMENT ON COLUMN carreras.resultado_habito IS 'Qué pasó con el hábito de km al guardarla; la respuesta repetida devuelve este mismo.';
COMMENT ON COLUMN carreras.puntos_otorgados IS 'Lo que pagó ESTA carrera: solo la que cerró el hábito (CERRADO).';

ALTER TABLE registros_habito
    DROP CONSTRAINT registros_habito_origen_medicion_valido;

ALTER TABLE registros_habito
    ADD CONSTRAINT registros_habito_origen_medicion_valido
        CHECK (origen_medicion IS NULL OR origen_medicion IN ('MANUAL', 'HEALTH_CONNECT', 'GPS'));

ALTER TABLE consultas_de_ficha DROP CONSTRAINT consultas_de_ficha_tipo_check;
ALTER TABLE consultas_de_ficha
    ADD CONSTRAINT consultas_de_ficha_tipo_check CHECK (tipo IN ('SEMANA', 'DIAGNOSTICO', 'RUTA_DE_CARRERA'));

ALTER TABLE consultas_de_ficha DROP CONSTRAINT consultas_de_ficha_rol_consultor_check;
ALTER TABLE consultas_de_ficha
    ADD CONSTRAINT consultas_de_ficha_rol_consultor_check CHECK (
        rol_consultor IN ('MENTOR_LEAD', 'ADMIN', 'ALCHEMIST')
        OR (tipo IN ('DIAGNOSTICO', 'RUTA_DE_CARRERA') AND rol_consultor IN ('MENTOR', 'TRAINEE_GUIDE', 'IT_SUPPORT')));

COMMENT ON COLUMN consultas_de_ficha.tipo IS
    'Qué se abrió: SEMANA (V109), DIAGNOSTICO (V157) o RUTA_DE_CARRERA (la ruta de una carrera con GPS, V170).';
