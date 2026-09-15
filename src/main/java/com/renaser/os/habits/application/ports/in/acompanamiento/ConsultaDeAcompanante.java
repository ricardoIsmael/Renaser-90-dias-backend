package com.renaser.os.habits.application.ports.in.acompanamiento;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/**
 * Quien pregunta, por que grupo, y por cual de sus alumnos. Los tres datos juntos porque los tres
 * hacen falta para autorizar: el grupo solo no alcanza —un mentor legitimo podria leer a cualquiera
 * pasando el id de SU grupo— y el alumno solo tampoco, porque no dice de donde sale el vinculo.
 *
 * <p>Es el mismo trio que ya usa {@code mentoring.ConsultarSeguimientoSemanalUseCase.ConsultaSemana}
 * para la semana del alumno. Se repite en vez de compartirse porque son modulos distintos y un
 * record de comando no es contrato publico de nadie.
 */
public record ConsultaDeAcompanante(UserId actorId, UUID grupoId, UserId alumnoId) {

    public ConsultaDeAcompanante {
        Objects.requireNonNull(actorId, "actorId es obligatorio");
        Objects.requireNonNull(grupoId, "grupoId es obligatorio");
        Objects.requireNonNull(alumnoId, "alumnoId es obligatorio");
    }
}
