package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class RocaMaestraService implements ConsultarRocasMaestrasUseCase {

    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;

    public RocaMaestraService(LoadRocaMaestraPort loadRocaMaestraPort,
                               ConsultarProgresoParticipanteRocksPort progresoPort) {
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.progresoPort = progresoPort;
    }

    @Override
    public List<RocaMaestra> misRocasMaestras(UserId actorId) {
        requireProgreso(actorId);
        return loadRocaMaestraPort.deParticipante(actorId);
    }

    /**
     * SUSPENDIDO -> 403. Sin fila de participante -> 404 (paridad
     * `findTraineeProfileByUserId`). Rol distinto de TRAINEE -> 403: en todo
     * `rocks`, igual que en el repo viejo, solo el propio aprendiz opera sus
     * rocas (`findTraineeProfileByUserId` en cada endpoint del `service.ts`
     * original) — no hay vista de mentor/admin construida todavia.
     */
    private ProgresoParticipanteRocks requireProgreso(UserId actorId) {
        ProgresoParticipanteRocks progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (progreso.rol() != RolParticipante.TRAINEE) {
            throw new NotAuthorizedException("Solo un aprendiz opera sus propias rocas");
        }
        return progreso;
    }
}
