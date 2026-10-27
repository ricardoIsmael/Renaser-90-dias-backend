package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarEstadoDelMapaPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.GuardarRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.domain.model.rocamaestra.MapaPendienteException;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class RocaMaestraService implements ConsultarRocasMaestrasUseCase, DefinirRocaMaestraUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RocaMaestraService.class);

    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final GuardarRocaMaestraPort guardarRocaMaestraPort;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final ConsultarEstadoDelMapaPort estadoDelMapaPort;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final boolean exigirMapaActivo;

    public RocaMaestraService(LoadRocaMaestraPort loadRocaMaestraPort,
                               GuardarRocaMaestraPort guardarRocaMaestraPort,
                               ConsultarProgresoParticipanteRocksPort progresoPort,
                               ConsultarEstadoDelMapaPort estadoDelMapaPort, Clock clock, IdGenerator idGenerator,
                               @Value("${renaser.rocks.exigir-mapa-activo:false}") boolean exigirMapaActivo) {
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.guardarRocaMaestraPort = guardarRocaMaestraPort;
        this.progresoPort = progresoPort;
        this.estadoDelMapaPort = estadoDelMapaPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.exigirMapaActivo = exigirMapaActivo;
    }

    @Override
    public List<RocaMaestra> misRocasMaestras(UserId actorId) {
        requireProgreso(actorId);
        return loadRocaMaestraPort.deParticipante(actorId);
    }

    /**
     * Define el objetivo del eje; que pasa si ya estaba depende del Mapa de la persona (D-284).
     *
     * <ul>
     *   <li><b>Mapa activo:</b> D-234. Por eje hay una sola Roca Maestra
     *       ({@code UNIQUE (participante_id, eje)}): si no existe se crea, y si existe decide
     *       {@link RocaMaestra#recibirDefinicion} — la misma definicion se acepta (idempotente), otro avance
     *       se registra, y cualquier cambio del objetivo, la meta, la unidad o el punto de partida se
     *       rechaza con {@code RocaMaestraFijaException} (409).
     *   <li><b>Mapa sin activar, escribiendolo el Mapa</b> ({@code desdeElMapa}): reemplaza a la roca que
     *       la persona hubiera definido antes desde Plan (issue #20).
     *   <li><b>Mapa sin activar, fuera del Mapa:</b> con {@code exigirMapaActivo} apagado se trata como el
     *       caso anterior (compatibilidad con el APK que no manda {@code desdeElMapa}); encendido, solo se
     *       acepta repetir la misma definicion y lo demas es {@code MapaPendienteException} (409).
     * </ul>
     *
     * > <b>Corregido 2026-09-30 (D-234).</b> Decia: "Define el objetivo del eje o corrige el que
     * > ya estaba [...] si existe se redefine". El dueno decidio que la Roca Maestra no se cambia.
     * > <b>Corregido 2026-10-27 (D-284).</b> Decia que quedaba fija "una vez definida": es una vez que el
     * > Mapa esta activo; antes, el Mapa manda.
     */
    @Override
    public RocaMaestra definir(DefinirRocaMaestraCommand command) {
        requireProgreso(command.actorId());
        MetaCuantitativa meta = command.tieneMeta()
                ? new MetaCuantitativa(command.meta(), command.avance(), command.unidad(), command.lineaBase())
                : null;
        Optional<RocaMaestra> existente = loadRocaMaestraPort.deParticipanteYEje(command.actorId(), command.eje());

        return guardarRocaMaestraPort.guardar(decidir(command, meta, existente));
    }

    private RocaMaestra decidir(DefinirRocaMaestraCommand command, MetaCuantitativa meta,
                                 Optional<RocaMaestra> existente) {
        if (estadoDelMapaPort.activo(command.actorId())) {
            return conElObjetivoFijo(command, meta, existente);
        }
        if (command.desdeElMapa() || !exigirMapaActivo) {
            return conLaDelMapa(command, meta, existente);
        }
        return existente.filter(roca -> roca.esLaMismaDefinicion(command.objetivo(), meta))
                .map(roca -> roca.recibirDefinicion(command.objetivo(), meta, clock.now()))
                .orElseThrow(MapaPendienteException::new);
    }

    /** D-234: el Mapa esta activo y el objetivo ya no se cambia; solo se crea la primera vez o se anota el avance. */
    private RocaMaestra conElObjetivoFijo(DefinirRocaMaestraCommand command, MetaCuantitativa meta,
                                           Optional<RocaMaestra> existente) {
        return existente.map(roca -> roca.recibirDefinicion(command.objetivo(), meta, clock.now()))
                .orElseGet(() -> nueva(command, meta));
    }

    /** D-284: el Mapa todavia no esta activo, asi que lo que el Mapa escribe reemplaza a lo que hubiera. */
    private RocaMaestra conLaDelMapa(DefinirRocaMaestraCommand command, MetaCuantitativa meta,
                                      Optional<RocaMaestra> existente) {
        return existente.map(roca -> reemplazar(roca, command, meta)).orElseGet(() -> nueva(command, meta));
    }

    private RocaMaestra reemplazar(RocaMaestra roca, DefinirRocaMaestraCommand command, MetaCuantitativa meta) {
        if (!roca.esLaMismaDefinicion(command.objetivo(), meta)) {
            LOG.info("[rocks] El objetivo de 90 dias del eje {} de {} se reemplaza: el Mapa todavia no esta "
                    + "activo (desdeElMapa={}).", command.eje(), command.actorId(), command.desdeElMapa());
        }
        return roca.reemplazarPorLaDelMapa(command.objetivo(), meta, clock.now());
    }

    private RocaMaestra nueva(DefinirRocaMaestraCommand command, MetaCuantitativa meta) {
        return RocaMaestra.definir(RocaMaestraId.of(idGenerator.newId()), command.actorId(), command.eje(),
                command.objetivo(), meta, clock.now());
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
