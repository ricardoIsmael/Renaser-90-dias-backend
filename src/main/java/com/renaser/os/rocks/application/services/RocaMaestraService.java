package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarObjetivosDelDiagnosticoPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.GuardarRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ConsultarObjetivosDelDiagnosticoPort diagnosticoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public RocaMaestraService(LoadRocaMaestraPort loadRocaMaestraPort,
                               GuardarRocaMaestraPort guardarRocaMaestraPort,
                               ConsultarProgresoParticipanteRocksPort progresoPort,
                               ConsultarObjetivosDelDiagnosticoPort diagnosticoPort, Clock clock, IdGenerator idGenerator) {
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.guardarRocaMaestraPort = guardarRocaMaestraPort;
        this.progresoPort = progresoPort;
        this.diagnosticoPort = diagnosticoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<RocaMaestra> misRocasMaestras(UserId actorId) {
        requireProgreso(actorId);
        return conLasDelDiagnostico(actorId);
    }

    /**
     * Define el objetivo del eje, o lo confirma, o registra su avance. Sin Mapa que decida nada (spec 026, D-303):
     *
     * <ul>
     *   <li><b>No existe:</b> se crea (queda fijo desde ya, como siempre desde D-234).
     *   <li><b>Nació del diagnóstico y está por confirmar:</b> la persona lo puede reescribir UNA vez; el pedido lo reemplaza (o lo deja igual) y lo
     *       confirma ({@link RocaMaestra#recibirDefinicion}).
     *   <li><b>Existe y está confirmado:</b> la misma definición es idempotente, otro avance se registra y cualquier cambio del objetivo, la meta, la unidad
     *       o el punto de partida lanza {@code RocaMaestraFijaException} (409).
     * </ul>
     *
     * Antes de decidir se le materializan los objetivos que nacieron de su diagnóstico, si los hay: así «por confirmar» vale aunque Plan todavía no los haya
     * leído. {@code desdeElMapa} se acepta y se ignora (la app anterior lo manda).
     *
     * > <b>Corregido 2026-09-30 (D-234).</b> Decia: "Define el objetivo del eje o corrige el que ya estaba [...] si existe se redefine". El dueno decidio que
     * > la Roca Maestra no se cambia.
     * > <b>Corregido 2026-10-27 (D-284)</b> y <b>2026-10-31 (D-303):</b> la fijeza dependio un tiempo del Mapa activo y de {@code ROCKS_EXIGIR_MAPA_ACTIVO};
     * > el Mapa se retiro y nadie queda bloqueado por no haberlo hecho.
     */
    @Override
    public RocaMaestra definir(DefinirRocaMaestraCommand command) {
        requireProgreso(command.actorId());
        conLasDelDiagnostico(command.actorId());
        MetaCuantitativa meta = command.tieneMeta()
                ? new MetaCuantitativa(command.meta(), command.avance(), command.unidad(), command.lineaBase())
                : null;
        Optional<RocaMaestra> existente = loadRocaMaestraPort.deParticipanteYEje(command.actorId(), command.eje());
        RocaMaestra decidida = existente.map(roca -> roca.recibirDefinicion(command.objetivo(), meta, clock.now()))
                .orElseGet(() -> nueva(command, meta));
        return guardarRocaMaestraPort.guardar(decidida);
    }

    /**
     * Las rocas de la persona, creando las que nacieron de su diagnóstico y todavía no existen (por confirmar). Si dos pedidos lo hacen a la vez gana uno;
     * al otro lo frena el {@code UNIQUE (participante_id, eje)} y vuelve a leer lo que dejó el primero.
     */
    private List<RocaMaestra> conLasDelDiagnostico(UserId actorId) {
        List<RocaMaestra> actuales = loadRocaMaestraPort.deParticipante(actorId);
        if (actuales.size() >= EjeObjetivo.values().length) {
            return actuales;
        }
        boolean creo = false;
        for (var objetivo : diagnosticoPort.de(actorId)) {
            if (actuales.stream().noneMatch(roca -> roca.eje() == objetivo.eje())) {
                creo |= crearDelDiagnostico(actorId, objetivo.eje(), objetivo.texto());
            }
        }
        return creo ? loadRocaMaestraPort.deParticipante(actorId) : actuales;
    }

    private boolean crearDelDiagnostico(UserId actorId, EjeObjetivo eje, String texto) {
        try {
            guardarRocaMaestraPort.guardar(RocaMaestra.delDiagnostico(RocaMaestraId.of(idGenerator.newId()), actorId, eje, texto, clock.now()));
            return true;
        } catch (RuntimeException e) {
            LOG.info("[rocks] El objetivo del eje {} de {} ya lo habia creado otro pedido: {}", eje, actorId, e.getClass().getSimpleName());
            return true;
        }
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
