package com.renaser.os.rag.application.ports.in.semaforo;

import com.renaser.os.rag.domain.model.semaforo.SemaforoEnChat.CierreDeSemana;
import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Cuando se cierra la semana del semáforo de una persona (D-168), el acompañante
 * ({@code COMPANION}) le deja en el chat cómo le fue: color, palabra y porcentaje, con una frase corta
 * según el color. Armado con una plantilla — sin llamar a la IA y sin consumir la cuota de Renasia.
 *
 * <p><b>Idempotente.</b> El outbox de Modulith puede reentregar: el mensaje se escribe una sola vez
 * por {@code claveEvento}.
 */
public interface DejarSemaforoEnChatUseCase {

    /** @return {@code true} si escribió el mensaje; {@code false} si no correspondía o ya estaba. */
    boolean dejarEnElChat(SemaforoEnChatCommand command);

    /**
     * Espejo de {@code points.api.SemanaDelSemaforoCerradaEvent}: el puerto no expone el tipo ajeno.
     *
     * @param claveEvento la {@code claveDeduplicacion} del evento: una por persona y semana
     */
    record SemaforoEnChatCommand(UserId participanteId, CierreDeSemana cierre, UUID claveEvento) {
    }
}
