-- V102 — Tipo de notificación OPERACION_PLATAFORMA (spec 002, fase 1; D-267)
--
-- Por qué se toca la base:
--   Cuando Soporte TI cambia un interruptor, el sistema avisa por push (FR-008, contrato §7): a Soporte TI
--   siempre, y al Admin y al Alquimista si la funcionalidad es crítica o quedó apagada para todos. La fila de
--   `notificaciones` lleva un `tipo_notificacion`, y ninguno de los que hay dice esto.
--
-- Por qué así:
--   * Un valor nuevo y no reusar ANUNCIO_SISTEMA: este aviso NO se pospone cuando Soporte TI apaga las
--     notificaciones (es justo el aviso de que las apagó), y la regla se escribe sobre el tipo. Además se
--     puede silenciar aparte en las preferencias, como RECORDATORIO_EVENTO (V70).
--   * Sola en su migración (E-187): `ADD VALUE` no se puede usar en la misma transacción que lo agrega.

ALTER TYPE renaser.tipo_notificacion ADD VALUE IF NOT EXISTS 'OPERACION_PLATAFORMA';
