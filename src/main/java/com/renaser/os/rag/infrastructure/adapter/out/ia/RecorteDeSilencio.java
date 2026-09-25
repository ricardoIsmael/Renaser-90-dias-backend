package com.renaser.os.rag.infrastructure.adapter.out.ia;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Quita el silencio de sobra al principio y al final de un clip PCM de 16 bits mono, mientras llega
 * por partes (E-232).
 *
 * <p>Gemini deja ~0,2 s de silencio al empezar y ~0,3 s al terminar cada clip. Con una oracion por
 * clip, cada punto sonaba como una pausa de casi un segundo. Se deja {@link #RESPIRO_MS} a cada lado
 * para que no suene cortado.
 *
 * <p>Principio: se descarta hasta la primera muestra audible. Final: se retiene la ultima porcion
 * ({@link #RETENCION_MS}), porque todavia no se sabe si es silencio final o una pausa entre palabras,
 * y al terminar se recorta lo que sobre.
 */
final class RecorteDeSilencio {

    static final int UMBRAL = 600;
    static final int RESPIRO_MS = 60;
    static final int RETENCION_MS = 600;

    private final int bytesDeRespiro;
    private final int bytesDeRetencion;
    private final ByteArrayOutputStream retenido = new ByteArrayOutputStream();
    private boolean empezoElSonido;

    RecorteDeSilencio(int muestrasPorSegundo) {
        int bytesPorMs = muestrasPorSegundo * 2 / 1000;
        this.bytesDeRespiro = RESPIRO_MS * bytesPorMs;
        this.bytesDeRetencion = RETENCION_MS * bytesPorMs;
    }

    /** Lo que ya se puede entregar despues de sumar {@code pcm}; puede ser vacio. */
    byte[] agregar(byte[] pcm) {
        retenido.writeBytes(pcm);
        byte[] todo = retenido.toByteArray();
        int desde = 0;
        if (!empezoElSonido) {
            int primera = primeraAudible(todo);
            if (primera < 0) {
                // Todo silencio hasta ahora: se guarda solo el respiro, lo anterior sobra.
                recortarA(Arrays.copyOfRange(todo, Math.max(0, pares(todo.length - bytesDeRespiro)), todo.length));
                return new byte[0];
            }
            empezoElSonido = true;
            desde = pares(Math.max(0, primera - bytesDeRespiro));
        }
        int hasta = pares(Math.max(desde, todo.length - bytesDeRetencion));
        recortarA(Arrays.copyOfRange(todo, hasta, todo.length));
        return Arrays.copyOfRange(todo, desde, hasta);
    }

    /** Lo que quedaba retenido, sin el silencio del final. */
    byte[] terminar() {
        byte[] todo = retenido.toByteArray();
        retenido.reset();
        if (!empezoElSonido) {
            return new byte[0];
        }
        int ultima = ultimaAudible(todo);
        int hasta = pares(Math.min(todo.length, ultima + 2 + bytesDeRespiro));
        return ultima < 0 ? new byte[0] : Arrays.copyOfRange(todo, 0, hasta);
    }

    private void recortarA(byte[] resto) {
        retenido.reset();
        retenido.writeBytes(resto);
    }

    private static int primeraAudible(byte[] pcm) {
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            if (Math.abs(muestra(pcm, i)) > UMBRAL) {
                return i;
            }
        }
        return -1;
    }

    private static int ultimaAudible(byte[] pcm) {
        for (int i = pares(pcm.length - 2); i >= 0; i -= 2) {
            if (Math.abs(muestra(pcm, i)) > UMBRAL) {
                return i;
            }
        }
        return -1;
    }

    private static int muestra(byte[] pcm, int i) {
        return (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
    }

    /** Siempre en limite de muestra (2 bytes): cortar a la mitad de una muestra suena a chasquido. */
    private static int pares(int bytes) {
        return bytes & ~1;
    }
}
