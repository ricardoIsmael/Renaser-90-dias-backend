-- V184 — La transcripción de cada sesión por Meet se guarda sola, cifrada y sin mostrarse (spec 047, D-332)
--
-- Por qué se toca la base
-- -----------------------
-- Pedido del dueño (06/11/2026): «que se guarde la transcripción, igual que RenaserOs, no mostrándola, pero que cumpla». Cuando termina una fecha
-- de un evento con sala creada por Renaser (spec 040, `eventos_meet`), el servidor lee la transcripción con la Meet REST API y la guarda aquí.
--
-- 1. `eventos_meet_transcripciones`: UNA fila por sala (evento + fecha). El texto y el enlace a la grabación van CIFRADOS (AES-256-GCM, la misma
--    clave de la 040, fuera de la base); en claro solo lo que no identifica (estado, cantidades, fechas). Se borra sola con la sala (CASCADE) y, por
--    retención (decisión P3 del dueño: 6 meses), el proceso diario vacía lo cifrado y la deja `BORRADA` como constancia.
-- 2. `eventos_meet_transcripciones_accesos`: quién sacó (LECTURA) o borró a pedido (BORRADO, derecho de cancelación de la Ley 29733) una
--    transcripción, cuándo y por qué (decisión P1: solo ADMIN y ALCHEMIST, con motivo). Sin texto. Se purga a los 12 meses, como la auditoría
--    de la 040.
-- 3. La auditoría de la 040 (`integracion_google_auditoria.accion`) suma las acciones de la transcripción. El CHECK enumera las acciones y hay que
--    reemplazarlo (mismo nombre que le dio Postgres en V169, ya reemplazado en V171).

SET search_path TO renaser, public;

CREATE TABLE eventos_meet_transcripciones (
    id                 uuid         PRIMARY KEY,
    evento_meet_id     uuid         NOT NULL UNIQUE REFERENCES eventos_meet (id) ON DELETE CASCADE,
    estado             text         NOT NULL CHECK (estado IN ('ESPERANDO', 'LISTA', 'SIN_REUNION', 'SIN_TRANSCRIPCION', 'SIN_PERMISO',
                                                               'BORRADA')),
    conferencias       integer      NOT NULL DEFAULT 0,
    entradas           integer      NOT NULL DEFAULT 0,
    largo              integer      NOT NULL DEFAULT 0,
    recortada          boolean      NOT NULL DEFAULT false,
    texto_cifrado      text,
    grabacion_cifrada  text,
    version_clave      smallint,
    intentos           integer      NOT NULL DEFAULT 0,
    proximo_intento_en timestamptz,
    motivo             varchar(300),
    obtenida_en        timestamptz,
    borrar_despues_de  timestamptz,
    borrada_en         timestamptz,
    creada_en          timestamptz  NOT NULL,
    actualizada_en     timestamptz  NOT NULL,
    CONSTRAINT eventos_meet_transcripciones_lista_con_texto CHECK (estado <> 'LISTA' OR (texto_cifrado IS NOT NULL AND borrar_despues_de IS NOT NULL)),
    CONSTRAINT eventos_meet_transcripciones_sin_texto CHECK (estado = 'LISTA' OR (texto_cifrado IS NULL AND grabacion_cifrada IS NULL))
);

COMMENT ON TABLE eventos_meet_transcripciones IS
    'Spec 047: la transcripción de una sesión por Meet (una por sala). texto_cifrado y grabacion_cifrada van cifrados; no se muestran en la app.';

CREATE INDEX eventos_meet_transcripciones_reintento_idx ON eventos_meet_transcripciones (proximo_intento_en)
    WHERE estado IN ('ESPERANDO', 'SIN_PERMISO');
CREATE INDEX eventos_meet_transcripciones_vence_idx ON eventos_meet_transcripciones (borrar_despues_de) WHERE estado = 'LISTA';

CREATE TABLE eventos_meet_transcripciones_accesos (
    id         uuid         PRIMARY KEY,
    en         timestamptz  NOT NULL,
    actor_id   uuid,
    evento_id  uuid         NOT NULL,
    fecha      date         NOT NULL,
    accion     text         NOT NULL CHECK (accion IN ('LECTURA', 'BORRADO')),
    motivo     varchar(500) NOT NULL
);

COMMENT ON TABLE eventos_meet_transcripciones_accesos IS
    'Spec 047: quién sacó o borró a pedido una transcripción, cuándo y por qué. Sin texto. Retención de 12 meses.';

CREATE INDEX eventos_meet_transcripciones_accesos_en_idx ON eventos_meet_transcripciones_accesos (en);

ALTER TABLE integracion_google_auditoria DROP CONSTRAINT integracion_google_auditoria_accion_check;
ALTER TABLE integracion_google_auditoria
    ADD CONSTRAINT integracion_google_auditoria_accion_check
        CHECK (accion IN ('CREAR_SALA', 'ACTUALIZAR_SALA', 'BORRAR_SALA', 'AJUSTAR_ACCESO', 'AJUSTAR_GRABACION', 'REINTENTAR', 'CONECTAR',
                          'DESCONECTAR', 'CUENTA_REVOCADA', 'TRANSCRIPCION_GUARDADA', 'TRANSCRIPCION_SIN_DATOS', 'TRANSCRIPCION_LEIDA',
                          'TRANSCRIPCION_BORRADA'));
