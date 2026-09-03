package com.renaser.os.rocks.application.ports.out.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoadRocaDiariaPort {

    Optional<RocaDiaria> byId(RocaDiariaId id);

    /** Version con bloqueo para el camino de escritura (completar): evita que dos requests
     * concurrentes (doble toque, reintento por timeout) completen la misma roca y otorguen el
     * premio dos veces — mismo patron que {@code LoadRegistroHabitoPort.byIdParaEscritura}
     * en `habits` (C-2, docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html). */
    Optional<RocaDiaria> byIdParaEscritura(RocaDiariaId id);

    List<RocaDiaria> deParticipanteYFecha(UserId participanteId, LocalDate fecha);

    int contarDeParticipanteYFecha(UserId participanteId, LocalDate fecha);

    /** Histórico completo, para {@code users.api.RocaLogrosFinder#totalRocksCompleted}. */
    int contarCompletadasDeParticipante(UserId participanteId);

    /** Para {@code users.api.RocaLogrosFinder#firstRockCompletedAt}. */
    Optional<Instant> primeraCompletadaEnDeParticipante(UserId participanteId);

    /**
     * Una fecha por cada Roca Diaria completada (repetida si hubo más de una el mismo
     * día) — insumo crudo de {@code RachaRocas#calcular}, para
     * {@code users.api.RocaLogrosFinder#bestRocksStreakDays}.
     */
    List<LocalDate> fechasCompletadasDeParticipante(UserId participanteId);
}
