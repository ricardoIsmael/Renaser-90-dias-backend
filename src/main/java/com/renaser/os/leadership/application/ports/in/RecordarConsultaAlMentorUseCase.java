package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * «Recordar al mentor» una consulta sin responder (spec 056, FR-029): el mentor asignado al aprendiz recibe en su chat con el líder un mensaje con la
 * consulta, y un aviso en su teléfono. Repetir la {@code claveOperacion} no manda nada dos veces.
 */
public interface RecordarConsultaAlMentorUseCase {

    /**
     * @throws java.util.NoSuchElementException 404 si la consulta no existe o su aprendiz no es del programa que se muestre
     * @throws com.renaser.os.shared.domain.NotAuthorizedException 403 si no es del liderazgo activo, o {@code FUERA_DE_SU_ALCANCE} si el aprendiz es de una
     *         formación que el líder no tiene
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException 409 {@code YA_RESPONDIDA} si ya no hay nada que recordar, o {@code SIN_MENTOR} si el
     *         aprendiz no tiene un mentor a quien recordarle
     */
    void recordar(RecordarConsultaCommand comando);

    /** @param claveOperacion la misma en un reintento: el recordatorio sale una sola vez */
    record RecordarConsultaCommand(UserId actorId, UUID ticketId, String claveOperacion) {
    }
}
