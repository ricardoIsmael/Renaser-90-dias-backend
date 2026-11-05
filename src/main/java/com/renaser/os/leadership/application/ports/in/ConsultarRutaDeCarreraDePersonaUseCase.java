package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.habits.api.RutaDeCarreraFinder.RutaLeida;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * La ruta de una carrera con GPS de una persona (spec 042, FR-041), en solo lectura, para el líder de mentores, Administración y
 * Alquimista. La ruta es de {@code habits}; acá solo se decide a quién se le deja ver, con las mismas reglas que su semana. Vacío si
 * esa persona no tiene esa carrera o no tiene ruta guardada (404).
 */
public interface ConsultarRutaDeCarreraDePersonaUseCase {

    Optional<RutaLeida> rutaDe(ConsultaRutaDePersona consulta);

    /** La misma ruta sin pasar por un grupo (spec 021). */
    Optional<RutaLeida> rutaDeUnaPersona(ConsultaRutaDeUnaPersona consulta);

    record ConsultaRutaDePersona(UserId actorId, UUID grupoId, UUID personaId, UUID carreraId) {

        public ConsultaRutaDePersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
            Objects.requireNonNull(carreraId, "carreraId es obligatorio");
        }
    }

    record ConsultaRutaDeUnaPersona(UserId actorId, UUID personaId, UUID carreraId) {

        public ConsultaRutaDeUnaPersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
            Objects.requireNonNull(carreraId, "carreraId es obligatorio");
        }
    }
}
