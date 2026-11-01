package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/** El diagnóstico de un alumno (spec 026, 026-11) para su mentor o su guía: el acompañante VIGENTE de SU grupo. Solo lectura. */
public interface ConsultarDiagnosticoDelAlumnoUseCase {

    LecturaDelDiagnostico diagnosticoDe(ConsultaDiagnostico consulta);

    /** Los tres juntos porque los tres hacen falta para autorizar (el grupo solo no alcanza). */
    record ConsultaDiagnostico(UserId actorId, UUID grupoId, UserId alumnoId) {

        public ConsultaDiagnostico {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(alumnoId, "alumnoId es obligatorio");
        }
    }
}
