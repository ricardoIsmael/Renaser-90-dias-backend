package com.renaser.os.chat.domain.model.emergencia;

import com.renaser.os.chat.domain.model.mensaje.MensajeId;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que el programa le escribe a la persona en su chat de soporte cuando quien atiende resuelve su pedido de
 * emergencia (D-244, pedido del dueño del 2026-10-02). Firmado «Formación Renaser».
 *
 * <p>El id del mensaje sale del pedido: un pedido se resuelve una sola vez, y una reentrega del outbox no
 * repite el mensaje.
 *
 * @param diaAplicado el día al que se la llevó; {@code null} si se cerró sin cambiar el día
 * @param diaActual   el día que vive ahora
 */
public record RespuestaAEmergencia(UUID solicitudId, Integer diaAplicado, int diaActual) {

    public RespuestaAEmergencia {
        Objects.requireNonNull(solicitudId, "solicitudId es obligatorio");
    }

    public String texto() {
        if (diaAplicado != null) {
            return "Listo: volviste al día " + diaAplicado + ". Si necesitas algo más, escríbenos aquí.";
        }
        return "Revisamos tu pedido; seguimos en el día " + diaActual + ". Escríbenos si necesitas algo.";
    }

    public MensajeId idDelMensaje() {
        String clave = "emergencia-resuelta|" + solicitudId;
        return MensajeId.of(UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8)));
    }
}
