package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/**
 * El diagnóstico de una persona (spec 026), en solo lectura, para el líder de mentores, Administración y Alquimista (026-11). El contenido es el de
 * {@code onboarding}; acá solo se decide a quién se le deja ver, con las mismas reglas que su semana.
 */
public interface ConsultarDiagnosticoDePersonaUseCase {

    LecturaDelDiagnostico diagnosticoDe(ConsultaDiagnosticoDePersona consulta);

    /** El mismo diagnóstico sin pasar por un grupo (spec 021). */
    LecturaDelDiagnostico diagnosticoDeUnaPersona(ConsultaDiagnosticoDeUnaPersona consulta);

    record ConsultaDiagnosticoDeUnaPersona(UserId actorId, UUID personaId) {

        public ConsultaDiagnosticoDeUnaPersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }

    record ConsultaDiagnosticoDePersona(UserId actorId, UUID grupoId, UUID personaId) {

        public ConsultaDiagnosticoDePersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }
}
