package com.renaser.os.rocks.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierresDelAlumnoUseCase;
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
 * Los cierres de un alumno para su mentor o su guía (spec 017). Es el mismo guardián que el Código Renaser del alumno y que los
 * huecos de horario: el acompañante VIGENTE del grupo y un alumno que hoy es de ESE grupo; más la cuenta del actor activa, porque
 * el interceptor no mira a MENTOR (A-1, E-258).
 *
 * <p>Se copia el guard y no se comparte, a propósito, por la razón que dejan escrita los otros: un guard que sirve a dos
 * autorizaciones distintas es como se abren los agujeros que después nadie encuentra (ARF-15).
 */
@Service
public class CierresDelAlumnoService implements ConsultarCierresDelAlumnoUseCase {

    private final AcompanamientoFinder acompanamiento;
    private final UserSummaryFinder usuarios;
    private final CierresDelDiaFinder cierres;
    private final Clock clock;

    CierresDelAlumnoService(AcompanamientoFinder acompanamiento, UserSummaryFinder usuarios, CierresDelDiaFinder cierres,
                            Clock clock) {
        this.acompanamiento = acompanamiento;
        this.usuarios = usuarios;
        this.cierres = cierres;
        this.clock = clock;
    }

    @Override
    public CierresDeLaSemana semanaDe(ConsultaCierres consulta) {
        Instant ahora = clock.now();
        if (!acompanamiento.acompanaVigente(consulta.actorId(), consulta.grupoId(), ahora)) {
            throw new NotAuthorizedException("No acompañas ese grupo");
        }
        requireCuentaActiva(consulta.actorId());
        if (!acompanamiento.aprendicesVigentes(consulta.grupoId(), ahora).contains(consulta.alumnoId())) {
            throw new NotAuthorizedException("Ese alumno no pertenece al grupo que acompañas");
        }
        return cierres.semanaDe(consulta.alumnoId(), consulta.semana());
    }

    private void requireCuentaActiva(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta está suspendida");
        }
    }
}
