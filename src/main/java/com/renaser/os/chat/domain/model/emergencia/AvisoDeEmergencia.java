package com.renaser.os.chat.domain.model.emergencia;

import com.renaser.os.chat.domain.model.mensaje.MensajeId;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * El mensaje que deja en el chat de soporte el pedido de emergencia de un aprendiz (D-244). Lo escribe el
 * programa («Formación Renaser»), con el resumen que pidió el dueño: «Emergencia: … Pide volver al día N (hoy
 * está en el día M)». Pedido en el Día 0 (sin día): «Emergencia: … / Pide ayuda (todavía está en el día 0).»
 *
 * <p>El id del mensaje se calcula del pedido: una reentrega del outbox encuentra el mensaje ya guardado y no lo
 * repite (mismo criterio que la tarjeta del semáforo, D-223).
 */
public record AvisoDeEmergencia(UUID solicitudId, String queOcurrio, Integer diaPedido, int diaAlPedir) {

    public AvisoDeEmergencia {
        Objects.requireNonNull(solicitudId, "solicitudId es obligatorio");
        if (queOcurrio == null || queOcurrio.isBlank()) {
            throw new IllegalArgumentException("El aviso de emergencia necesita lo que pasó");
        }
    }

    public String texto() {
        String pedido = diaPedido == null
                ? "Pide ayuda (todavía está en el día " + diaAlPedir + ")."
                : "Pide volver al día " + diaPedido + " (hoy está en el día " + diaAlPedir + ").";
        return "Emergencia: " + conPunto(queOcurrio.strip()) + "\n" + pedido;
    }

    /**
     * Lo que escribió la persona suele terminar sin punto, y con el texto justo al borde de la burbuja el
     * salto de línea no se distingue: se leía «…sin poder seguir Pide volver al día 12» (e2e 02/10).
     */
    private static String conPunto(String texto) {
        return texto.matches("(?s).*[.!?…)]$") ? texto : texto + ".";
    }

    public MensajeId idDelMensaje() {
        String clave = "emergencia|" + solicitudId;
        return MensajeId.of(UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8)));
    }
}
