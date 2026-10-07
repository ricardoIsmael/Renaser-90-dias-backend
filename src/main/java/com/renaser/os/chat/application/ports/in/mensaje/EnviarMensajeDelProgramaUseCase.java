package com.renaser.os.chat.application.ports.in.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Objects;

/**
 * Un mensaje que escribe el PROGRAMA y no una persona (D-199/D-204): las bienvenidas. Se guarda como
 * SISTEMA y hacia afuera lo firma «Formación Renaser» ({@link Mensaje#remitentePublico}).
 *
 * <p>No es un {@link EnviarMensajeUseCase} sin actor: no hay nadie que tenga que estar activo ni ser
 * participante, y no se marca leído a nadie (es nuevo para todos los que están en el chat). Lo usa
 * solo código del servidor; ningún endpoint llega acá.
 */
public interface EnviarMensajeDelProgramaUseCase {

    /**
     * Guarda el mensaje (en la transacción de quien llama, si hay una) y lo empuja en vivo después
     * del commit.
     *
     * @param sobreQuien la persona a quien se refiere; queda en {@code emisor_id} (ver {@link Mensaje})
     * @throws java.util.NoSuchElementException si la conversación no existe
     */
    Mensaje enviarDelPrograma(ConversacionId conversacionId, UserId sobreQuien, ContenidoDelPrograma contenido);

    /**
     * Manda las piezas cuyo id todavía no existe, en ESE orden y en una transacción (D-223, la tarjeta
     * diaria del semáforo). Los ids los calcula quien llama: una pieza que ya salió no se vuelve a guardar,
     * ni a empujar en vivo, ni a avisar. Así un barrido puede correr dos veces, o tarde, sin duplicar.
     *
     * @return cuántas piezas salieron ahora (0 = ya estaban todas)
     * @throws java.util.NoSuchElementException si la conversación no existe
     */
    int enviarUnaVez(EntregaDelPrograma entrega);

    /** A quién le llega el push de una pieza (D-221/D-223). */
    enum AvisoDeLaPieza {
        /** A todos los que ven el chat, como cualquier mensaje del programa. */
        A_TODOS,
        /** Solo a la persona de quien habla el mensaje: el staff del soporte no recibe push. */
        SOLO_A_QUIEN_SE_REFIERE,
        /** A nadie: la pieza acompaña a otra que sí avisa (la imagen de la tarjeta). */
        SIN_AVISO
    }

    /** Un mensaje con su id ya calculado y su aviso. */
    record PiezaDelPrograma(MensajeId id, ContenidoDelPrograma contenido, AvisoDeLaPieza aviso) {
        public PiezaDelPrograma {
            Objects.requireNonNull(id, "id es obligatorio");
            Objects.requireNonNull(contenido, "contenido es obligatorio");
            Objects.requireNonNull(aviso, "aviso es obligatorio");
        }
    }

    /**
     * @param sobreQuien la persona a quien se refieren las piezas; queda en {@code emisor_id}. {@code null} solo
     *                   con {@link #sinPersona}: piezas que no se refieren a nadie (D-262, el podio semanal)
     */
    record EntregaDelPrograma(ConversacionId conversacionId, UserId sobreQuien, List<PiezaDelPrograma> piezas) {
        public EntregaDelPrograma {
            Objects.requireNonNull(conversacionId, "conversacionId es obligatorio");
            piezas = List.copyOf(piezas);
            if (sobreQuien == null && piezas.stream().anyMatch(p -> p.aviso() == AvisoDeLaPieza.SOLO_A_QUIEN_SE_REFIERE)) {
                throw new IllegalArgumentException("Sin persona a quien se refiera, no hay a quién avisarle solo a ella");
            }
        }

        /** Piezas del programa que no se refieren a nadie: se guardan sin {@code emisor_id} (V95, D-262). */
        public static EntregaDelPrograma sinPersona(ConversacionId conversacionId, List<PiezaDelPrograma> piezas) {
            return new EntregaDelPrograma(conversacionId, null, piezas);
        }
    }
}
