-- V158 — Cronograma de pagos de cada persona (spec 032; D-307)
--
-- Por qué se toca la base
-- -----------------------
-- El dueño (2026-11-01) pidió que, al aceptar una solicitud, Administración decida si la persona «pagó completo» (sin cronograma) o «paga en cuotas» (con cronograma), que la persona lo
-- vea en Yo → «Mi ingreso» y que Administración o Alquimista marquen cada cuota «Pagado» o «Pendiente». Hasta hoy la app no sabía nada de pagos: quien cobra lo llevaba afuera.
--
-- Por qué no hacen falta tablas de cronogramas, planes ni pagos
-- ------------------------------------------------------------
-- · Lo único genuinamente nuevo es la LISTA de cuotas de cada persona (fecha fija, monto y si se pagó): una tabla, `cuotas_de_pago`. Sin plan modelo (decisión 3: persona por persona) y sin
--   tabla de pagos aparte: cada cuota dice si está pagada, quién lo marcó y cuándo (decisión 5).
-- · La modalidad («sin cronograma» / «con cronograma»), la moneda del cronograma y la confirmación de la persona son datos 1:1 con la persona, de su ingreso, así que van como columnas
--   de `estado_onboarding` (PK `usuario_id`) y no en una tabla nueva: es el mismo criterio de la marca «con o sin diagnóstico» (V143, spec 026) y se lee junto con ella. `estado_onboarding`
--   ya guarda también la aceptación de los términos, que es el «Acuerdo» que la maqueta del dueño muestra junto a «Pagos».
-- · Estado «sin anotar» = `pago_modalidad IS NULL`: las cuentas de antes y el personal que entra con «Sin cronograma» por defecto no tienen nada que mostrar. No se hace backfill.
--
-- Por qué estos nombres y tipos
-- -----------------------------
-- · `cuotas_de_pago.vence_el date`: una fecha FIJA del calendario, la que escribe Administración (decisión 4). Sin hora ni zona: el 15 de noviembre es el 15 de noviembre en cualquier parte.
-- · `monto numeric(12,2)`: dinero exacto, dos decimales. `CHECK (monto > 0)`.
-- · `estado text` con `CHECK` (PENDIENTE | PAGADA), como el resto del esquema: la cuota solo tiene dos estados; «vencida» o «atrasada» no existen (la maqueta no los tiene y la decisión 7 es
--   que no pagar no reprocha ni bloquea). Nada se deriva de la fecha.
-- · `estado_cambiado_por` / `estado_cambiado_en`: quién y cuándo marcó la cuota (pagada o devuelta a pendiente) por última vez. Una cuota pagada siempre dice cuándo (`CHECK`). El «quién»
--   queda en NULL si esa cuenta se borra (`ON DELETE SET NULL`): la cuota es de la persona, no de quien la marcó. No hay bitácora de cada cambio (supuesto a la vista del dueño).
-- · `creada_en`: orden estable cuando dos cuotas caen el mismo día. No se muestra.
-- · `estado_onboarding.pago_modalidad` (`SIN_CRONOGRAMA` | `CON_CRONOGRAMA`) y `pago_moneda` (`PEN` | `USD`, una por cronograma y sin valor por defecto: la elige Administración).
-- · La confirmación («Revisé mis fechas y montos» + nombre) son tres columnas: la hora, el nombre que escribió y la HUELLA de lo que confirmó (SHA-256 de la moneda y de las fechas y montos de
--   todas las cuotas, sin el estado de pago). Se guarda la huella y no un «vigente sí/no» porque así la confirmación se invalida SOLA cuando Administración cambia fechas o montos, sin que cada
--   camino que edita cuotas tenga que acordarse de borrarla; y marcar un pago no la invalida (no cambia lo acordado). Es la regla «derivar, no acumular» (reglas/02).
-- · Los `CHECK` evalúan la fila: con cronograma hay moneda y sin cronograma no; la confirmación va completa o no va, y solo existe con cronograma.
--
-- Qué pasa con lo que ya existe
-- -----------------------------
-- Nada cambia para nadie: todas las columnas nuevas son NULL y ninguna cuenta de antes tiene cuotas. La app no muestra «Mi ingreso» a quien no tiene nada anotado.

