-- V171 — La sala de Meet de un evento graba, transcribe y toma notas sola (spec 040, decisión 6 del dueño del 2026-11-05, FR-036 y FR-037)
--
-- Por qué se toca la base
-- -----------------------
-- Pedido del dueño: «que el Meet ya venga grabando y transcribiendo». Al crear cada sala, el servidor le pide a Google (Meet REST API,
-- `config.artifactConfig`) que grabe, transcriba y tome notas sola, y LEE de vuelta lo que quedó, porque un 200 no prueba que quedó y porque una
-- cuenta sin el plan (grabar: Google One de 2 TB o más; transcribir y las notas: Google Workspace) puede dejarlo apagado sin dar un error. Lo que
-- Google confirmó se guarda POR SALA (por fecha), igual que `acceso_confirmado`, para que Administración vea en el detalle del evento «Para grabar
-- hace falta…» sin volver a preguntarle a Google cada vez que abre la pantalla.
--
-- Por qué tres columnas y no una: cada una se pide y se confirma por separado (una cuenta con Google One de 2 TB graba pero no transcribe), y el
-- aviso dice cuál falta. Son NULABLES a propósito, con tres estados: `true` = Google lo dejó encendido; `false` = se pidió y Google no lo dejó;
-- `NULL` = no se pidió (el interruptor `EVENTOS_MEET_GRABAR` / `..._TRANSCRIBIR` / `..._NOTAS` apagado) o todavía no se sabe. Las filas que ya
-- existen quedan en `NULL`: no se inventa lo que Google tiene.
--
-- No es trabajo pendiente: ninguna de las tres entra en el filtro de reintentos de `eventos_meet` (si la cuenta no tiene el plan, reintentar no lo
-- cambia), por eso no se toca el índice `eventos_meet_proximo_intento_idx`.
--
-- Además, la auditoría (`integracion_google_auditoria.accion`) suma `AJUSTAR_GRABACION`: el CHECK de V169 enumera las acciones y hay que
-- reemplazarlo (mismo nombre que le dio Postgres en V169).

SET search_path TO renaser, public;

ALTER TABLE eventos_meet
    ADD COLUMN grabacion_confirmada     boolean,
    ADD COLUMN transcripcion_confirmada boolean,
    ADD COLUMN notas_confirmadas        boolean;

ALTER TABLE integracion_google_auditoria DROP CONSTRAINT integracion_google_auditoria_accion_check;
ALTER TABLE integracion_google_auditoria
    ADD CONSTRAINT integracion_google_auditoria_accion_check
        CHECK (accion IN ('CREAR_SALA', 'ACTUALIZAR_SALA', 'BORRAR_SALA', 'AJUSTAR_ACCESO', 'AJUSTAR_GRABACION', 'REINTENTAR', 'CONECTAR',
                          'DESCONECTAR', 'CUENTA_REVOCADA'));
