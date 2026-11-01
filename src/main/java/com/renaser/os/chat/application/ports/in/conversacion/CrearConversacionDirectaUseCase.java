package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface CrearConversacionDirectaUseCase {

    /** Busca-o-crea, idempotente por {@code claveDirecta} (CLAUDE.MD sec. 5.4.3). */
    Conversacion obtenerOCrear(CrearConversacionDirectaCommand command);

    /**
     * {@code formacion} (spec 024, FR-029): solo cuando las dos personas comparten MÁS de una formación (dos administradores, por ejemplo) hace
     * falta elegir en cuál se abre el directo; en todo lo demás sale de las dos personas y se ignora. Dos personas sin formación en común no
     * se pueden escribir (404, como si la otra no existiera).
     */
    record CrearConversacionDirectaCommand(@NotNull UserId actorId, @NotNull UserId otroUsuarioId, Formacion formacion) {

        public CrearConversacionDirectaCommand {
            SelfValidating.validateConstructorArgs(CrearConversacionDirectaCommand.class, actorId, otroUsuarioId, formacion);
        }

        public CrearConversacionDirectaCommand(UserId actorId, UserId otroUsuarioId) {
            this(actorId, otroUsuarioId, null);
        }
    }
}
