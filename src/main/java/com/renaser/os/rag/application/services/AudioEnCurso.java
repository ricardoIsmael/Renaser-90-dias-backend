package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.voz.VozDelOrbeUseCase.AudioDelOrbe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * El audio de una oracion mientras se genera (D-159): un hilo le va agregando bytes y quien lo
 * escucha los copia desde el principio, esperando los que faltan. Se puede leer mas de una vez (el
 * reproductor del telefono a veces reintenta) y siempre entrega lo mismo.
 *
 * <p>Nadie espera para siempre: si la generacion se cuelga, la lectura se corta a los
 * {@link #ESPERA_MAXIMA} sin bytes nuevos.
 */
final class AudioEnCurso implements AudioDelOrbe {

    /** Gemini tarda ~1,5 s en el primer sonido; pasado esto, mejor que hable el telefono. */
    static final Duration ESPERA_PRIMER_SONIDO = Duration.ofSeconds(8);
    static final Duration ESPERA_MAXIMA = Duration.ofSeconds(20);

    private final ReentrantLock candado = new ReentrantLock();
    private final Condition cambio = candado.newCondition();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private boolean cerrado;

    void agregar(byte[] parte) {
        candado.lock();
        try {
            bytes.writeBytes(parte);
            cambio.signalAll();
        } finally {
            candado.unlock();
        }
    }

    /** Termino de generarse, bien o mal: ya no llegan mas bytes. */
    void cerrar() {
        candado.lock();
        try {
            cerrado = true;
            cambio.signalAll();
        } finally {
            candado.unlock();
        }
    }

    @Override
    public boolean tieneSonido() throws InterruptedException {
        return esperarPrimerosBytes(ESPERA_PRIMER_SONIDO);
    }

    /** Espera a que haya algo para escuchar. {@code false} si se cerro vacio o no llego nada a tiempo. */
    boolean esperarPrimerosBytes(Duration espera) throws InterruptedException {
        candado.lock();
        try {
            long restante = espera.toNanos();
            while (bytes.size() == 0 && !cerrado && restante > 0) {
                restante = cambio.awaitNanos(restante);
            }
            return bytes.size() > 0;
        } finally {
            candado.unlock();
        }
    }

    /** Copia todo, desde el principio, hasta que se cierre. */
    @Override
    public void escribirEn(OutputStream destino) throws IOException, InterruptedException {
        int enviados = 0;
        while (true) {
            byte[] nuevos = esperarDesde(enviados);
            if (nuevos == null) {
                return;
            }
            destino.write(nuevos);
            destino.flush();
            enviados += nuevos.length;
        }
    }

    /** Los bytes que siguen a {@code desde}; {@code null} si ya no va a llegar nada mas. */
    private byte[] esperarDesde(int desde) throws InterruptedException {
        candado.lock();
        try {
            while (bytes.size() == desde && !cerrado) {
                if (!cambio.await(ESPERA_MAXIMA.toMillis(), TimeUnit.MILLISECONDS)) {
                    return null;
                }
            }
            if (bytes.size() == desde) {
                return null;
            }
            byte[] todo = bytes.toByteArray();
            return Arrays.copyOfRange(todo, desde, todo.length);
        } finally {
            candado.unlock();
        }
    }
}
