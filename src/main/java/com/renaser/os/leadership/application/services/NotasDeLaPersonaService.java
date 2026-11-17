package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarNotasDeLaPersonaUseCase;
import com.renaser.os.mentoring.api.NotaDelAprendiz;
import com.renaser.os.mentoring.api.NotasDelMentorFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Las notas privadas de una persona para el liderazgo (spec 055, FR-024). Autoriza primero —rol de liderazgo activo y una persona que
 * se muestra dentro de SU formación, la misma regla de {@link PersonasDelPrograma#mirar}— y lee después. Las notas son de
 * {@code mentoring}; acá solo se decide quién las puede leer.
 */
@Service
public class NotasDeLaPersonaService implements ConsultarNotasDeLaPersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelPrograma programa;
    private final NotasDelMentorFinder notas;
    private final Clock clock;

    NotasDeLaPersonaService(AccesoDeLiderazgo acceso, PersonasDelPrograma programa, NotasDelMentorFinder notas,
                            Clock clock) {
        this.acceso = acceso;
        this.programa = programa;
        this.notas = notas;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotaDelAprendiz> notasDeUnaPersona(UserId actorId, UUID personaId) {
        acceso.requireLiderazgoActivo(actorId);
        programa.mirar(actorId, UserId.of(personaId), clock.now());
        return notas.notasDe(UserId.of(personaId));
    }
}
