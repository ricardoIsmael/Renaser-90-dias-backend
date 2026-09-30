package com.renaser.os.rag.application.services.vozenvivo;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

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
    private Instant ultimoOido;
    private boolean sono;
    /** Pidio una herramienta y todavia no hablo despues de ella. */
    private boolean herramientaSinRespuesta;

    void oir(String texto, Instant ahora) {
        if (inicio == null) {
            inicio = ahora;
        }
        ultimoOido = ahora;
        oido.append(texto);
    }

    /**
     * El primer audio del acompanante en este turno: cuanto tardo desde lo ultimo que se oyo de la
     * persona (E-458, para medir la espera en los logs). Vacio si no es el primero o si no se oyo nada.
     */
    Optional<Duration> sonar(Instant ahora) {
        if (sono) {
            return Optional.empty();
        }
        sono = true;
        return ultimoOido == null ? Optional.empty()
                : Optional.of(Duration.between(ultimoOido, ahora));
    }

    void decir(String texto, Instant ahora) {
        if (inicio == null) {
            inicio = ahora;
        }
        dicho.append(texto);
        if (!texto.isBlank()) {
            herramientaSinRespuesta = false;
        }
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
        herramientaSinRespuesta = true;
    }

    /**
     * El modelo pidio una herramienta y todavia no hablo despues de ella: el {@code turnComplete} que
     * manda Gemini en ese momento no es el fin de la respuesta (verificado con la API real el
     * 2026-09-24: llegan dos, uno tras el pedido y otro tras hablar).
     *
     * <p>Corregido 2026-09-30 (E-458): decia "pidio una herramienta y no dijo nada en todo el turno".
     * Desde que el acompanante avisa con una frase corta antes de consultar ("Dejame revisar"), ya
     * habia dicho algo, y el turno se cerraba con la frase de aviso sola.
     */
    boolean esperandoRespuesta() {
        return herramientaSinRespuesta;
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
