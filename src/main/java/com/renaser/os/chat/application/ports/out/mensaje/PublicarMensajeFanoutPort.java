package com.renaser.os.chat.application.ports.out.mensaje;

import com.renaser.os.chat.domain.model.mensaje.Mensaje;

/**
 * `FanoutPort` de PLAN_DE_MODULOS.md linea 133: empuja un mensaje YA PERSISTIDO a quien este
 * mirando la conversacion en vivo, en TODAS las instancias del backend (CLAUDE.MD §5.2.1 —
 * un evento in-process de Modulith no cruza instancias). La implementacion real
 * (adapter/out/redis) publica a Redis Pub/Sub; nunca se llama antes del commit de la
 * transaccion que guardo el mensaje (fire-and-forget, no es el canal de verdad — Postgres lo
 * es).
 */
public interface PublicarMensajeFanoutPort {

    void publicar(Mensaje mensaje);
}
