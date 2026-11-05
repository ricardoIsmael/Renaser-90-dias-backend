package com.renaser.os.chat.domain.model.evento;

import com.renaser.os.chat.domain.model.mensaje.MensajeId;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * El recordatorio de un evento como mensaje del programa en el chat de soporte (spec 041). El id del mensaje se calcula de la fila de la cola de
 * recordatorios: una reentrega del *outbox* encuentra el mensaje ya guardado y no lo repite (mismo criterio que la tarjeta del semaforo, D-223, y
 * el pedido de emergencia, D-244). Cada uno de los cuatro avisos de un evento es otra fila de la cola, asi que tiene su propio mensaje.
 */
public record RecordatorioDeEventoEnSoporte(long recordatorioId, String texto) {

    public RecordatorioDeEventoEnSoporte {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El recordatorio del evento necesita un texto");
        }
    }

    public MensajeId idDelMensaje() {
        String clave = "recordatorio-evento|" + recordatorioId;
        return MensajeId.of(UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8)));
    }
}
