package com.renaser.os.support.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Una consulta de mentoría puntual, para que el líder de mentores se la recuerde al mentor que le toca (spec 056, FR-029). A diferencia de
 * {@link AtencionDeTicketsFinder}, que solo cuenta y mide, esta SÍ trae el texto del bloqueo: el recordatorio lo lleva, y quien lo recibe es el mentor del
 * aprendiz, que ya lo lee en su bandeja.
 *
 * <p>Solo lectura y sin autorización propia: quien la consume ya decidió si el actor puede mirar.
 */
public interface ConsultaDeTicketFinder {

    /** La consulta, o vacío si no existe. */
    Optional<ConsultaDeTicket> consulta(UUID ticketId);

    /**
     * @param descripcionDelBloqueo lo que el aprendiz escribió que lo bloquea
     * @param respondida            si ya tiene respuesta: no hay nada que recordar
     */
    record ConsultaDeTicket(UUID ticketId, UserId participanteId, String descripcionDelBloqueo, Instant creadaEn, boolean respondida) {
    }
}
