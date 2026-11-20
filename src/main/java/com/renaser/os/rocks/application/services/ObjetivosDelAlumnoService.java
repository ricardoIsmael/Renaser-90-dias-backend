package com.renaser.os.rocks.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder;
import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.ObjetivosDelAprendiz;
import com.renaser.os.rocks.application.ports.in.objetivos.ConsultarObjetivosDelAlumnoUseCase;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Los objetivos de un alumno para su mentor o su guía (spec 057). Mismo guard que los cierres del alumno: el acompañante VIGENTE del grupo,
 * un alumno que hoy es de ESE grupo y la cuenta del actor activa (el interceptor no mira a MENTOR: A-1, E-258). Lo que se lee es lo que
 * ya lee el líder ({@link ObjetivosDelAprendizFinder}); un alumno suspendido o sin programa andando es un 404 con motivo, no un 500.
 */
@Service
class ObjetivosDelAlumnoService implements ConsultarObjetivosDelAlumnoUseCase {

    static final String SIN_PROGRAMA_ACTIVO = "SIN_PROGRAMA_ACTIVO";

    private final AcompanamientoFinder acompanamiento;
    private final UserSummaryFinder usuarios;
    private final ObjetivosDelAprendizFinder objetivos;
    private final Clock clock;

    ObjetivosDelAlumnoService(AcompanamientoFinder acompanamiento, UserSummaryFinder usuarios,
                              ObjetivosDelAprendizFinder objetivos, Clock clock) {
        this.acompanamiento = acompanamiento;
        this.usuarios = usuarios;
        this.objetivos = objetivos;
        this.clock = clock;
    }

    @Override
    public ObjetivosDelAprendiz objetivosDe(ConsultaObjetivos consulta) {
        Instant ahora = clock.now();
        if (!acompanamiento.acompanaVigente(consulta.actorId(), consulta.grupoId(), ahora)) {
            throw new NotAuthorizedException("No acompañas ese grupo");
        }
        requireCuentaActiva(consulta.actorId());
        if (!acompanamiento.aprendicesVigentes(consulta.grupoId(), ahora).contains(consulta.alumnoId())) {
            throw new NotAuthorizedException("Ese alumno no pertenece al grupo que acompañas");
        }
        try {
            return objetivos.objetivosDe(consulta.alumnoId());
        } catch (NotAuthorizedException | NoSuchElementException sinPrograma) {
            // Suspendido, o sin su programa de objetivos andando: existe, pero todavía no tiene qué mostrar.
            throw RechazoConCodigoException.noEncontrado(SIN_PROGRAMA_ACTIVO, "Esa persona todavía no tiene objetivos");
        }
    }

    private void requireCuentaActiva(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta está suspendida");
        }
    }
}
