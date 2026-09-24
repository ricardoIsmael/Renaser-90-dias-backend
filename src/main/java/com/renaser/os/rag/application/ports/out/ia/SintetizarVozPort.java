package com.renaser.os.rag.application.ports.out.ia;

import java.util.function.Consumer;

/**
 * Convierte un texto corto en audio hablado, para que el orbe de voz de la app hable con una voz
 * natural en vez del TTS del telefono.
 *
 * <p>El audio sale <b>por partes</b>, a medida que se genera (D-159): primero la cabecera WAV y
 * despues los bytes de sonido. Asi la app puede empezar a escucharlo antes de que termine de
 * generarse. Un proveedor que no transmite (Piper) entrega el WAV entero de una vez.
 *
 * <p><b>Nunca lanza:</b> devuelve {@code false} si no hubo voz o se corto a la mitad. Una voz
 * caida nunca puede dejar a la persona sin respuesta: la app habla con el TTS del telefono.
 *
 * <p>Lo implementan {@code GeminiVozAdapter} ({@code renaser.ia.voz.proveedor=gemini}),
 * {@code PiperVozAdapter} ({@code piper}) y {@code NoOpVozAdapter} (el default).
 *
 * <p><b>Nunca dentro de una transaccion</b> (regla 01, C-1): el adaptador real hace una llamada de
 * red que dura varios segundos.
 *
 * <p>Corregido 2026-09-23: la primera version (D-157) devolvia {@code Optional<byte[]>} con el WAV
 * completo. Con Gemini, esperar el audio entero tardaba 4 a 7 s por frase.
 */
public interface SintetizarVozPort {

    /** {@code false} si no hay proveedor configurado: ni se intenta, la app usa su propia voz. */
    boolean disponible();

    /** Bloquea hasta terminar. {@code true} si el audio salio completo. */
    boolean sintetizar(String texto, Consumer<byte[]> destino);
}
