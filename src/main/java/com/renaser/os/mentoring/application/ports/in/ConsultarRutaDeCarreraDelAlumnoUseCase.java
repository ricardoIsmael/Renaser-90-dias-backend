package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.habits.api.RutaDeCarreraFinder.RutaLeida;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** La ruta de una carrera con GPS de un alumno (spec 042, FR-041) para su mentor o su guía: el acompañante VIGENTE de SU grupo. */
public interface ConsultarRutaDeCarreraDelAlumnoUseCase {

    Optional<RutaLeida> rutaDe(ConsultaRuta consulta);

    /** Los cuatro juntos porque hacen falta para autorizar (el grupo solo no alcanza) y para encontrar la carrera. */
    record ConsultaRuta(UserId actorId, UUID grupoId, UserId alumnoId, UUID carreraId) {

        public ConsultaRuta {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(alumnoId, "alumnoId es obligatorio");
            Objects.requireNonNull(carreraId, "carreraId es obligatorio");
        }
    }
}
