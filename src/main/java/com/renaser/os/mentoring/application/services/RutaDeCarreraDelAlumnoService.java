package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.habits.api.RutaDeCarreraFinder;
import com.renaser.os.habits.api.RutaDeCarreraFinder.LectorDeLaRuta;
import com.renaser.os.habits.api.RutaDeCarreraFinder.RutaLeida;
import com.renaser.os.mentoring.application.ports.in.ConsultarRutaDeCarreraDelAlumnoUseCase;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * La ruta de una carrera de un alumno para su mentor o su guía (spec 042, FR-041). El mismo guardián que el diagnóstico del alumno
 * ({@link DiagnosticoDelAlumnoService}): el acompañante VIGENTE del grupo, un alumno que hoy es de ESE grupo y la cuenta del actor
 * activa (el interceptor no mira a MENTOR, A-1). Se copia el guard y no se comparte, por la misma razón que dejan escrita los otros
 * (ARF-15). Después de autorizar, {@code habits} lee y deja constancia de quién la abrió.
 */
@Service
public class RutaDeCarreraDelAlumnoService implements ConsultarRutaDeCarreraDelAlumnoUseCase {

    private final AcompanamientoFinder acompanamiento;
    private final UserSummaryFinder usuarios;
    private final RutaDeCarreraFinder rutas;
    private final Clock clock;

    RutaDeCarreraDelAlumnoService(AcompanamientoFinder acompanamiento, UserSummaryFinder usuarios, RutaDeCarreraFinder rutas,
                                  Clock clock) {
        this.acompanamiento = acompanamiento;
        this.usuarios = usuarios;
        this.rutas = rutas;
        this.clock = clock;
    }

    @Override
    public Optional<RutaLeida> rutaDe(ConsultaRuta consulta) {
        Instant ahora = clock.now();
        if (!acompanamiento.acompanaVigente(consulta.actorId(), consulta.grupoId(), ahora)) {
            throw new NotAuthorizedException("No acompanas ese grupo");
        }
        requireCuentaActiva(consulta.actorId());
        if (!acompanamiento.aprendicesVigentes(consulta.grupoId(), ahora).contains(consulta.alumnoId())) {
            throw new NotAuthorizedException("Ese alumno no pertenece al grupo que acompanas");
        }
        return rutas.rutaDe(consulta.alumnoId(), consulta.carreraId(),
                new LectorDeLaRuta(consulta.actorId(), consulta.grupoId(), consulta.actorId()));
    }

    private void requireCuentaActiva(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }
}
