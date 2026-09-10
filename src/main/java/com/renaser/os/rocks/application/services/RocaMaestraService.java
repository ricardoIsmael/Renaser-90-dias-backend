package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.GuardarRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class RocaMaestraService implements ConsultarRocasMaestrasUseCase, DefinirRocaMaestraUseCase {

    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final GuardarRocaMaestraPort guardarRocaMaestraPort;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public RocaMaestraService(LoadRocaMaestraPort loadRocaMaestraPort,
                               GuardarRocaMaestraPort guardarRocaMaestraPort,
                               ConsultarProgresoParticipanteRocksPort progresoPort, Clock clock,
                               IdGenerator idGenerator) {
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.guardarRocaMaestraPort = guardarRocaMaestraPort;
        this.progresoPort = progresoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<RocaMaestra> misRocasMaestras(UserId actorId) {
        requireProgreso(actorId);
        return loadRocaMaestraPort.deParticipante(actorId);
    }

    /**
     * Define el objetivo del eje o corrige el que ya estaba. Es la misma operacion porque por
     * eje hay una sola Roca Maestra ({@code UNIQUE (participante_id, eje)}): si existe se
     * redefine conservando identidad y fecha de creacion, si no se crea.
     */
    @Override
    public RocaMaestra definir(DefinirRocaMaestraCommand command) {
        requireProgreso(command.actorId());
        MetaCuantitativa meta = command.tieneMeta()
                ? new MetaCuantitativa(command.meta(), command.avance(), command.unidad(), command.lineaBase())
                : null;

        return guardarRocaMaestraPort.guardar(
                loadRocaMaestraPort.deParticipanteYEje(command.actorId(), command.eje())
                        .map(existente -> existente.redefinir(command.objetivo(), meta, clock.now()))
                        .orElseGet(() -> RocaMaestra.definir(RocaMaestraId.of(idGenerator.newId()),
                                command.actorId(), command.eje(), command.objetivo(), meta, clock.now())));
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
        /* E-169: la puerta no es el ROL, es tener el programa andando. `TRACK_PROGRAM_AS_STAFF`
           y `POST /api/v1/mentor/activate-tracking` existen para que el staff curse los 90 dias;
           preguntar solo por el rol dejaba la inscripcion construida y el uso prohibido.

           Se conserva la rama del rol en vez de reducirlo a `!programaActivado`: un TRAINEE recien
           aprobado tiene `programa_activado_en` en null hasta que termina primer login + Ficha +
           Terminos, y el cambio corto lo habria dejado fuera de su propio programa. Asi el cambio
           es ESTRICTAMENTE aditivo: nadie que hoy pase, deja de pasar. */
        if (progreso.rol() != RolParticipante.TRAINEE && !progreso.programaActivado()) {
            throw new NotAuthorizedException("Solo un aprendiz opera sus propias rocas");
        }
        return progreso;
    }
}
