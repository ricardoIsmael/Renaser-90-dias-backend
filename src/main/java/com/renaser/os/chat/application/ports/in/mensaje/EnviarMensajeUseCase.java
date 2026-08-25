package com.renaser.os.chat.application.ports.in.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface EnviarMensajeUseCase {

    Mensaje enviar(EnviarMensajeCommand command);

    record EnviarMensajeCommand(@NotNull UserId actorId, @NotNull ConversacionId conversacionId,
                                 @NotNull TipoMensaje tipo, String texto, String mediaBucket, String mediaRuta,
                                 String mediaMime, Integer mediaBytes, Short mediaDuracionS,
                                 MensajeId respuestaAId) {

        public EnviarMensajeCommand {
            SelfValidating.validateConstructorArgs(EnviarMensajeCommand.class, actorId, conversacionId, tipo, texto,
                    mediaBucket, mediaRuta, mediaMime, mediaBytes, mediaDuracionS, respuestaAId);
        }
    }
}
