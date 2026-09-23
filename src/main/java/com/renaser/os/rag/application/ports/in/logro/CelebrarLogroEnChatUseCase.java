package com.renaser.os.rag.application.ports.in.logro;

import com.renaser.os.rag.domain.model.logro.TipoLogro;
import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Cuando la persona alcanza un hito (racha sin celular completa, roca completada), el acompanante
 * ({@code COMPANION}) le deja un mensaje de celebracion en el chat, armado con una plantilla — sin
 * llamar a la IA y sin consumir la cuota diaria de Renasia.
 *
 * <p><b>Idempotente.</b> El outbox de Modulith puede reentregar: el mensaje se escribe una sola vez
 * por {@code (tipo, claveEvento)}.
 */
public interface CelebrarLogroEnChatUseCase {

    /** @return {@code true} si escribio el mensaje; {@code false} si no correspondia o ya estaba. */
    boolean celebrarEnElChat(LogroEnChatCommand command);

    /**
     * @param claveEvento el id del hito en su modulo ({@code rachaId}, {@code rocaId}): cada uno se
     *                    completa una sola vez, asi que identifica la celebracion
     */
    record LogroEnChatCommand(UserId participanteId, TipoLogro tipo, UUID claveEvento) {
    }
}
