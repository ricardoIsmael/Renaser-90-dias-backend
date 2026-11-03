package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.DiagnosticoConsultadoEvent;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LectorDelDiagnostico;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Deja constancia de que alguien abrió el diagnóstico de otra persona (spec 026): publica {@link DiagnosticoConsultadoEvent}, que {@code leadership}
 * anota en {@code consultas_de_ficha}. Va dentro de la transacción de la lectura, así que si la lectura se deshace no queda constancia, y el outbox de
 * Modulith lo reentrega si quien lo anota falla. Si el lector es la propia persona no publica nada.
 */
@Component
class ConstanciaDeLecturasDelDiagnostico {

    private final ApplicationEventPublisher eventos;
    private final IdGenerator idGenerator;
    private final Clock clock;

    ConstanciaDeLecturasDelDiagnostico(ApplicationEventPublisher eventos, IdGenerator idGenerator, Clock clock) {
        this.eventos = eventos;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    void dejar(UserId persona, LectorDelDiagnostico lector) {
        if (lector.consultorId().equals(persona)) {
            return;
        }
        eventos.publishEvent(new DiagnosticoConsultadoEvent(idGenerator.newId(), lector.consultorId(), persona, lector.grupoId(), lector.mentorId(),
                clock.now()));
    }
}
