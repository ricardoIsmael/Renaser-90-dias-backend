package com.renaser.os.rag.infrastructure.adapter.in.websocket;

import java.io.ByteArrayOutputStream;

/**
 * Junta los pedazos de un frame de WebSocket que llega partido (E-234): Tomcat entrega de a 8 KB
 * cuando el handler acepta mensajes parciales. Uno por conexion; la conexion entrega de a un frame
 * por vez, asi que no necesita sincronizacion.
 *
 * <p>Con tope: un cliente que manda un frame sin fin no puede llenar la memoria del servidor. Lo que
 * pasa del tope se descarta entero.
 */
final class FramesPartidos {

    /** ~32 s de audio a 16 kHz; la app manda pedazos de ~100 ms. */
    static final int TOPE = 1024 * 1024;

    private final ByteArrayOutputStream binario = new ByteArrayOutputStream();
    private final StringBuilder texto = new StringBuilder();
    private boolean descartando;

    /** El frame completo, o {@code null} si todavia faltan pedazos (o si se paso del tope). */
    byte[] juntarBinario(byte[] pedazo, boolean ultimo) {
        if (binario.size() == 0 && ultimo && !descartando) {
            return pedazo;
        }
        if (binario.size() + pedazo.length > TOPE) {
            descartando = true;
            binario.reset();
        }
        if (!descartando) {
            binario.writeBytes(pedazo);
        }
        if (!ultimo) {
            return null;
        }
        byte[] completo = descartando ? null : binario.toByteArray();
        binario.reset();
        descartando = false;
        return completo;
    }

    /** Igual que {@link #juntarBinario}, para los frames de texto (el unico que se espera es {@code fin}). */
    String juntarTexto(String pedazo, boolean ultimo) {
        if (texto.isEmpty() && ultimo) {
            return pedazo;
        }
        if (texto.length() + pedazo.length() <= TOPE) {
            texto.append(pedazo);
        }
        if (!ultimo) {
            return null;
        }
        String completo = texto.toString();
        texto.setLength(0);
        return completo;
    }
}
