package com.renaser.os.rag.application.ports.in.analisis;

import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;

/** La persona lee su propio 80/20 y su análisis (spec 031, FR-003 y FR-020): siempre sobre sí misma, nunca por un id que se pase. */
public interface ConsultarAnalisis8020UseCase {

    VistaDelAnalisis deLaPersona(ConsultaDeAnalisis consulta);

    /** @param inicio el primer día del periodo; {@code null} = el periodo en curso */
    record ConsultaDeAnalisis(UserId actorId, TipoDePeriodo tipo, LocalDate inicio) {

        public ConsultaDeAnalisis {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(tipo, "tipo es obligatorio");
        }
    }
}
