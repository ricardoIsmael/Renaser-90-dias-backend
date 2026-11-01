package com.renaser.os.onboarding.domain.model.pagos;

import java.time.Instant;

/**
 * Lo que una persona tiene anotado de su pago (spec 032), guardado en su estado de ingreso: la modalidad, la moneda del cronograma y su confirmación. Las cuotas no están
 * aquí: son filas aparte.
 *
 * <p>Tres situaciones: <b>sin anotar</b> (nadie decidió nada: las cuentas de antes y el personal que entra con «Sin cronograma» por defecto; no se muestra «Mi ingreso»),
 * <b>sin cronograma</b> (pagó completo) y <b>con cronograma</b>. La confirmación es de la persona y se guarda con la huella de lo que confirmó: es vigente mientras esa
 * huella siga siendo la del cronograma ({@link CronogramaDePagos#huella()}).
 *
 * @param confirmadoNombre el nombre que escribió al confirmar; tal cual, sin compararlo con su cuenta
 */
public record PagoDeLaPersona(ModalidadDePago modalidad, Moneda moneda, Instant confirmadoEn, String confirmadoNombre, String confirmadoHuella) {

    public static final int LARGO_MAXIMO_DEL_NOMBRE = 120;

    public PagoDeLaPersona {
        boolean conCronograma = modalidad == ModalidadDePago.CON_CRONOGRAMA;
        if (conCronograma != (moneda != null)) {
            throw new IllegalArgumentException("Un cronograma lleva su moneda y sin cronograma no la lleva");
        }
        boolean confirmado = confirmadoEn != null;
        if (confirmado != (confirmadoNombre != null) || confirmado != (confirmadoHuella != null)) {
            throw new IllegalArgumentException("La confirmación lleva su hora, su nombre y su huella, o ninguno");
        }
        if (confirmado && !conCronograma) {
            throw new IllegalArgumentException("Solo un cronograma se confirma");
        }
    }

    /** Nadie decidió nada todavía. */
    public static PagoDeLaPersona sinAnotar() {
        return new PagoDeLaPersona(null, null, null, null, null);
    }

    /** Pagó completo. */
    public static PagoDeLaPersona sinCronograma() {
        return new PagoDeLaPersona(ModalidadDePago.SIN_CRONOGRAMA, null, null, null, null);
    }

    /** Paga en cuotas, en esa moneda, y todavía no confirmó. */
    public static PagoDeLaPersona conCronograma(Moneda moneda) {
        return new PagoDeLaPersona(ModalidadDePago.CON_CRONOGRAMA, moneda, null, null, null);
    }

    /** ¿Hay algo anotado? Sin eso la app no muestra «Mi ingreso». */
    public boolean anotado() {
        return modalidad != null;
    }

    public boolean esConCronograma() {
        return modalidad == ModalidadDePago.CON_CRONOGRAMA;
    }

    /**
     * Lo que confirmó la persona. El nombre se guarda sin espacios sobrantes.
     *
     * @throws IllegalStateException    (409) si no tiene un cronograma que confirmar
     * @throws IllegalArgumentException (400) si el nombre falta o pasa de {@value #LARGO_MAXIMO_DEL_NOMBRE} letras
     */
    public PagoDeLaPersona confirmado(String nombre, String huella, Instant cuando) {
        if (!esConCronograma()) {
            throw new IllegalStateException("No tienes un cronograma de pagos que confirmar");
        }
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty() || limpio.length() > LARGO_MAXIMO_DEL_NOMBRE) {
            throw new IllegalArgumentException("Escribe tu nombre (hasta " + LARGO_MAXIMO_DEL_NOMBRE + " letras)");
        }
        return new PagoDeLaPersona(modalidad, moneda, cuando, limpio, huella);
    }

    /** Dónde está su confirmación frente al cronograma de ahora ({@code huellaActual}). */
    public EstadoDeConfirmacion confirmacion(String huellaActual) {
        if (confirmadoEn == null) {
            return EstadoDeConfirmacion.SIN_CONFIRMAR;
        }
        return confirmadoHuella.equals(huellaActual) ? EstadoDeConfirmacion.CONFIRMADA : EstadoDeConfirmacion.DESACTUALIZADA;
    }
}
