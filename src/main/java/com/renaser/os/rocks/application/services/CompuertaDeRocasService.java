package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.CompuertaDeRocasFinder;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocasMaestras;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Implementa {@link CompuertaDeRocasFinder} (D-247) con las mismas piezas que usan los casos de uso que
 * escriben: {@link RocasMaestras} y {@link AccesoARocas#objetivoSemanalDelDia}. Aca no hay una regla propia.
 */
@Service
class CompuertaDeRocasService implements CompuertaDeRocasFinder {

    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final LoadRocaSemanalPort loadRocaSemanalPort;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final Clock clock;

    CompuertaDeRocasService(LoadRocaMaestraPort loadRocaMaestraPort, LoadRocaSemanalPort loadRocaSemanalPort,
                            ConsultarProgresoParticipanteRocksPort progresoPort, Clock clock) {
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.loadRocaSemanalPort = loadRocaSemanalPort;
        this.progresoPort = progresoPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean rocasMaestrasCompletas(UserId aprendizId) {
        return maestrasDe(aprendizId).completas();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha) {
        ProgresoParticipanteRocks progreso = progresoPort.deParticipante(aprendizId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + aprendizId));
        Optional<SemanaPrograma> semanas = progreso.semanas(clock.now().atZone(progreso.zona()).toLocalDate());
        if (semanas.isEmpty()) {
            return List.of();
        }
        RocasMaestras maestras = maestrasDe(aprendizId);
        return Arrays.stream(EjeObjetivo.values())
                .filter(eje -> maestras.delEje(eje)
                        .flatMap(maestra -> AccesoARocas.objetivoSemanalDelDia(loadRocaSemanalPort, maestra,
                                semanas.get(), fecha))
                        .isEmpty())
                .map(EjeObjetivo::name)
                .toList();
    }

    private RocasMaestras maestrasDe(UserId aprendizId) {
        return RocasMaestras.de(loadRocaMaestraPort.deParticipante(aprendizId));
    }
}
