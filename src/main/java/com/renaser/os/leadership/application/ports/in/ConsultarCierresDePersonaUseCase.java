package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Los cierres de una semana de una persona de un grupo, para el líder de mentores, Administración y Alquimista (spec 017). Solo
 * lectura. Lo que se lee lo calcula {@code rocks} ({@code CierresDelDiaFinder}); acá solo se decide a quién se le deja ver y se deja
 * constancia de la consulta.
 */
public interface ConsultarCierresDePersonaUseCase {

    CierresDeLaSemana cierresDe(ConsultaCierresDePersona consulta);

    /** @param semana el lunes de la semana pedida; {@code null} = la semana en curso de la persona, en SU zona */
    record ConsultaCierresDePersona(UserId actorId, UUID grupoId, UUID personaId, LocalDate semana) {

        public ConsultaCierresDePersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }
}
