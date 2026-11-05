package com.renaser.os.notifications.domain.model.whatsapp;

import java.util.Objects;

/**
 * Lo que devolvio el proveedor al enviar un mensaje (spec 041). El WhatsApp es un canal extra: <b>no se reintenta nada</b> (FR-023), ni un 429.
 * Un 5xx o un corte son {@link EstadoDeEntrega#NO_SE_SUPO}: el mensaje pudo salir, y volver a mandarlo repetiria un mensaje de pago.
 *
 * @param motivo texto corto del proveedor, sin el cuerpo del mensaje ni el numero (el motivo se guarda y se escribe en el registro)
 */
public record ResultadoDelEnvio(EstadoDeEntrega estado, String proveedorId, String wamid, String motivo, CausaDeFalla causa) {

    public static final int LARGO_MAXIMO_DEL_MOTIVO = 200;

    public ResultadoDelEnvio {
        Objects.requireNonNull(estado, "estado es obligatorio");
        Objects.requireNonNull(causa, "causa es obligatoria");
        motivo = acortar(motivo);
    }

    public static ResultadoDelEnvio aceptado(String proveedorId, String wamid) {
        return new ResultadoDelEnvio(EstadoDeEntrega.ACEPTADO, proveedorId, wamid, null, CausaDeFalla.NINGUNA);
    }

    public static ResultadoDelEnvio fallido(CausaDeFalla causa, String motivo) {
        return new ResultadoDelEnvio(EstadoDeEntrega.FALLIDO, null, null, motivo, causa);
    }

    public static ResultadoDelEnvio noSeSupo(String motivo) {
        return new ResultadoDelEnvio(EstadoDeEntrega.NO_SE_SUPO, null, null, motivo, CausaDeFalla.OTRA);
    }

    /** El proveedor contesto, pero WhatsApp no lo acepto («failed» en la misma respuesta). */
    public static ResultadoDelEnvio rechazado(String proveedorId, String wamid, CausaDeFalla causa, String motivo) {
        return new ResultadoDelEnvio(EstadoDeEntrega.FALLIDO, proveedorId, wamid, motivo, causa);
    }

    private static String acortar(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return null;
        }
        // Un error del proveedor puede repetir el numero ("el numero +51 987 654 321 no es valido"): el numero no se guarda en ningun lado.
        String limpio = motivo.strip().replaceAll("\\s+", " ").replaceAll("\\+?\\d[\\d -]{6,}\\d", "[número]");
        return limpio.length() > LARGO_MAXIMO_DEL_MOTIVO ? limpio.substring(0, LARGO_MAXIMO_DEL_MOTIVO - 1) + "…" : limpio;
    }
}
