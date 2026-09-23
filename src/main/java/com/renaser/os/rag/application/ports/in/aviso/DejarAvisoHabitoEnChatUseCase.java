package com.renaser.os.rag.application.ports.in.aviso;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Cuando sale uno de los dos avisos automaticos de un habito, deja ADEMAS un mensaje del
 * acompanante ({@code COMPANION}) en la conversacion del aprendiz, armado con una plantilla y
 * datos reales — sin llamar a la IA (fase 5, {@code PROPUESTA_ACOMPANANTE_90_DIAS.md} §5.1).
 *
 * <p><b>Idempotente.</b> {@code habits} vuelve a publicar el mismo aviso en cada barrido mientras
 * dura su franja, y el outbox de Modulith puede reentregar: el mensaje se escribe una sola vez por
 * {@code claveEvento}.
 *
 * <p>No consume la cuota diaria de Renasia: no es un mensaje que la persona haya escrito.
 */
public interface DejarAvisoHabitoEnChatUseCase {

    /** @return {@code true} si escribio el mensaje; {@code false} si no correspondia o ya estaba. */
    boolean dejarEnElChat(AvisoHabitoEnChatCommand command);

    /**
     * Espejo de {@code habits.api.AvisoHabitoDebidoEvent}: el puerto no expone el tipo ajeno.
     *
     * @param tipoAviso        {@code INICIO} o {@code POR_VENCER}, como String
     * @param minutosQueFaltan desde {@code calculadoEn}, redondeado hacia arriba por {@code habits}
     * @param puntosEnJuego    lo que gana si lo entrega ahora, calculado por {@code habits} (D-97)
     * @param claveEvento      clave deterministica por registro y tipo de aviso
     * @param calculadoEn      el {@code occurredAt} del evento: el instante en que se calculo el aviso
     */
    record AvisoHabitoEnChatCommand(UserId participanteId, String tituloHabito, String tipoAviso,
                                    long minutosQueFaltan, int puntosEnJuego, UUID claveEvento,
                                    Instant calculadoEn) {
    }
}
