package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.mentoring.api.NotaDelAprendiz;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.UUID;

/**
 * El liderazgo (MENTOR_LEAD, ADMIN y ALCHEMIST activos) lee las notas privadas que los mentores escribieron sobre una persona de su
 * formación (spec 055, FR-024). Solo lectura: {@code puedeCorregir} siempre es false. No deja registro de consulta.
 */
public interface ConsultarNotasDeLaPersonaUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si no es liderazgo activo (403)
     * @throws java.util.NoSuchElementException                    si la persona no se muestra o es de otra formación (404)
     */
    List<NotaDelAprendiz> notasDeUnaPersona(UserId actorId, UUID personaId);
}
