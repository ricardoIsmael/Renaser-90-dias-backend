package com.renaser.os.rag.infrastructure.adapter.out.ia;

import java.io.ByteArrayOutputStream;

/**
 * Baja a 16 kHz el PCM de 16 bits mono a 24 kHz que devuelve Gemini Live (D-162), para que la app
 * reciba el mismo formato que graba.
 *
 * <p><b>Como.</b> 24 → 16 kHz es 3 → 2. Primero un paso bajo de tres puntos {@code [1, 2, 1] / 4}
 * (anula lo que queda por encima de la nueva frecuencia de Nyquist, que si no volveria como ruido
 * metalico) y despues interpolacion lineal en las posiciones 0 y 1,5 de cada grupo de tres
 * muestras: la primera sale tal cual y la segunda es el promedio de las dos siguientes.
 *
 * <p><b>Sin clics entre pedazos.</b> Gemini manda el audio en pedazos de ~40 ms, y un pedazo puede
 * terminar a mitad de un grupo o incluso a mitad de una muestra (un byte suelto). Todo el estado —las
 * dos ultimas muestras, en que punto del grupo se quedo, el byte suelto— pasa de un pedazo al
 * siguiente, asi que convertir el audio en pedazos da exactamente lo mismo que convertirlo entero.
 *
 * <p>Una instancia por sesion: no es segura entre hilos (el adaptador la usa desde un solo hilo).
 */
final class RemuestreoDe24a16kHz {

    private int byteSuelto = -1;
    private int muestrasVistas;
    private int anterior;
    private int actual;
    private int posicionEnGrupo;
    private int pendiente;

    byte[] convertir(byte[] pcm24kHz) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream(pcm24kHz.length * 2 / 3 + 2);
        int i = 0;
        if (byteSuelto >= 0 && pcm24kHz.length > 0) {
            recibir(muestra(byteSuelto, pcm24kHz[0]), salida);
            byteSuelto = -1;
            i = 1;
        }
        for (; i + 1 < pcm24kHz.length; i += 2) {
            recibir(muestra(pcm24kHz[i], pcm24kHz[i + 1]), salida);
        }
        if (i < pcm24kHz.length) {
            byteSuelto = pcm24kHz[i] & 0xFF;
        }
        return salida.toByteArray();
    }

    /** Filtra con una muestra de retraso: la salida filtrada de {@code actual} necesita la siguiente. */
    private void recibir(int nueva, ByteArrayOutputStream salida) {
        if (muestrasVistas == 0) {
            anterior = nueva;
            actual = nueva;
        } else {
            emitirFiltrada((anterior + 2 * actual + nueva) / 4, salida);
            anterior = actual;
            actual = nueva;
        }
        muestrasVistas = Math.min(muestrasVistas + 1, 2);
    }

    private void emitirFiltrada(int filtrada, ByteArrayOutputStream salida) {
        switch (posicionEnGrupo) {
            case 0 -> escribir(filtrada, salida);
            case 1 -> pendiente = filtrada;
            default -> escribir((pendiente + filtrada) / 2, salida);
        }
        posicionEnGrupo = (posicionEnGrupo + 1) % 3;
    }

    private static int muestra(int bajo, byte alto) {
        return (short) ((bajo & 0xFF) | (alto << 8));
    }

    private static void escribir(int muestra, ByteArrayOutputStream salida) {
        salida.write(muestra & 0xFF);
        salida.write((muestra >> 8) & 0xFF);
    }
}
