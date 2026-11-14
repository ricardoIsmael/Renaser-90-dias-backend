-- V195 — Cómo quedó el pedido de hacer coanfitrión a quien dirige cada sesión de Meet (spec 040 FR-038, issue #151)
--
-- Por qué se toca la base
-- -----------------------
-- Decisión del dueño (14/11/2026): las sesiones de Meet deben quedar SIEMPRE grabadas y transcritas sin depender de que entre la cuenta
-- conectada (la anfitriona). Google solo empieza a grabar y transcribir solo cuando entra alguien con permiso para grabar: el anfitrión o un
-- coanfitrión. Renaser pide, por la Meet REST API (`spaces.members`, rol COHOST), que quien dirige la sesión sea coanfitrión de la sala. Ese
-- pedido puede fallar (falta el permiso `meetings.space.created`, la sala no es «de la app», el correo no es de una cuenta de Google) sin
-- romper el evento, así que el resultado se anota por sala para que Administración lo vea y el diagnóstico de #151 distinga «quien dirige no
-- era coanfitrión».
--
-- Por qué en `eventos_meet` y no en otra tabla: el resultado es de UNA sala (una fila por evento y fecha), nace y se borra con ella, y se
-- consulta siempre junto con el resto del estado de la sala. Por qué no se reusa `ultimo_error`: ese campo es del ciclo de reintentos de la
-- creación y un fallo de coanfitriones no debe poner la sala en FALLIDO ni reintentarse solo.
--
-- `coanfitriones_estado` es NULL = todavía no se pidió (y en todas las filas anteriores a esta migración: la próxima sincronización de cada
-- fecha desde hoy lo pide, que es el relleno de las salas existentes). `coanfitriones_motivo` es una frase fija; nunca correos ni texto de Google.

SET search_path TO renaser, public;

ALTER TABLE eventos_meet
    ADD COLUMN coanfitriones_estado text,
    ADD COLUMN coanfitriones_motivo text,
    ADD CONSTRAINT eventos_meet_coanfitriones_estado_chk
        CHECK (coanfitriones_estado IS NULL OR coanfitriones_estado IN ('OK', 'PARCIAL', 'FALLIDO', 'SIN_CORREO'));

COMMENT ON COLUMN eventos_meet.coanfitriones_estado IS
    'Spec 040 FR-038: OK, PARCIAL, FALLIDO o SIN_CORREO; NULL = todavía no se pidió. Nunca guarda correos.';
COMMENT ON COLUMN eventos_meet.coanfitriones_motivo IS
    'Frase fija con el porqué cuando coanfitriones_estado no es OK (sin correos ni texto de Google).';

-- La auditoría de Google (FR-016) también deja una fila por cada pedido de coanfitriones, con la misma forma que las demás acciones (quién,
-- qué evento, si salió, el código de Google). Sin ella, el CHECK de la acción rechazaría la fila. Se rehace como lo hizo V184.
ALTER TABLE integracion_google_auditoria DROP CONSTRAINT integracion_google_auditoria_accion_check;
ALTER TABLE integracion_google_auditoria
    ADD CONSTRAINT integracion_google_auditoria_accion_check
        CHECK (accion IN ('CREAR_SALA', 'ACTUALIZAR_SALA', 'BORRAR_SALA', 'AJUSTAR_ACCESO', 'AJUSTAR_GRABACION', 'AJUSTAR_COANFITRIONES',
                          'REINTENTAR', 'CONECTAR', 'DESCONECTAR', 'CUENTA_REVOCADA', 'TRANSCRIPCION_GUARDADA', 'TRANSCRIPCION_SIN_DATOS',
                          'TRANSCRIPCION_LEIDA', 'TRANSCRIPCION_BORRADA'));
