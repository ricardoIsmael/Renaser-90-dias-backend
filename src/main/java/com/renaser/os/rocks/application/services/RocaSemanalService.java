package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.SaveRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica;
import com.renaser.os.rocks.domain.model.rocasemanal.EstadoPlazo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.rocks.domain.model.rocasemanal.VentanaPlanificacionSemanal;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class RocaSemanalService implements CrearPlanSemanalUseCase, EditarDentroDe48hUseCase, CerrarSemanaUseCase,
        ConsultarRocasSemanalesUseCase {

    private static final Set<EjeObjetivo> LOS_TRES_EJES = Set.of(EjeObjetivo.CUERPO, EjeObjetivo.TRABAJO,
            EjeObjetivo.RELACIONES);

    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final LoadRocaSemanalPort loadRocaSemanalPort;
    private final SaveRocaSemanalPort saveRocaSemanalPort;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public RocaSemanalService(LoadRocaMaestraPort loadRocaMaestraPort, LoadRocaSemanalPort loadRocaSemanalPort,
                               SaveRocaSemanalPort saveRocaSemanalPort,
                               ConsultarProgresoParticipanteRocksPort progresoPort, Clock clock,
                               IdGenerator idGenerator) {
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.loadRocaSemanalPort = loadRocaSemanalPort;
        this.saveRocaSemanalPort = saveRocaSemanalPort;
        this.progresoPort = progresoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public List<RocaSemanal> crear(CrearPlanSemanalCommand command) {
        ProgresoParticipanteRocks progreso = requireProgreso(command.actorId());
        Map<EjeObjetivo, RocaMaestra> maestras = requireRocasMaestrasCompletas(command.actorId());
        requireUnEjePorItem(command.rocas());

        LocalDate hoy = hoyEn(progreso.zona());
        int numeroSemana = numeroSemanaAPlanificar(progreso, hoy);

        List<RocaMaestraId> idsMaestras = maestras.values().stream().map(RocaMaestra::id).toList();
        if (!loadRocaSemanalPort.deParticipanteYSemana(idsMaestras, numeroSemana).isEmpty()) {
            throw new IllegalStateException("ALREADY_PLANNED: la semana " + numeroSemana + " ya tiene rocas planificadas");
        }

        List<RocaSemanal> creadas = command.rocas().stream()
                // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD §5.4.7).
                .map(item -> RocaSemanal.planificar(RocaSemanalId.of(idGenerator.newId()),
                        maestras.get(item.eje()).id(), numeroSemana, item.titulo(),
                        acciones(item.accionCritica1(), item.accionCritica2(), item.accionCritica3()),
                        item.obstaculo(), item.contingencia(), item.autoevaluacionInicio(), clock))
                .map(saveRocaSemanalPort::save)
                .toList();
        return creadas;
    }

    @Override
    @Transactional
    public RocaSemanal editar(EditarRocaSemanalCommand command) {
        ProgresoParticipanteRocks progreso = requireProgreso(command.actorId());
        RocaSemanal rocaSemanal = requireRocaSemanalPropia(command.actorId(), command.rocaSemanalId());

        EstadoPlazo plazoAlCrear = VentanaPlanificacionSemanal.plazoAlCrear(rocaSemanal.creadoEn(), progreso.zona());
        boolean editable = VentanaPlanificacionSemanal.puedeEditar(plazoAlCrear, rocaSemanal.creadoEn(), clock.now(),
                progreso.zona());
        if (!editable) {
            throw new NotAuthorizedException("La ventana para editar esta roca semanal ya cerro");
        }

        List<AccionCritica> acciones = command.accionesCriticas() == null ? null
                : acciones(command.accionesCriticas().get(0), command.accionesCriticas().get(1),
                        command.accionesCriticas().get(2));
        rocaSemanal.actualizarPlanificacion(command.titulo(), acciones, command.obstaculo(), command.contingencia(),
                command.autoevaluacionInicio(), clock);
        return saveRocaSemanalPort.save(rocaSemanal);
    }

    @Override
    @Transactional
    public RocaSemanal cerrar(CerrarSemanaCommand command) {
        requireProgreso(command.actorId());
        RocaSemanal rocaSemanal = requireRocaSemanalPropia(command.actorId(), command.rocaSemanalId());
        rocaSemanal.registrarRevision(command.autoevaluacionFin(), command.bloqueoPrincipal(), command.correccion(),
                clock);
        return saveRocaSemanalPort.save(rocaSemanal);
    }

    @Override
    public List<RocaSemanal> misRocasSemanales(UserId actorId, Integer numeroSemana) {
        ProgresoParticipanteRocks progreso = requireProgreso(actorId);
        List<RocaMaestraId> idsMaestras = loadRocaMaestraPort.deParticipante(actorId).stream()
                .map(RocaMaestra::id).toList();
        int semana = numeroSemana != null ? numeroSemana
                : SemanaPrograma.numeroSemanaParaFecha(progreso.fechaInicio(), hoyEn(progreso.zona()));
        return loadRocaSemanalPort.deParticipanteYSemana(idsMaestras, semana);
    }

    /**
     * Qué semana se está planificando. El +1 SOLO vale el domingo: las semanas
     * son lunes-domingo, asi que planificar el domingo prepara la que empieza
     * mañana; cualquier otro día ya se está dentro de la que se quiere llenar.
     */
    private int numeroSemanaAPlanificar(ProgresoParticipanteRocks progreso, LocalDate hoy) {
        int semanaDeHoy = SemanaPrograma.numeroSemanaParaFecha(progreso.fechaInicio(), hoy);
        return hoy.getDayOfWeek() == DayOfWeek.SUNDAY ? semanaDeHoy + 1 : semanaDeHoy;
    }

    private LocalDate hoyEn(ZoneId zona) {
        return clock.now().atZone(zona).toLocalDate();
    }

    /**
     * Las acciones que hayan venido, numeradas de corrido. Vacias se saltean: desde que las acciones
     * viven en el objetivo diario (V61) el asistente ya no las pide, y armar
     * {@code new AccionCritica(1, null)} reventaria con "descripcion es obligatoria".
     */
    private static List<AccionCritica> acciones(String a1, String a2, String a3) {
        List<AccionCritica> criticas = new java.util.ArrayList<>();
        for (String texto : List.of(a1 == null ? "" : a1, a2 == null ? "" : a2, a3 == null ? "" : a3)) {
            if (!texto.isBlank()) {
                criticas.add(new AccionCritica(criticas.size() + 1, texto));
            }
        }
        return List.copyOf(criticas);
    }

    /**
     * Un objetivo semanal por eje, sin repetir. <b>No hace falta mandar los tres.</b>
     *
     * > <b>Corregido el 2026-09-22 (E-205).</b> Exigia {@code ejes.equals(LOS_TRES_EJES)}, o sea los
     * > tres si o si. Unas horas antes se habia relajado {@code @Size(min = 3, max = 3)} a
     * > {@code min = 1} en el comando creyendo que eso alcanzaba, y no: mandar un solo eje pasaba la
     * > validacion del comando y moria aca con "se requiere exactamente una roca semanal por eje".
     * > La regla de los tres estaba escrita en DOS lugares y solo se cambio uno.
     *
     * Cuantos vienen lo acota {@code @Size(min = 1, max = 3)}; lo que se verifica aca es lo otro:
     * que no lleguen dos del mismo eje, que dejaria a un eje con dos objetivos para la misma semana.
     */
    private static void requireUnEjePorItem(List<ItemRocaSemanal> rocas) {
        Set<EjeObjetivo> ejes = rocas.stream().map(ItemRocaSemanal::eje).collect(java.util.stream.Collectors.toSet());
        if (ejes.size() != rocas.size()) {
            throw new IllegalArgumentException("no se puede planificar dos objetivos semanales del mismo eje");
        }
    }

    private Map<EjeObjetivo, RocaMaestra> requireRocasMaestrasCompletas(UserId actorId) {
        List<RocaMaestra> maestras = loadRocaMaestraPort.deParticipante(actorId);
        if (maestras.size() < LOS_TRES_EJES.size()) {
            throw new NotAuthorizedException("ROCKS_LOCKED: completa tu onboarding antes de planificar rocas");
        }
        return maestras.stream().collect(java.util.stream.Collectors.toMap(RocaMaestra::eje, m -> m));
    }

    private RocaSemanal requireRocaSemanalPropia(UserId actorId, RocaSemanalId id) {
        RocaSemanal rocaSemanal = loadRocaSemanalPort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Roca semanal no encontrada: " + id));
        boolean esPropia = loadRocaMaestraPort.deParticipante(actorId).stream()
                .anyMatch(m -> m.id().equals(rocaSemanal.rocaMaestraId()));
        if (!esPropia) {
            throw new NotAuthorizedException("Esta roca semanal no pertenece al actor");
        }
        return rocaSemanal;
    }

    /** SUSPENDIDO -> 403. Rol distinto de TRAINEE -> 403 (solo el aprendiz opera sus rocas). */
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
