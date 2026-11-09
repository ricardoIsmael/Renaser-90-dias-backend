package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.CompuertaDeRocasFinder;
import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocasMaestras;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
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
 *
 * <p><b>Issue #60 (2026-11-09).</b> Las Rocas Maestras se leen como las lee Plan
 * ({@link ConsultarRocasMaestrasUseCase#misRocasMaestras}): creando las que nacieron del diagnostico y todavia no existen. Antes se
 * leian sin crearlas, y a quien tenia sus objetivos en el diagnostico pero aun no habia abierto Plan, SER le decia que la
 * planificacion estaba cerrada. Por eso estas lecturas ya no son {@code readOnly}: pueden escribir esas filas, una sola vez.
 */
@Service
class CompuertaDeRocasService implements CompuertaDeRocasFinder {

    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final LoadRocaSemanalPort loadRocaSemanalPort;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final Clock clock;
    private final ConsultarRocasMaestrasUseCase rocasMaestras;

    CompuertaDeRocasService(LoadRocaMaestraPort loadRocaMaestraPort, LoadRocaSemanalPort loadRocaSemanalPort,
                            ConsultarProgresoParticipanteRocksPort progresoPort, Clock clock,
                            ConsultarRocasMaestrasUseCase rocasMaestras) {
        this.rocasMaestras = rocasMaestras;
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.loadRocaSemanalPort = loadRocaSemanalPort;
        this.progresoPort = progresoPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean rocasMaestrasCompletas(UserId aprendizId) {
        return maestrasDe(aprendizId).completas();
    }

    @Override
    @Transactional
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

    /**
     * Sin programa andando (no participa, cuenta suspendida, personal sin programa) {@code misRocasMaestras} no deja leer: ahi no se crea
     * nada y se lee lo que haya, como antes.
     */
    private RocasMaestras maestrasDe(UserId aprendizId) {
        try {
            return RocasMaestras.de(rocasMaestras.misRocasMaestras(aprendizId));
        } catch (NoSuchElementException | NotAuthorizedException sinProgresoQueLeer) {
            return RocasMaestras.de(loadRocaMaestraPort.deParticipante(aprendizId));
        }
    }
}
