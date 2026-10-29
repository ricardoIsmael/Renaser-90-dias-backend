package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Los huecos de horario de una persona de un grupo, para el líder de mentores, Administración y Alquimista (spec 015).
 * Solo lectura: la persona no los ve en esta primera versión. El cálculo es el de {@code mentoring}
 * ({@code HuecosDeHorarioFinder}); acá solo se decide a quién se le deja ver.
 */
public interface ConsultarHuecosDePersonaUseCase {

    HuecosDeLaSemana huecosDe(ConsultaHuecosDePersona consulta);

    /**
     * @param semana el lunes de la semana pedida; {@code null} = la semana en curso de la persona, en SU zona
     */
    record ConsultaHuecosDePersona(UserId actorId, UUID grupoId, UUID personaId, LocalDate semana) {

        public ConsultaHuecosDePersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
        }
    }
}
