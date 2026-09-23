package com.renaser.os.rag.application.ports.out.ia;

import java.util.Optional;

/**
 * Convierte un texto corto en audio hablado, para que el orbe de voz de la app hable con una voz
 * natural en vez del TTS del telefono.
 *
 * <p><b>Vacio no es un error:</b> significa "no hay voz del servidor ahora" — no hay proveedor
 * configurado ({@code NoOpVozAdapter}) o el proveedor fallo o tardo demasiado. La app, al recibir
 * eso, habla con el TTS del telefono. Por eso el contrato no lanza: una voz caida nunca puede
 * dejar a la persona sin respuesta.
 *
 * <p>El audio es un WAV completo (cabecera RIFF incluida). Hoy lo implementan
 * {@code PiperVozAdapter} ({@code renaser.ia.voz.proveedor=piper}) y {@code NoOpVozAdapter} (el
 * default).
 *
 * <p><b>Nunca dentro de una transaccion</b> (regla 01, C-1): el adaptador real hace una llamada de
 * red.
 */
public interface SintetizarVozPort {

    Optional<byte[]> sintetizar(String texto);
}
