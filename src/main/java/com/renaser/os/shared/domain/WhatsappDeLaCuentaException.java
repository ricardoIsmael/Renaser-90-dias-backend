package com.renaser.os.shared.domain;

import java.util.Objects;

/**
 * Spec 051: un rechazo de «Mi WhatsApp confirmado» (Yo) que la app reconoce por {@code codigo} sin leer el texto. {@code
 * GlobalExceptionHandler} lo traduce a {@code estado} (409 o 422) con {@code codigo} y {@code message} listo para mostrar
 * (formato {@code ApiErrorConCodigo}). El mensaje nunca lleva el numero ni el codigo.
 */
public class WhatsappDeLaCuentaException extends RuntimeException {

    /** El motivo y el estado HTTP con el que se dice. */
    public enum Motivo {
        /** El canal de WhatsApp no esta configurado (sin clave, numero emisor o plantilla). */
        CANAL_NO_DISPONIBLE(409, "Por ahora no podemos enviar códigos por WhatsApp. Intenta más tarde."),
        /** Lo escrito no tiene forma de numero de WhatsApp. */
        NUMERO_INVALIDO(422, "Escribe un número de WhatsApp válido, con el código de país."),
        /** El proveedor rechazo el envio. */
        CODIGO_NO_SALIO(409, "No pudimos enviar el código por WhatsApp. Intenta de nuevo en unos minutos."),
        /** Ese numero ya es el confirmado de esta misma cuenta. */
        YA_CONFIRMADO(409, "Ese ya es tu WhatsApp confirmado."),
        /** El codigo no coincide, ya vencio o se agotaron los intentos. */
        CODIGO_INCORRECTO(422, "El código no es válido o ya venció. Pide uno nuevo."),
        /** Ese numero ya esta confirmado en otra cuenta. */
        NUMERO_EN_USO(409, "Ese número de WhatsApp ya está en uso en otra cuenta.");

        private final int estado;
        private final String mensaje;

        Motivo(int estado, String mensaje) {
            this.estado = estado;
            this.mensaje = mensaje;
        }

        public int estado() {
            return estado;
        }

        public String mensaje() {
            return mensaje;
        }
    }

    private final Motivo motivo;

    public WhatsappDeLaCuentaException(Motivo motivo) {
        super(Objects.requireNonNull(motivo, "motivo").mensaje());
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
