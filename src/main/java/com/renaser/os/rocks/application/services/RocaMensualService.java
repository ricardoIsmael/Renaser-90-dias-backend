package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarRocasMensualesUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamensual.GuardarRocaMensualPort;
import com.renaser.os.rocks.application.ports.out.rocamensual.LoadRocaMensualPort;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensualId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class RocaMensualService implements ConsultarRocasMensualesUseCase, DefinirRocaMensualUseCase {

    private final LoadRocaMensualPort loadRocaMensualPort;
    private final GuardarRocaMensualPort guardarRocaMensualPort;
    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public RocaMensualService(LoadRocaMensualPort loadRocaMensualPort,
                               GuardarRocaMensualPort guardarRocaMensualPort,
                               LoadRocaMaestraPort loadRocaMaestraPort,
                               ConsultarProgresoParticipanteRocksPort progresoPort, Clock clock,
                               IdGenerator idGenerator) {
        this.loadRocaMensualPort = loadRocaMensualPort;
        this.guardarRocaMensualPort = guardarRocaMensualPort;
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.progresoPort = progresoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<RocaMensual> misRocasMensuales(UserId actorId) {
        requireProgreso(actorId);
        return loadRocaMensualPort.deParticipante(actorId);
    }

    /**
     * Define el tramo de ese mes o corrige el que ya estaba. Es la misma operacion porque por
     * (eje, mes) hay un solo objetivo mensual ({@code UNIQUE (roca_maestra_id, numero_mes)}): si
     * existe se redefine conservando identidad y fecha de creacion, si no se crea.
     */
    @Override
    public RocaMensual definir(DefinirRocaMensualCommand command) {
        requireProgreso(command.actorId());
        RocaMaestra maestra = requireMaestraDelEje(command);
        MetaCuantitativa meta = command.tieneMeta()
                ? new MetaCuantitativa(command.meta(), command.avance(), command.unidad(), null)
                : null;

        return guardarRocaMensualPort.guardar(
                loadRocaMensualPort.deMaestraYMes(maestra.id(), command.numeroMes())
                        .map(existente -> existente.redefinir(command.titulo(), meta, clock.now()))
                        .orElseGet(() -> RocaMensual.definir(RocaMensualId.of(idGenerator.newId()),
                                maestra.id(), command.numeroMes(), command.titulo(), meta, clock.now())));
    }

    /**
     * El tramo mensual cuelga del objetivo de 90 dias, asi que sin ese objetivo no hay de que ser
     * tramo: "facturar 10.000 este mes" se entiende porque la meta de los 90 dias son 30.000.
     * Por eso falta la maestra es un 404 con un mensaje que dice que hacer, y no un alta silenciosa
     * de una maestra vacia — el aprendiz tiene que decidir su objetivo grande primero.
     */
    private RocaMaestra requireMaestraDelEje(DefinirRocaMensualCommand command) {
        return loadRocaMaestraPort.deParticipanteYEje(command.actorId(), command.eje())
                .orElseThrow(() -> new NoSuchElementException(
                        "Antes de definir un objetivo mensual hay que definir el objetivo de 90 dias del eje "
                                + command.eje()));
    }

    /**
     * Mismo guard que {@code RocaMaestraService}: SUSPENDIDO -> 403, sin fila de participante ->
     * 404, y rol distinto de TRAINEE -> 403 porque en todo {@code rocks} solo el propio aprendiz
     * opera sus rocas — no hay vista de mentor/admin construida todavia.
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
