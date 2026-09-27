package com.renaser.os.chat.application.ports.in.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.shared.domain.UserId;

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
}
