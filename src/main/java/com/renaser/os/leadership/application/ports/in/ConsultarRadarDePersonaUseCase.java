package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.CodigoRenaserDePersona;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * El Código Renaser de una persona de un grupo, con su Mi Radar, para el líder de mentores, Administración y el
 * alquimista (spec 012, FR-013). Solo lectura sobre la persona, y no deja registro de consulta propio (FR-015, quitado
 * por el dueño el 2026-10-29: la ficha del líder ya registra la suya al abrirse, spec 005 FR-016).
 */
public interface ConsultarRadarDePersonaUseCase {

    CodigoRenaserDePersona radarDe(ConsultaRadarDePersona consulta);

    /** El mismo Código Renaser, sin pasar por un grupo (spec 021). */
    CodigoRenaserDePersona radarDeUnaPersona(ConsultaRadarDeUnaPersona consulta);

    /** @param cursor el {@code nextCursor} de la página anterior; null trae la más reciente */
    record ConsultaRadarDeUnaPersona(UserId actorId, UUID personaId, Instant cursor) {

        public ConsultaRadarDeUnaPersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }

    /** @param cursor el {@code nextCursor} de la página anterior; null trae la más reciente */
    record ConsultaRadarDePersona(UserId actorId, UUID grupoId, UUID personaId, Instant cursor) {

        public ConsultaRadarDePersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }
}
