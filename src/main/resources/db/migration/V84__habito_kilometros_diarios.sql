-- ============================================================================
-- Hábito «Kilómetros diarios» con el número del día (D-226, pedido del dueño del 2026-09-29).
--
-- QUE PROBLEMA RESUELVE
-- ---------------------
-- El dueño pidió el hábito de kilómetros para todos, opcional, y un ranking por km acumulados en
-- todo el programa. El hábito ya existe desde V9 (`ea87fdec…`, «KILÓMETROS DIARIOS», CHECKBOX,
-- captura OBLIGATORIA, es_opcional = true) pero apagado y sin clave de sistema. Y la base no tiene
-- dónde guardar "cuántos km": `registros_habito` solo sabe si se cumplió, a qué hora y con qué
-- texto. Sin el número no hay total acumulado ni ranking.
--
-- QUE HACE
-- --------
-- 1. `registros_habito.valor_medido numeric(7,2)` — lo que la persona midió ESE día (hoy, km).
--    Por qué en `registros_habito` y no en una tabla nueva: el número es un atributo del registro
--    del día (uno por participante, hábito y fecha, que es justo la UNIQUE de esta tabla); una tabla
--    aparte repetiría esa clave, el estado y la fecha, y habría que mantenerlas coherentes.
--    Por qué no se reusa `respuesta_texto`: es texto libre de la persona («¿Qué sentiste?»); sumar
--    un texto para el ranking obligaría a parsearlo cada noche y no se podría imponer `>= 0`.
--    Por qué no `calificacion_productividad`: es smallint con CHECK 1..10.
--    Por qué `numeric(7,2)`: los km llevan decimales (3,5 km) y la suma del ranking tiene que ser
--    exacta (un float acumula error en 90 días × todo el padrón). 7,2 llega a 99 999,99: sobra para
--    km y alcanza para pasos (fase 2) sin otra migración.
--    Por qué el nombre es genérico y no `km_recorridos`: el dueño pidió dejarlo listo para pasos
--    (Health Connect / HealthKit). La unidad NO se guarda por fila: la define la política del hábito
--    en el código (`PoliticaKilometros`, por `clave_sistema`), igual que toda regla propia de un
--    hábito (`PoliticaClaseDiaria`, `PoliticaPostDiarioComunidad`). Una columna de unidad en
--    `habitos` duplicaría esa regla en dos lugares — la lección de `fecha_graduacion_esperada` (V22).
--
-- 2. `registros_habito.origen_medicion text` — de dónde vino el número: hoy solo 'MANUAL' (lo
--    escribió la persona junto a su captura). Existe para que la fase 2 (lectura automática de la
--    captura, o pasos del teléfono) pueda convivir con lo manual y se sepa después qué cifra es
--    declarada y cuál medida. Texto con CHECK y no un enum de Postgres: un valor nuevo de enum no se
--    puede usar en la transacción que lo crea (E-187) y obliga a dos migraciones; ampliar un CHECK
--    es una sola.
--
-- 3. CHECKs, todos evaluables con los datos de la fila (regla 04):
--    - valor >= 0 (el dominio exige además > 0 y un tope diario: eso es regla del hábito y vive en
--      su política, no acá);
--    - el valor y el origen van juntos o no va ninguno (mismo patrón que la meta cuantitativa de
--      las rocas, V35);
--    - solo un registro COMPLETADO lleva medición: el número se escribe al completar, nunca antes.
--
-- 4. El hábito ea87fdec pasa a `clave_sistema = 'DAILY_KM'` (lo que seleccionan la política y el
--    ranking) y a `activo = true` (se prende para todos: `habitos.activo` es global). Sigue opcional
--    (si lo completa suma al promedio, si no, no resta) y con captura obligatoria. La descripción se
--    ajusta a lo que ahora se pide: la captura Y el número.
--
-- LO QUE NO HACE
-- --------------
-- No crea tablas ni toca el horario del hábito (07:00, sin hora límite, V9): quien lo registre de
-- noche lo cumple igual, pero cobra según esa ventana, como cualquier hábito. Si el dueño quiere
-- otra ventana se cambia desde el panel (horarios del hábito), no en una migración.
-- Los tracks del hábito empiezan a generarse en el barrido nocturno siguiente.
-- Idempotente en lo que actualiza (correrla dos veces da lo mismo).
-- ============================================================================

SET search_path TO renaser, public;

ALTER TABLE registros_habito
    ADD COLUMN valor_medido    numeric(7,2) NULL,
    ADD COLUMN origen_medicion text         NULL;

ALTER TABLE registros_habito
    ADD CONSTRAINT registros_habito_valor_medido_no_negativo
        CHECK (valor_medido IS NULL OR valor_medido >= 0),
    ADD CONSTRAINT registros_habito_origen_medicion_valido
        CHECK (origen_medicion IS NULL OR origen_medicion IN ('MANUAL')),
    ADD CONSTRAINT registros_habito_medicion_completa
        CHECK ((valor_medido IS NULL) = (origen_medicion IS NULL)),
    ADD CONSTRAINT registros_habito_medicion_solo_completado
        CHECK (valor_medido IS NULL OR estado = 'COMPLETADO');

COMMENT ON COLUMN registros_habito.valor_medido IS
    'Lo que la persona midio ese dia (hoy: km de DAILY_KM). La unidad la define la politica del habito. D-226.';
COMMENT ON COLUMN registros_habito.origen_medicion IS
    'De donde vino valor_medido: MANUAL (lo escribio la persona). Fase 2: lectura automatica. D-226.';

UPDATE habitos
SET clave_sistema  = 'DAILY_KM',
    activo         = true,
    descripcion    = 'Sube la captura de tu app de actividad con la distancia y la fecha de hoy, y escribe cuántos km recorriste.',
    actualizado_en = now()
WHERE id = 'ea87fdec-d4c1-4c4f-9e61-1557bc7255d1';
