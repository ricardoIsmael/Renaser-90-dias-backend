package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * El padrón del cuerpo de mentores con sus indicadores (SDD 002, RL-04; D-241). Solo MENTOR_LEAD,
 * ADMIN y ALCHEMIST con la cuenta activa.
 */
public interface ConsultarPadronDeMentoresUseCase {

    PadronDeMentores padron(UserId actorId);

    /**
     * @param mes            el mes en curso, en {@code zona}: de él son la atención y la evaluación
     * @param semaforoDesde  la ventana del semáforo vigente; null si la medición no respondió
     * @param mentores       primero los activos y después los suspendidos, cada grupo por nombre
     * @param suspendidos    spec 056 FR-004: cuáles de {@code mentores} tienen la cuenta suspendida (la app los muestra apagados)
     */
    record PadronDeMentores(String mes, String zona, Instant corteEn, LocalDate semaforoDesde,
                            LocalDate semaforoHasta, List<IndicadoresDeMentor> mentores, Set<UserId> suspendidos) {
    }
}
