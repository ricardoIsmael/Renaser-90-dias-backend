package com.renaser.os.notifications.domain.model.whatsapp;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Como va un recordatorio por WhatsApp (spec 041, FR-022). Es el vocabulario de la columna {@code estado} de {@code entregas_whatsapp}.
 *
 * <p><b>El estado solo avanza.</b> Un aviso tardio del proveedor («enviado» despues de «leido») no deshace nada: {@link #puedeAvanzarA} lo dice.
 * {@link #NO_SE_SUPO} (un corte o un 5xx: pudo salir) se resuelve si despues llega el estado del proveedor. {@link #OMITIDO} y
 * {@link #FALLIDO} son finales.
 */
public enum EstadoDeEntrega {

    /** Apartada: la fila existe y la llamada al proveedor esta en curso (o el servidor cayo en medio). Nunca se reenvia sola. */
    ENVIANDO,
    /** Se llamo y no se supo como termino (corte, tiempo agotado, 5xx): pudo haber salido. No se reintenta. */
    NO_SE_SUPO,
    ACEPTADO,
    ENVIADO,
    ENTREGADO,
    LEIDO,
    FALLIDO,
    /** No se envio, con el motivo: sin numero, llego tarde, el evento ya empezo, tope mensual. */
    OMITIDO;

    /** Desde que estados se puede pasar a {@code nuevo} (nunca hacia atras, nunca desde un estado final). */
    public Set<EstadoDeEntrega> desdeLosQuePuedeAvanzarA() {
        return switch (this) {
            case ENVIANDO, OMITIDO -> EnumSet.noneOf(EstadoDeEntrega.class);
            case NO_SE_SUPO -> EnumSet.of(ENVIANDO);
            case ACEPTADO -> EnumSet.of(ENVIANDO, NO_SE_SUPO);
            case ENVIADO -> EnumSet.of(ENVIANDO, NO_SE_SUPO, ACEPTADO);
            case ENTREGADO -> EnumSet.of(ENVIANDO, NO_SE_SUPO, ACEPTADO, ENVIADO);
            case LEIDO -> EnumSet.of(ENVIANDO, NO_SE_SUPO, ACEPTADO, ENVIADO, ENTREGADO);
            case FALLIDO -> EnumSet.of(ENVIANDO, NO_SE_SUPO, ACEPTADO, ENVIADO);
        };
    }

    public boolean puedeAvanzarA(EstadoDeEntrega nuevo) {
        return nuevo.desdeLosQuePuedeAvanzarA().contains(this);
    }

    /** El estado que dice el proveedor en su aviso de estado («accepted», «sent», «delivered», «read», «failed»); vacio si no lo conocemos. */
    public static java.util.Optional<EstadoDeEntrega> delProveedor(String estado) {
        if (estado == null) {
            return java.util.Optional.empty();
        }
        return switch (estado.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "accepted" -> java.util.Optional.of(ACEPTADO);
            case "sent" -> java.util.Optional.of(ENVIADO);
            case "delivered" -> java.util.Optional.of(ENTREGADO);
            case "read" -> java.util.Optional.of(LEIDO);
            case "failed" -> java.util.Optional.of(FALLIDO);
            default -> java.util.Optional.empty();
        };
    }

    /** Los estados que cuentan como «enviado» para el tope mensual y el resumen: todos menos los que no salieron. */
    public static Set<EstadoDeEntrega> queSalieron() {
        Set<EstadoDeEntrega> salieron = EnumSet.copyOf(Arrays.asList(values()));
        salieron.remove(OMITIDO);
        salieron.remove(FALLIDO);
        return salieron;
    }
}
