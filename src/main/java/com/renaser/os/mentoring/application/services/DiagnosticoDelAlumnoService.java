package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.mentoring.application.ports.in.ConsultarDiagnosticoDelAlumnoUseCase;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * El diagnóstico de un alumno para su mentor o su guía (spec 026). El mismo guardián que los huecos y el Código Renaser del alumno ({@link HuecosDelAlumnoService}): el
 * acompañante VIGENTE del grupo, un alumno que hoy es de ESE grupo y la cuenta del actor activa (el interceptor no mira a MENTOR, A-1). Se copia el guard y
 * no se comparte, a propósito, por la misma razón que dejan escrita los otros (ARF-15).
 */
@Service
public class DiagnosticoDelAlumnoService implements ConsultarDiagnosticoDelAlumnoUseCase {

    private final AcompanamientoFinder acompanamiento;
    private final UserSummaryFinder usuarios;
    private final DiagnosticoDeLaPersonaFinder diagnostico;
    private final Clock clock;

    DiagnosticoDelAlumnoService(AcompanamientoFinder acompanamiento, UserSummaryFinder usuarios, DiagnosticoDeLaPersonaFinder diagnostico, Clock clock) {
        this.acompanamiento = acompanamiento;
        this.usuarios = usuarios;
        this.diagnostico = diagnostico;
        this.clock = clock;
    }

    @Override
    public LecturaDelDiagnostico diagnosticoDe(ConsultaDiagnostico consulta) {
        Instant ahora = clock.now();
        if (!acompanamiento.acompanaVigente(consulta.actorId(), consulta.grupoId(), ahora)) {
            throw new NotAuthorizedException("No acompanas ese grupo");
        }
        requireCuentaActiva(consulta.actorId());
        if (!acompanamiento.aprendicesVigentes(consulta.grupoId(), ahora).contains(consulta.alumnoId())) {
            throw new NotAuthorizedException("Ese alumno no pertenece al grupo que acompanas");
        }
        return diagnostico.lecturaDe(consulta.alumnoId());
    }

    private void requireCuentaActiva(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }
}
