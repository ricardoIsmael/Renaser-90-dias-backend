package com.renaser.os.rocks.application.services;

import com.renaser.os.users.api.RocaLogrosFinder;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RachaRocas;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Implementación de {@link RocaLogrosFinder} (docs/PLAN_INTEGRACION_FRONTEND.md
 * §5, gap #22). Solo lectura histórica agregada sobre {@code rocas_diarias};
 * la racha (D-... {@code bestRocksStreakDays}) se calcula en memoria con
 * {@link RachaRocas}, no en SQL — mismo criterio que {@code PorcentajeRocasService}
 * ya aplica para su propia Ley VI.
 */
@Service
class RocaLogrosService implements RocaLogrosFinder {

    private final LoadRocaDiariaPort loadRocaDiariaPort;

    RocaLogrosService(LoadRocaDiariaPort loadRocaDiariaPort) {
        this.loadRocaDiariaPort = loadRocaDiariaPort;
    }

    @Override
    @Transactional(readOnly = true)
    public int totalRocksCompleted(UserId participanteId) {
        return loadRocaDiariaPort.contarCompletadasDeParticipante(participanteId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> firstRockCompletedAt(UserId participanteId) {
        return loadRocaDiariaPort.primeraCompletadaEnDeParticipante(participanteId);
    }

    @Override
    @Transactional(readOnly = true)
    public int bestRocksStreakDays(UserId participanteId) {
        return RachaRocas.calcular(loadRocaDiariaPort.fechasCompletadasDeParticipante(participanteId));
    }
}
