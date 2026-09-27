package com.renaser.os.chat.application.ports.out.lectura;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;

import java.time.Instant;

/**
 * Empuja «todos leyeron hasta X» a quien esté mirando la conversación, en todas las instancias
 * (D-208). Mismo canal y misma naturaleza que {@code PublicarMensajeFanoutPort}: fire-and-forget, se
 * llama con la lectura ya guardada, y la verdad sigue en Postgres (el listado de mensajes trae la
 * marca de cada uno).
 */
@FunctionalInterface
public interface PublicarLecturaFanoutPort {

    void publicarLectura(ConversacionId conversacionId, Instant leidoPorTodosHasta);
}
