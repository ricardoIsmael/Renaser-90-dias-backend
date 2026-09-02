-- C-7 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): "Outbox sin
-- republicacion al reiniciar, sin limpieza y con listeners no idempotentes".
--
-- Los 4 listeners de `notifications` que consumen eventos de dominio de otros modulos
-- (HabitoCompletado/RachaCompletada/SantuarioRoto de `habits`, RocaCompletada de `rocks`)
-- llamaban a EmitirNotificacionUseCase.emitir sin ninguna clave de deduplicacion: con
-- at-least-once (el outbox de Spring Modulith puede entregar el MISMO evento mas de una vez
-- -- reintento tras un fallo transitorio, o la republicacion al reiniciar que esta migracion
-- habilita en application.yaml, spring.modulith.events.republish-outstanding-events-on-restart)
-- cada redelivery insertaba una fila nueva en notificaciones y disparaba un push duplicado.
--
-- origen_evento_id guarda el id de dominio del evento que origino la notificacion
-- (registroId/rachaId/rocaId -- ver habits.api.HabitoCompletadoEvent/RachaCompletadaEvent/
-- SantuarioRotoEvent y rocks.api.RocaCompletadaEvent). El indice unico parcial de abajo hace
-- que un segundo intento sobre el MISMO evento choque contra la restriccion en vez de crear
-- una fila nueva -- NotificacionService.emitir atrapa esa DataIntegrityViolationException (en
-- su propia transaccion REQUIRES_NEW, mismo patron que
-- ConversacionService.crearDirectaConAmbosParticipantes para C-10) y lo trata como exito
-- idempotente: no crea una segunda fila y no reenvia el push.
--
-- NULL cuando la notificacion no viene de un evento de dominio con id propio (cualquier otro
-- llamador futuro de EmitirNotificacionUseCase sin origen rastreable) -- el indice parcial
-- (WHERE origen_evento_id IS NOT NULL) deja esos casos sin deduplicar a proposito: no existe
-- clave natural para ellos, y forzar una la inventaria.

BEGIN;

-- Explicito a proposito, mismo motivo que V3/V11/V12/V13/V14: si esta migracion corre sola en
-- un despliegue posterior, el search_path que fija V1 no esta puesto en la sesion.
SET search_path TO renaser, public;

ALTER TABLE notificaciones ADD COLUMN origen_evento_id uuid;

COMMENT ON COLUMN notificaciones.origen_evento_id IS
    'Id de dominio del evento que origino esta notificacion (registroId/rachaId/rocaId segun '
    'el tipo), usado solo para deduplicar entregas repetidas del outbox (C-7). NULL cuando no '
    'hay un evento de dominio identificable detras.';

CREATE UNIQUE INDEX notificaciones_origen_evento_uk
    ON notificaciones (usuario_id, tipo, origen_evento_id)
    WHERE origen_evento_id IS NOT NULL;

COMMIT;