SET search_path TO renaser, public;

CREATE TABLE cuotas_de_pago (
    id                  uuid          PRIMARY KEY,
    usuario_id          uuid          NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    vence_el            date          NOT NULL,
    monto               numeric(12,2) NOT NULL CHECK (monto > 0),
    estado              text          NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'PAGADA')),
    estado_cambiado_por uuid          REFERENCES usuarios (id) ON DELETE SET NULL,
    estado_cambiado_en  timestamptz,
    creada_en           timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT cuotas_de_pago_pagada_dice_cuando CHECK (estado = 'PENDIENTE' OR estado_cambiado_en IS NOT NULL)
);

CREATE INDEX cuotas_de_pago_persona_idx ON cuotas_de_pago (usuario_id, vence_el);

COMMENT ON TABLE cuotas_de_pago IS
    'Las cuotas del cronograma de pagos de cada persona (V158, spec 032). Fecha fija del calendario, monto y estado (PENDIENTE | PAGADA); quién y cuándo marcó el estado por última vez. La modalidad, la moneda y la confirmación están en estado_onboarding.';
COMMENT ON COLUMN cuotas_de_pago.vence_el IS 'Fecha fija del calendario que escribe Administración: sin hora ni zona.';
COMMENT ON COLUMN cuotas_de_pago.estado_cambiado_por IS 'Quién marcó la cuota pagada o la devolvió a pendiente por última vez; NULL si nunca se tocó o si esa cuenta se borró.';

ALTER TABLE estado_onboarding
    ADD COLUMN pago_modalidad              text        CHECK (pago_modalidad IN ('SIN_CRONOGRAMA', 'CON_CRONOGRAMA')),
    ADD COLUMN pago_moneda                 text        CHECK (pago_moneda IN ('PEN', 'USD')),
    ADD COLUMN cronograma_confirmado_en     timestamptz,
    ADD COLUMN cronograma_confirmado_nombre text        CHECK (cronograma_confirmado_nombre IS NULL OR length(cronograma_confirmado_nombre) BETWEEN 1 AND 120),
    ADD COLUMN cronograma_confirmado_huella text,
    ADD CONSTRAINT estado_onboarding_pago_coherente CHECK (
        COALESCE(pago_modalidad = 'CON_CRONOGRAMA', false) = (pago_moneda IS NOT NULL)
        AND (cronograma_confirmado_en IS NULL OR COALESCE(pago_modalidad = 'CON_CRONOGRAMA', false))),
    ADD CONSTRAINT estado_onboarding_confirmacion_completa CHECK (
        (cronograma_confirmado_en IS NULL) = (cronograma_confirmado_nombre IS NULL)
        AND (cronograma_confirmado_en IS NULL) = (cronograma_confirmado_huella IS NULL));

COMMENT ON COLUMN estado_onboarding.pago_modalidad IS
    'SIN_CRONOGRAMA (pagó completo: Yo dice «Tu pago fue cancelado»), CON_CRONOGRAMA (cuotas en cuotas_de_pago) o NULL = sin anotar (V158, spec 032). Solo con algo anotado la app muestra «Mi ingreso».';
COMMENT ON COLUMN estado_onboarding.pago_moneda IS 'PEN (S/) o USD (US$); solo con cronograma, una por cronograma y sin valor por defecto (V158, spec 032).';
COMMENT ON COLUMN estado_onboarding.cronograma_confirmado_huella IS
    'SHA-256 de la moneda y de las fechas y montos de las cuotas que la persona confirmó (sin el estado de pago). La confirmación vale mientras coincida con la huella de ahora; si Administración cambia fechas o montos, se vuelve a pedir.';
