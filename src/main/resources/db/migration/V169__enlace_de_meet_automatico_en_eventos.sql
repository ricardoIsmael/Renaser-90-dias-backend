-- V169 — Enlace de Meet automático al crear un evento (spec 040, D-321; pedido del dueño del 2026-11-04)
--
-- Por qué se toca la base
-- -----------------------
-- Hoy quien crea un evento PEGA a mano el link de Meet en `eventos.valor_ubicacion`. El dueño pidió que el link se cree solo: el servidor le pide
-- la sala a Google (Calendar API, con una cuenta Gmail de Renaser conectada por OAuth) después de guardar el evento. Hacen falta cuatro cosas que
-- hoy no existen y que no caben en ninguna tabla actual:
--
-- 1. `eventos.acceso_sala_meet`. Es lo que Administración elige EN CADA EVENTO (decisión 2 del dueño): la sala es `ABIERTA` (se entra directo; lo
--    que se elige si no se dice nada) o `CON_PERMISO` (quien organiza admite a cada persona). Se guarda en el evento y no en la cuenta porque cada
--    evento puede querer lo suyo. Es NULABLE a propósito: `NULL` = el evento no pide la sala automática (su link se pegó a mano o no usa Meet),
--    y con valor = sí la pide. Así el mismo dato dice las dos cosas y no hace falta una segunda columna booleana que pueda contradecirse con la
--    primera. El link que cree Google se guarda en `valor_ubicacion`, que ya existe: el resto del sistema (listas, «Unirme», recordatorios,
--    asistencia) lo trata como cualquier link de Meet pegado a mano.
--
-- 2. `eventos_meet`: UNA fila por evento y por FECHA (decisión 4 del dueño: «un enlace distinto por fecha»; un evento que no se repite tiene
--    una). No se puede guardar en `eventos` porque un evento que se repite tiene varias salas. Guarda el estado de cada sala, el acceso elegido y
--    el que Google confirmó al leerla de vuelta, los intentos con su próximo turno y el último error (texto corto, sin datos personales).
--    - `id_google` se DERIVA del id del evento y la fecha (`IdDeEventoDeGoogle`), pero se guarda: así borrar o actualizar el evento de Google no
--      depende de recordar la regla con que se derivó, que cambia si un evento pasa de uno solo a repetirse.
--    - `evento_id` NO tiene clave foránea, a propósito: cuando se borra un evento de Renaser su sala todavía tiene que borrarse en Google, y si
--      Google falla en ese momento, la fila es lo único que queda para seguir intentando en segundo plano. Con `ON DELETE CASCADE` se perdería.
--      Las filas ya borradas en Google (`BORRADO`) quedan como lápida 12 meses: Google no reutiliza el id de un evento borrado.
--    - `CHECK`: una sala `CREADO` siempre tiene enlace (es una invariante de la propia fila).
--
-- 3. `integracion_google`: la cuenta de Google que organiza las salas. UNA fila para toda la plataforma (`id = 1`, con CHECK). Guarda el correo,
--    el permiso permanente (refresh token) CIFRADO con AES-256-GCM (`refresh_token_cifrado` más `version_de_clave`, para poder rotar la clave), los
--    permisos que Google concedió, el id del calendario propio «Renaser 90 días» (FR-034: así RenaserOs, que comparte el Gmail, no ve nuestros
--    eventos) y el estado (`ACTIVA` o `REVOCADA`). Sin claves foráneas a `usuarios`: es un dato de la plataforma, no de una persona.
--
-- 4. `integracion_google_conexiones`: las conexiones con Google que alguien empezó y todavía no volvieron (el `state` de OAuth: de un solo uso y de
--    15 minutos). Guarda el SHA-256 del `state`, nunca el `state`, y el verificador de PKCE cifrado. Y `integracion_google_auditoria`: una fila por
--    cada acción con Google (quién, qué evento, resultado, código HTTP de Google, correlación). Sin tokens ni enlaces. Retención de 12 meses.
--
-- Todo nace vacío o nulo: ningún evento existente cambia, y la función entera está apagada de fábrica (`eventos.meet`, Soporte TI).

SET search_path TO renaser, public;

ALTER TABLE eventos
    ADD COLUMN acceso_sala_meet text CHECK (acceso_sala_meet IN ('ABIERTA', 'CON_PERMISO'));

CREATE TABLE eventos_meet (
    id                uuid        PRIMARY KEY,
    evento_id         uuid        NOT NULL,
    fecha_ocurrencia  date        NOT NULL,
    id_google         text        NOT NULL,
    enlace            text,
    estado            text        NOT NULL CHECK (estado IN ('CREADO', 'PENDIENTE', 'FALLIDO', 'PENDIENTE_DE_SINCRONIZAR', 'BORRADO')),
    acceso_elegido    text        NOT NULL CHECK (acceso_elegido IN ('ABIERTA', 'CON_PERMISO')),
    acceso_confirmado text        CHECK (acceso_confirmado IN ('ABIERTA', 'CON_PERMISO')),
    intentos          smallint    NOT NULL DEFAULT 0,
    proximo_intento   timestamptz,
    ultimo_error      text,
    creada_en         timestamptz NOT NULL,
    actualizada_en    timestamptz NOT NULL,
    version           bigint      NOT NULL DEFAULT 0,
    CONSTRAINT eventos_meet_evento_fecha_uk UNIQUE (evento_id, fecha_ocurrencia),
    CONSTRAINT eventos_meet_id_google_uk UNIQUE (id_google),
    CONSTRAINT eventos_meet_creado_con_enlace CHECK (estado <> 'CREADO' OR enlace IS NOT NULL)
);

-- El proceso en segundo plano busca lo que ya toca volver a intentar.
CREATE INDEX eventos_meet_proximo_intento_idx ON eventos_meet (proximo_intento) WHERE proximo_intento IS NOT NULL;

CREATE TABLE integracion_google (
    id                    smallint    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    correo                text        NOT NULL,
    refresh_token_cifrado text        NOT NULL,
    version_de_clave      smallint    NOT NULL,
    permisos              text        NOT NULL,
    calendario_id         text,
    estado                text        NOT NULL CHECK (estado IN ('ACTIVA', 'REVOCADA')),
    conectada_por         uuid,
    conectada_en          timestamptz NOT NULL,
    ultimo_uso_en         timestamptz,
    ultimo_error          text,
    ultimo_aviso_en       timestamptz,
    version               bigint      NOT NULL DEFAULT 0
);

CREATE TABLE integracion_google_conexiones (
    hash_del_state        text        PRIMARY KEY,
    actor_id              uuid        NOT NULL,
    code_verifier_cifrado text        NOT NULL,
    vence_en              timestamptz NOT NULL
);

CREATE TABLE integracion_google_auditoria (
    id          uuid        PRIMARY KEY,
    en          timestamptz NOT NULL,
    accion      text        NOT NULL CHECK (accion IN ('CREAR_SALA', 'ACTUALIZAR_SALA', 'BORRAR_SALA', 'AJUSTAR_ACCESO', 'REINTENTAR',
                                                       'CONECTAR', 'DESCONECTAR', 'CUENTA_REVOCADA')),
    actor_id    uuid,
    evento_id   uuid,
    exito       boolean     NOT NULL,
    codigo_http smallint,
    correlacion text        NOT NULL
);

CREATE INDEX integracion_google_auditoria_en_idx ON integracion_google_auditoria (en DESC);
