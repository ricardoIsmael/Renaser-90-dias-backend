package com.renaser.os.rag.application.ports.in.voz;

import com.renaser.os.shared.domain.UserId;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * La voz del orbe de la app, en dos pasos (D-159):
 * <ol>
 *   <li>{@link #preparar}: "decime esto en voz alta". Empieza a generar el audio y devuelve con que
 *   id pedirlo; vacio si no hay voz del servidor (la app usa el TTS del telefono).</li>
 *   <li>{@link #buscar}: el audio de ese id, para escucharlo mientras se genera. El reproductor del
 *   telefono solo sabe bajar una URL, por eso no puede ir todo en un solo pedido.</li>
 * </ol>
 *
 * <p>Solo una cuenta activa puede usarlo ({@code NotAuthorizedException} si no), y solo su dueno
 * encuentra el audio. El texto va recortado, no vacio y de a lo sumo {@link #LARGO_MAXIMO_TEXTO}
 * caracteres ({@code IllegalArgumentException} si no): la app manda de a una oracion.
 *
 * <p>Corregido 2026-09-23: era {@code SintetizarVozUseCase}, que devolvia el WAV entero (D-157).
 */
public interface VozDelOrbeUseCase {

    int LARGO_MAXIMO_TEXTO = 400;

    Optional<UUID> preparar(UserId actorId, String texto);

    /** Vacio si el id no existe, ya vencio o es de otra persona (no se distingue cual). */
    Optional<AudioDelOrbe> buscar(UserId actorId, UUID id);

    /** El audio de una oracion, visto desde quien lo escucha. */
    interface AudioDelOrbe {

        /** Espera a que haya sonido. {@code false} si la generacion fallo antes de producir nada. */
        boolean tieneSonido() throws InterruptedException;

        /** Escribe el WAV desde el principio, siguiendo lo que se genere, hasta que termine. */
        void escribirEn(OutputStream destino) throws IOException, InterruptedException;
    }
}
