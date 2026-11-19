package com.renaser.os.support.application.ports.in.ticketmentor;

import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;

/**
 * Un ticket y el nombre de quien lo respondió (spec 056, FR-030): el aprendiz ve la respuesta con el nombre de quien la dio —el mentor o el líder— y la
 * respuesta no puede deducirlo del mentor de hoy, que miente sobre el pasado (D-241). {@code nombreDeQuienRespondio} es {@code null} si no está respondido,
 * si es de antes de V88 (sin autor) o si la cuenta de quien respondió ya no existe.
 */
public record TicketConAutor(TicketMentor ticket, String nombreDeQuienRespondio) {
}
