package com.renaser.os.rag.application.ports.out.diagnostico;

import com.renaser.os.rag.domain.model.diagnostico.DiagnosticoDeLaPersona;
import com.renaser.os.shared.domain.UserId;

/** El diagnóstico (spec 026) de la persona que conversa. Lo resuelve {@code onboarding}; {@code rag} no toca sus tablas (D-41). */
public interface ConsultarDiagnosticoDeLaPersonaPort {

    /** Nunca {@code null}: quien no lo tiene sale como {@link DiagnosticoDeLaPersona#ninguno()}. */
    DiagnosticoDeLaPersona de(UserId participanteId);
}
