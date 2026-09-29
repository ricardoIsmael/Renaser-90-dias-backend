package com.renaser.os.chat.domain.model.semaforo;

/**
 * Qué tarjeta del semáforo sale (D-223): una por color, diseño de Operaciones en Canva. No hay tarjeta de
 * «sin datos»: un día sin nada programado no manda nada.
 *
 * <p>El color NO se decide acá: llega ya resuelto por el semáforo ({@code points.ReglaDelSemaforo}, verde
 * ≥ 80, amarillo ≥ 60, rojo por debajo). Este enum solo nombra qué imagen le corresponde.
 */
public enum ColorDeTarjeta {

    VERDE("verde"),
    AMARILLO("amarillo"),
    ROJO("rojo");

    /**
     * La versión del diseño. Va en la ruta del almacenamiento, que se sube una sola vez y la reusan todos los
     * mensajes: si Operaciones cambia una tarjeta, se sube como {@code v2} y los mensajes viejos siguen
     * mostrando la que recibieron.
     */
    static final String VERSION = "v1";

    private final String nombre;

    ColorDeTarjeta(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre del archivo en los recursos de la aplicación: {@code semaforo/tarjetas/verde.jpg}. */
    public String recurso() {
        return "semaforo/tarjetas/" + nombre + ".jpg";
    }

    /** Dónde vive en el almacenamiento: la misma ruta para todos los aprendices y todas las noches. */
    public String rutaEnAlmacenamiento() {
        return "semaforo/tarjetas/" + nombre + "-" + VERSION + ".jpg";
    }
}
