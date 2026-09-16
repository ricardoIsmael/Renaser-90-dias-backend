package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * El staff se va de un chat de soporte. <b>El aprendiz no puede</b> (regla del dueño del proyecto:
 * la conversacion es su via de contacto con la casa, y si pudiera cerrarla se quedaria sin ella sin
 * enterarse).
 *
 * <p>Solo aplica a conversaciones de tipo SOPORTE. Irse de una CELULA, de un DM o de la GLOBAL son
 * tres preguntas distintas que nadie contesto todavia: pedirlo sobre cualquier otro tipo se rechaza
 * en vez de inventarle un significado.
 *
 * <p>Salir borra la fila de participacion, nunca los mensajes: lo que esa persona escribio sigue
 * ahi, igual que en {@code QuitarParticipantePort}. La conversacion es del aprendiz y su historia
 * tambien.
 */
public interface SalirDeConversacionSoporteUseCase {

    void salir(SalirDeConversacionSoporteCommand command);

    record SalirDeConversacionSoporteCommand(@NotNull UserId actorId, @NotNull ConversacionId conversacionId) {

        public SalirDeConversacionSoporteCommand {
            SelfValidating.validateConstructorArgs(SalirDeConversacionSoporteCommand.class, actorId, conversacionId);
        }
    }
}
