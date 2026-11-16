-- V196 — Las salas de Meet se crean con la Meet API y las viejas de Calendar se reemplazan (spec 040 FR-040 y FR-041, D-360)
--
-- Por qué se toca la base
-- -----------------------
-- Decisión del dueño (16/11/2026): el permiso `meetings.space.created` solo cubre espacios creados por la app con la Meet API
-- (`spaces.create`); las salas que nacían como conferencia de un evento de Calendar daban 403 al pedir coanfitriones (FR-038). Ahora cada sala
-- nueva se crea con `spaces.create` y las de hoy en adelante se reemplazan por una sala nueva; la vieja se cierra.
--
-- * `con_meet_api`: la sala se creó con la Meet API (Renaser es su dueña). FALSE en todas las filas anteriores a esta migración: son las que el
--   proceso de fondo migra, solo las de fechas de hoy en adelante, cuando el interruptor EVENTOS_MEET_SALAS_CON_MEET_API está prendido.
-- * `enlace_anterior`: el enlace de la sala vieja mientras falta cerrarla (acceso restringido y fin de la conferencia activa). NULL = nada por
--   cerrar. Se anota ANTES de cerrar para que una caída a medias no deje una sala vieja abierta sin que nadie lo sepa.
--
-- Va en `eventos_meet` porque es del estado de UNA sala y nace y se borra con ella. No toca el filtro de reintentos de la base: la migración
-- y el cierre los recorre el proceso de mantenimiento con su propia espera (intentos / proximo_intento).

SET search_path TO renaser, public;

ALTER TABLE eventos_meet
    ADD COLUMN con_meet_api boolean NOT NULL DEFAULT false,
    ADD COLUMN enlace_anterior text;

COMMENT ON COLUMN eventos_meet.con_meet_api IS
    'Spec 040 FR-040: la sala se creó con la Meet API (spaces.create). FALSE = nació como conferencia de Calendar (se migra, FR-041).';
COMMENT ON COLUMN eventos_meet.enlace_anterior IS
    'Spec 040 FR-041: el enlace de la sala vieja de Calendar que falta cerrar; NULL = nada por cerrar.';

CREATE INDEX eventos_meet_por_migrar_idx ON eventos_meet (fecha_ocurrencia)
    WHERE estado = 'CREADO' AND (con_meet_api = false OR enlace_anterior IS NOT NULL);

ALTER TABLE integracion_google_auditoria DROP CONSTRAINT integracion_google_auditoria_accion_check;
ALTER TABLE integracion_google_auditoria
    ADD CONSTRAINT integracion_google_auditoria_accion_check
        CHECK (accion IN ('CREAR_SALA', 'ACTUALIZAR_SALA', 'BORRAR_SALA', 'AJUSTAR_ACCESO', 'AJUSTAR_GRABACION', 'AJUSTAR_COANFITRIONES',
                          'CERRAR_SALA_ANTERIOR', 'REINTENTAR', 'CONECTAR', 'DESCONECTAR', 'CUENTA_REVOCADA', 'TRANSCRIPCION_GUARDADA',
                          'TRANSCRIPCION_SIN_DATOS', 'TRANSCRIPCION_LEIDA', 'TRANSCRIPCION_BORRADA'));
