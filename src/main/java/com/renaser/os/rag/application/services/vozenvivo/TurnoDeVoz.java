package com.renaser.os.rag.application.services.vozenvivo;

import java.time.Instant;

/**
 * Lo que se va diciendo en un turno de voz, hasta que el modelo lo da por terminado: la
 * transcripcion de la persona ({@code oido}), la del acompanante ({@code dicho}) y lo que se agrega
 * al mensaje guardado sin haberse dicho (el resumen de una propuesta).
 *
 * <p>No es seguro entre hilos: lo protege {@link SesionDeVozEnVivo}.
 */
final class TurnoDeVoz {

    private final StringBuilder oido = new StringBuilder();
    private final StringBuilder dicho = new StringBuilder();
    private final StringBuilder anexos = new StringBuilder();
    private Instant inicio;
    private boolean huboHerramienta;

    void oir(String texto, Instant ahora) {
        if (inicio == null) {
            inicio = ahora;
        }
        oido.append(texto);
    }

    void decir(String texto, Instant ahora) {
        if (inicio == null) {
            inicio = ahora;
        }
        dicho.append(texto);
    }

    /**
     * Queda al final del mensaje guardado del acompanante, aunque haya pasado a mitad del turno (como
     * en el chat, donde las propuestas van antes del {@code fin}). No se le manda a la app como
     * {@code dicho}: no se dijo.
     */
    void anexar(String texto) {
        anexos.append(texto);
    }

    void marcarHerramienta() {
        huboHerramienta = true;
    }

    /**
     * El modelo pidio una herramienta y todavia no dijo nada: el {@code turnComplete} que manda
     * Gemini en ese momento no es el fin de la respuesta (verificado con la API real el 2026-09-24:
     * llegan dos, uno tras el pedido y otro tras hablar).
     */
    boolean esperandoRespuesta() {
        return huboHerramienta && dicho.toString().isBlank();
    }

    /** El acompanante ya dijo algo en este turno. */
    boolean yaRespondio() {
        return !dicho.toString().isBlank();
    }

    boolean vacio() {
        return oido.toString().isBlank() && respuesta().isBlank();
    }

    String oido() {
        return oido.toString().strip();
    }

    /** Lo que dijo el acompanante y, al final, lo anexado. */
    String respuesta() {
        return (dicho.toString().strip() + anexos).strip();
    }

    /** Cuando empezo; {@code null} si todavia no se dijo nada. */
    Instant inicio() {
        return inicio;
    }
}
