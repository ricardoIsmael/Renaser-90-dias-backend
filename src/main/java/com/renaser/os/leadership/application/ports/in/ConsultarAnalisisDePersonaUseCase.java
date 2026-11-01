package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * El 80/20 y el análisis de una persona para el líder de mentores, Administración y Alquimista (spec 031, 031-2 A). Solo lectura. Lo que
 * se lee lo arma {@code rag} ({@code Analisis8020Finder}); acá solo se decide a quién se le deja ver, se respeta el interruptor «Código
 * Renaser en las fichas» (spec 034) y se deja constancia de la consulta.
 */
public interface ConsultarAnalisisDePersonaUseCase {

    VistaDelAnalisis analisisDe(ConsultaAnalisisDePersona consulta);

    /** El mismo análisis, sin pasar por un grupo (spec 021); deja el mismo registro de consulta. */
    VistaDelAnalisis analisisDeUnaPersona(ConsultaAnalisisDeUnaPersona consulta);

    /** @param inicio el primer día del periodo; {@code null} = el periodo en curso de la persona */
    record ConsultaAnalisisDeUnaPersona(UserId actorId, UUID personaId, Tipo tipo, LocalDate inicio) {

        public ConsultaAnalisisDeUnaPersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
            Objects.requireNonNull(tipo, "tipo es obligatorio");
        }
    }

    /** @param inicio igual que en {@link ConsultaAnalisisDeUnaPersona} */
    record ConsultaAnalisisDePersona(UserId actorId, UUID grupoId, UUID personaId, Tipo tipo, LocalDate inicio) {

        public ConsultaAnalisisDePersona {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(personaId, "personaId es obligatorio");
            Objects.requireNonNull(tipo, "tipo es obligatorio");
        }
    }
}
