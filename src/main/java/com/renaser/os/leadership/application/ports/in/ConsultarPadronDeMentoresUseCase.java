package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * El padrón del cuerpo de mentores con sus indicadores (SDD 002, RL-04; D-241). Solo MENTOR_LEAD,
 * ADMIN y ALCHEMIST con la cuenta activa.
 */
public interface ConsultarPadronDeMentoresUseCase {

    PadronDeMentores padron(UserId actorId);

    /**
     * @param mes            el mes en curso, en {@code zona}: de él son la atención y la evaluación
     * @param semaforoDesde  la ventana del semáforo vigente; null si la medición no respondió
     */
    record PadronDeMentores(String mes, String zona, Instant corteEn, LocalDate semaforoDesde,
                            LocalDate semaforoHasta, List<IndicadoresDeMentor> mentores) {
    }
}
