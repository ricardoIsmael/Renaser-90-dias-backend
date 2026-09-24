package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RemuestreoDe24a16kHzTest {

    private static byte[] pcm(short... muestras) {
        ByteBuffer buffer = ByteBuffer.allocate(muestras.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short muestra : muestras) {
            buffer.putShort(muestra);
        }
        return buffer.array();
    }

    private static short[] muestras(byte[] pcm) {
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        short[] muestras = new short[pcm.length / 2];
        for (int i = 0; i < muestras.length; i++) {
            muestras[i] = buffer.getShort();
        }
        return muestras;
    }

    /** Un tono de 440 Hz a 24 kHz, amplitud 10000. */
    private static byte[] tono(int cantidad) {
        short[] muestras = new short[cantidad];
        for (int i = 0; i < cantidad; i++) {
            muestras[i] = (short) Math.round(10_000 * Math.sin(2 * Math.PI * 440 * i / 24_000.0));
        }
        return pcm(muestras);
    }

    @Test
    @DisplayName("tres muestras de entrada dan dos de salida")
    void tresADos() {
        byte[] salida = new RemuestreoDe24a16kHz().convertir(tono(2_401));

        // Una muestra de retraso por el filtro: 2400 filtradas -> 1600 de salida.
        assertThat(salida).hasSize(1_600 * 2);
    }

    @Test
    @DisplayName("una senal constante sale constante (el filtro no cambia el volumen)")
    void constante() {
        short[] entrada = new short[301];
        java.util.Arrays.fill(entrada, (short) -1234);

        short[] salida = muestras(new RemuestreoDe24a16kHz().convertir(pcm(entrada)));

        assertThat(salida).isNotEmpty().containsOnly((short) -1234);
    }

    @Test
    @DisplayName("partido en pedazos impares, incluso a mitad de una muestra, da lo mismo que entero: no hay clics")
    void pedazosDanLoMismo() {
        byte[] entero = tono(4_801);
        byte[] deUnaVez = new RemuestreoDe24a16kHz().convertir(entero);

        RemuestreoDe24a16kHz enPedazos = new RemuestreoDe24a16kHz();
        ByteArrayOutputStream juntado = new ByteArrayOutputStream();
        int[] largos = {1, 7, 960, 3, 1919, 2, 41};
        int desde = 0;
        int i = 0;
        while (desde < entero.length) {
            int hasta = Math.min(entero.length, desde + largos[i++ % largos.length]);
            juntado.writeBytes(enPedazos.convertir(java.util.Arrays.copyOfRange(entero, desde, hasta)));
            desde = hasta;
        }

        assertThat(juntado.toByteArray()).isEqualTo(deUnaVez);
    }

    @Test
    @DisplayName("un tono de 440 Hz sigue siendo el mismo tono a 16 kHz")
    void conservaElTono() {
        short[] salida = muestras(new RemuestreoDe24a16kHz().convertir(tono(24_001)));

        // La muestra n de 16 kHz cae en la posicion 1,5·n de 24 kHz; el filtro [1,2,1]/4 atenua el
        // tono en (1 + cos w) / 2, que a 440 Hz es 0,997.
        double w = 2 * Math.PI * 440 / 24_000.0;
        for (int n = 100; n < salida.length - 100; n += 97) {
            double ideal = 10_000 * (1 + Math.cos(w)) / 2 * Math.sin(w * n * 1.5);
            assertThat((double) salida[n]).isCloseTo(ideal, within(400.0));
        }
    }

    @Test
    @DisplayName("un pedazo vacio no rompe ni emite nada")
    void vacio() {
        assertThat(new RemuestreoDe24a16kHz().convertir(new byte[0])).isEmpty();
    }
}
