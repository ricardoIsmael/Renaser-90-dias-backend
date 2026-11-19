package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.shared.domain.UserId;

/**
 * «Avisar a su mentor» desde la ficha de una persona (spec 056, FR-019): el mentor vigente de la persona recibe un aviso en su teléfono y, en su chat con
 * el líder, el mensaje con el enlace a la ficha. Es una observación del líder (spec 005, R-7) sobre ese mentor y esa persona, así que queda en «Lo que le
 * dijiste» y repetir la {@code claveOperacion} no manda nada dos veces.
 */
public interface AvisarAlMentorUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException 403 si no es del liderazgo activo, o {@code FUERA_DE_SU_ALCANCE} si la persona es de una
     *         formación que el líder no tiene
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException 409 {@code SIN_MENTOR} si la persona no tiene un mentor a quien avisar (sin grupo,
     *         en la recepción o en un grupo sin mentor)
     * @throws java.util.NoSuchElementException 404 si la persona no es un aprendiz del programa que se muestre
     */
    void avisar(AvisarAlMentorCommand comando);

    /** @param claveOperacion la misma en un reintento: el aviso sale una sola vez */
    record AvisarAlMentorCommand(UserId actorId, UserId personaId, String texto, String claveOperacion) {
    }
}
