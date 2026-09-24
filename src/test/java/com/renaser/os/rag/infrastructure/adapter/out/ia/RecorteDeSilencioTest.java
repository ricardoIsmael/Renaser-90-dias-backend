package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-232: cada clip de Gemini trae ~0,2 s de silencio al empezar y ~0,3 s al terminar; entre oracion y
 * oracion eso sonaba como un corte. Se recorta al vuelo dejando {@link RecorteDeSilencio#RESPIRO_MS}.
 */
class RecorteDeSilencioTest {

    private static final int HZ = 24_000;

    private static byte[] tramo(int ms, int amplitud) {
        int muestras = HZ * ms / 1000;
        ByteBuffer pcm = ByteBuffer.allocate(muestras * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < muestras; i++) {
            pcm.putShort((short) (i % 2 == 0 ? amplitud : -amplitud));
        }
        return pcm.array();
    }

    private static int ms(int bytes) {
        return bytes * 1000 / (HZ * 2);
    }

    /** Lo pasa por el recorte en pedazos del tamano dado, como llegan de Gemini. */
    private static byte[] recortar(byte[] clip, int pedazo) {
        RecorteDeSilencio recorte = new RecorteDeSilencio(HZ);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        for (int i = 0; i < clip.length; i += pedazo) {
            byte[] parte = Arrays.copyOfRange(clip, i, Math.min(clip.length, i + pedazo));
            salida.writeBytes(recorte.agregar(parte));
        }
        salida.writeBytes(recorte.terminar());
        return salida.toByteArray();
    }

    private static byte[] unir(byte[]... partes) {
        ByteArrayOutputStream todo = new ByteArrayOutputStream();
        for (byte[] parte : partes) {
            todo.writeBytes(parte);
        }
        return todo.toByteArray();
    }

    @Test
    @DisplayName("300 ms de silencio + 500 ms de voz + 400 ms de silencio quedan en 60 + 500 + 60")
    void recortaPrincipioYFinal() {
        byte[] clip = unir(tramo(300, 0), tramo(500, 5000), tramo(400, 0));

        assertThat(ms(recortar(clip, 4_801).length)).isBetween(615, 625);
    }

    @Test
    @DisplayName("las pausas entre palabras se respetan: solo se recorta lo de los bordes")
    void respetaLasPausasDelMedio() {
        byte[] clip = unir(tramo(200, 0), tramo(300, 5000), tramo(250, 0), tramo(300, 5000), tramo(300, 0));

        assertThat(ms(recortar(clip, 3_000).length)).isBetween(965, 975);
    }

    @Test
    @DisplayName("el resultado no depende de como llegan los pedazos, y nunca corta una muestra a la mitad")
    void noDependeDeLosPedazos() {
        byte[] clip = unir(tramo(250, 0), tramo(400, 7000), tramo(350, 0));

        byte[] enPedazosChicos = recortar(clip, 333);
        assertThat(enPedazosChicos).isEqualTo(recortar(clip, 50_000));
        assertThat(enPedazosChicos.length % 2).isZero();
    }

    @Test
    @DisplayName("un clip todo silencio no entrega nada")
    void todoSilencio() {
        assertThat(recortar(tramo(500, 0), 1_000)).isEmpty();
    }
}
