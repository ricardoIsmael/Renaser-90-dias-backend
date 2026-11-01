package com.renaser.os.rag.application.ports.in.analisis;

import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * El 80/20 y el análisis de un alumno para su mentor o su guía (spec 031, 031-2 A): solo lectura, solo del acompañante VIGENTE del grupo
 * y solo mientras el interruptor «Código Renaser en las fichas» (spec 034) lo deje. Es la ficha del alumno: no hay ruta para pedir a
 * otra persona.
 */
public interface ConsultarAnalisisDeAlumnoUseCase {

    VistaDelAnalisis deAlumno(ConsultaDeAnalisisDeAlumno consulta);

    /** @param inicio el primer día del periodo; {@code null} = el periodo en curso */
    record ConsultaDeAnalisisDeAlumno(UserId actorId, UUID grupoId, UserId alumnoId, TipoDePeriodo tipo, LocalDate inicio) {

        public ConsultaDeAnalisisDeAlumno {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(alumnoId, "alumnoId es obligatorio");
            Objects.requireNonNull(tipo, "tipo es obligatorio");
        }
    }
}
