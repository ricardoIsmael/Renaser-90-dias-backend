package com.renaser.os.chat.domain.model.emergencia;

import com.renaser.os.chat.domain.model.mensaje.MensajeId;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * El mensaje que deja en el chat de soporte el pedido de emergencia de un aprendiz (D-244). Lo escribe el
 * programa («Formación Renaser»), con el resumen que pidió el dueño: «Emergencia: … Pide volver al día N (hoy
 * está en el día M)».
 *
 * <p>El id del mensaje se calcula del pedido: una reentrega del outbox encuentra el mensaje ya guardado y no lo
 * repite (mismo criterio que la tarjeta del semáforo, D-223).
 */
public record AvisoDeEmergencia(UUID solicitudId, String queOcurrio, int diaPedido, int diaAlPedir) {

    public AvisoDeEmergencia {
        Objects.requireNonNull(solicitudId, "solicitudId es obligatorio");
        if (queOcurrio == null || queOcurrio.isBlank()) {
            throw new IllegalArgumentException("El aviso de emergencia necesita lo que pasó");
        }
    }

    public String texto() {
        return "Emergencia: " + queOcurrio.strip() + "\nPide volver al día " + diaPedido + " (hoy está en el día "
                + diaAlPedir + ").";
    }

    public MensajeId idDelMensaje() {
        String clave = "emergencia|" + solicitudId;
        return MensajeId.of(UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8)));
    }
}
