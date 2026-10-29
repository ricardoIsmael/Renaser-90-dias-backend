package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.mentoring.api.HuecosDeHorarioFinder;
import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.application.ports.in.ConsultarHuecosDelAlumnoUseCase;
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
 * Los huecos de horario de un alumno para su mentor o su guía (spec 015). Es el mismo guardián que el Código Renaser
 * del alumno ({@code AcompanamientoDeAlumnoService}) y que el seguimiento semanal: el acompañante VIGENTE del grupo y
 * un alumno que hoy es de ESE grupo; más la cuenta del actor activa, porque el interceptor no mira a MENTOR (A-1, E-258).
 *
 * <p>Se copia el guard y no se comparte, a propósito, por la misma razón que dejan escrita los otros dos: un guard
 * que sirve a dos autorizaciones distintas es como se abren los agujeros que después nadie encuentra (ARF-15).
 */
@Service
public class HuecosDelAlumnoService implements ConsultarHuecosDelAlumnoUseCase {

    private final AcompanamientoFinder acompanamiento;
    private final UserSummaryFinder usuarios;
    private final HuecosDeHorarioFinder huecos;
    private final Clock clock;

    HuecosDelAlumnoService(AcompanamientoFinder acompanamiento, UserSummaryFinder usuarios,
                           HuecosDeHorarioFinder huecos, Clock clock) {
        this.acompanamiento = acompanamiento;
        this.usuarios = usuarios;
        this.huecos = huecos;
        this.clock = clock;
    }

    @Override
    public HuecosDeLaSemana semanaDe(ConsultaHuecos consulta) {
        Instant ahora = clock.now();
        if (!acompanamiento.acompanaVigente(consulta.actorId(), consulta.grupoId(), ahora)) {
            throw new NotAuthorizedException("No acompanas ese grupo");
        }
        requireCuentaActiva(consulta.actorId());
        if (!acompanamiento.aprendicesVigentes(consulta.grupoId(), ahora).contains(consulta.alumnoId())) {
            throw new NotAuthorizedException("Ese alumno no pertenece al grupo que acompanas");
        }
        return huecos.semanaDe(consulta.alumnoId(), consulta.semana());
    }

    private void requireCuentaActiva(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }
}
