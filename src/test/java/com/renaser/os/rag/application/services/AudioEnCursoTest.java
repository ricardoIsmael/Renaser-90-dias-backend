package com.renaser.os.rag.application.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AudioEnCursoTest {

    private final AudioEnCurso audio = new AudioEnCurso();

    private CompletableFuture<byte[]> escucharEnOtroHilo() {
        return CompletableFuture.supplyAsync(() -> {
            ByteArrayOutputStream escuchado = new ByteArrayOutputStream();
            try {
                audio.escribirEn(escuchado);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return escuchado.toByteArray();
        });
    }

    @Test
    @DisplayName("quien escucha recibe lo que ya habia y lo que llega despues, hasta que se cierra")
    void escuchaMientrasSeGenera() throws Exception {
        audio.agregar(new byte[] {1, 2});
        CompletableFuture<byte[]> escuchado = escucharEnOtroHilo();

        Thread.sleep(50);
        audio.agregar(new byte[] {3});
        audio.agregar(new byte[] {4, 5});
        audio.cerrar();

        assertThat(escuchado.get(5, TimeUnit.SECONDS)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("se puede escuchar mas de una vez y siempre empieza desde el principio")
    void segundaLecturaEntregaLoMismo() throws Exception {
        audio.agregar(new byte[] {7, 8});
        audio.cerrar();

        assertThat(escucharEnOtroHilo().get(5, TimeUnit.SECONDS)).containsExactly(7, 8);
        assertThat(escucharEnOtroHilo().get(5, TimeUnit.SECONDS)).containsExactly(7, 8);
    }

    @Test
    @DisplayName("hay sonido apenas llega el primer byte, aunque la generacion siga")
    void tieneSonidoConElPrimerByte() throws Exception {
        CompletableFuture.runAsync(() -> audio.agregar(new byte[] {1}), CompletableFuture.delayedExecutor(50,
                TimeUnit.MILLISECONDS));

        assertThat(audio.esperarPrimerosBytes(Duration.ofSeconds(5))).isTrue();
    }

    @Test
    @DisplayName("si la generacion falla sin producir nada, no hay sonido y no se espera de mas")
    void cerradoVacioNoTieneSonido() throws Exception {
        audio.cerrar();

        assertThat(audio.tieneSonido()).isFalse();
    }

    @Test
    @DisplayName("si no llega nada a tiempo, no hay sonido")
    void sinBytesATiempoNoHaySonido() throws Exception {
        assertThat(audio.esperarPrimerosBytes(Duration.ofMillis(50))).isFalse();
    }
}
