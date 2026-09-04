package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder.UsuarioConDiaPrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.participante.ActivateSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.AssignMentorToTraineeUseCase;
import com.renaser.os.users.application.ports.in.participante.AssignTraineeCellUseCase;
import com.renaser.os.users.application.ports.in.participante.ConsultarSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.DeactivateSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.GetTraineeDetailUseCase;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase;
import com.renaser.os.users.application.ports.in.participante.RemoveTraineeCellUseCase;
import com.renaser.os.users.application.ports.in.participante.SetTraineeProgramDayUseCase;
import com.renaser.os.users.application.ports.in.participante.UpdateTraineeProfileUseCase;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.participante.ConsultarResumenParticipacionPort;
import com.renaser.os.users.application.ports.out.participante.DeleteParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.ajustediaprograma.LoadUltimoAjusteDiaProgramaPort;
import com.renaser.os.users.application.ports.out.ajustediaprograma.SaveAjusteDiaProgramaPort;
import com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Casos de uso del 4to agregado de `users`: la inscripcion al programa de 90 dias
 * (`participantes_programa`). Tambien implementa {@link ParticipacionProgramaFinder}
 * (users/api): la unica forma en que otro modulo (o esta misma clase, para "consultar
 * mi propia participacion") deberia leer esta tabla.
 *
 * <p>Autorizacion como guard clause EN EL SERVICIO (CLAUDE.MD), no en el controller:
 * los tres casos de uso mutantes cargan al actor real via {@link RequireActiveUserGuard}
 * y verifican su rol antes de tocar nada.
 */
@Service
public class ParticipacionProgramaService implements ActivateSelfTrackingUseCase, DeactivateSelfTrackingUseCase,
        ConsultarSelfTrackingUseCase, AssignMentorToTraineeUseCase, ParticipacionProgramaFinder,
        ListTraineesUseCase, GetTraineeDetailUseCase, SetTraineeProgramDayUseCase, UpdateTraineeProfileUseCase,
        AssignTraineeCellUseCase, RemoveTraineeCellUseCase, AsignacionCelulaPort {

    /**
     * Mismo conjunto que {@code requireRole(auth.data, ['MENTOR', 'MENTOR_LEAD', 'ADMIN', 'ALCHEMIST'])}
     * del backend viejo (route.ts de activate-tracking). TRAINEE queda afuera a proposito:
     * su participacion es obligatoria y se crea al aprobar su cuenta, no por esta via
     * opcional de "seguimiento personal" de staff.
     */
    private static final Set<UserRole> ROLES_CON_SEGUIMIENTO_OPCIONAL =
            EnumSet.of(UserRole.MENTOR, UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST);

    private static final Logger log = LoggerFactory.getLogger(ParticipacionProgramaService.class);

    private final RequireActiveUserGuard requireActiveUserGuard;
    private final LoadParticipacionProgramaPort loadParticipacionProgramaPort;
    private final SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    private final DeleteParticipacionProgramaPort deleteParticipacionProgramaPort;
    private final ConsultarResumenParticipacionPort consultarResumenParticipacionPort;
    private final LoadMentorProfilePort loadMentorProfilePort;
    private final LoadUserPort loadUserPort;
    private final RequireAdminGuard requireAdminGuard;
    private final SaveAjusteDiaProgramaPort saveAjusteDiaProgramaPort;
    private final LoadUltimoAjusteDiaProgramaPort loadUltimoAjusteDiaProgramaPort;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ParticipacionProgramaService(RequireActiveUserGuard requireActiveUserGuard,
                                         LoadParticipacionProgramaPort loadParticipacionProgramaPort,
                                         SaveParticipacionProgramaPort saveParticipacionProgramaPort,
                                         DeleteParticipacionProgramaPort deleteParticipacionProgramaPort,
                                         ConsultarResumenParticipacionPort consultarResumenParticipacionPort,
                                         LoadMentorProfilePort loadMentorProfilePort, LoadUserPort loadUserPort,
                                         RequireAdminGuard requireAdminGuard,
                                         SaveAjusteDiaProgramaPort saveAjusteDiaProgramaPort,
                                         LoadUltimoAjusteDiaProgramaPort loadUltimoAjusteDiaProgramaPort,
                                         IdGenerator idGenerator, Clock clock) {
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.loadParticipacionProgramaPort = loadParticipacionProgramaPort;
        this.saveParticipacionProgramaPort = saveParticipacionProgramaPort;
        this.deleteParticipacionProgramaPort = deleteParticipacionProgramaPort;
        this.consultarResumenParticipacionPort = consultarResumenParticipacionPort;
        this.loadMentorProfilePort = loadMentorProfilePort;
        this.loadUserPort = loadUserPort;
        this.requireAdminGuard = requireAdminGuard;
        this.saveAjusteDiaProgramaPort = saveAjusteDiaProgramaPort;
        this.loadUltimoAjusteDiaProgramaPort = loadUltimoAjusteDiaProgramaPort;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ParticipacionPrograma activate(ActivateSelfTrackingCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        requireStaffRole(actor);

        if (loadParticipacionProgramaPort.byParticipanteId(actor.id()).isPresent()) {
            throw new IllegalStateException("Ya activaste tu seguimiento personal de habitos y objetivos");
        }

        ParticipacionPrograma nueva = ParticipacionPrograma.activarSeguimientoPersonal(actor.id(), clock);
        return saveParticipacionProgramaPort.save(nueva);
    }

    @Override
    @Transactional
    public boolean deactivate(DeactivateSelfTrackingCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        requireStaffRole(actor);

        return deleteParticipacionProgramaPort.deleteByParticipanteId(actor.id());
    }

    /**
     * Verifica el actor igual que {@link #activate}/{@link #deactivate} — a diferencia de
     * ellos NO exige rol de staff: un TRAINEE tiene participacion obligatoria y consultarla
     * es legitimo, lo que no puede es activarla/desactivarla por esta via (E-38).
     */
    @Override
    public boolean estaActivo(ConsultarSelfTrackingQuery query) {
        User actor = requireActiveUserGuard.of(query.actorId());
        return deParticipante(actor.id())
                .map(com.renaser.os.users.api.ParticipacionPrograma::inscrito)
                .orElse(false);
    }

    @Override
    @Transactional
    public void assignMentor(AssignMentorCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        if (!actor.canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST asignan mentor a un participante");
        }
        if (loadMentorProfilePort.byUserId(command.mentorId()).isEmpty()) {
            throw new NoSuchElementException("El usuario " + command.mentorId() + " no tiene perfil de mentor");
        }
        ParticipacionPrograma participacion = loadParticipacionProgramaPort.byParticipanteId(command.traineeId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Participante no inscripto en el programa: " + command.traineeId()));

        participacion.asignarMentor(command.mentorId(), clock);
        saveParticipacionProgramaPort.save(participacion);
    }

    /**
     * U-05 (hueco #1, `PATCH /api/v1/users/me/trainee-profile`). Self-only: el actor solo
     * edita su PROPIA fila (no recibe traineeId, solo actorId — igual criterio que
     * {@code UpdateMyProfileUseCase} en `users/api`).
     */
    @Override
    @Transactional
    public ParticipacionPrograma updateMyTraineeProfile(UpdateTraineeProfileCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        ParticipacionPrograma participacion = loadParticipacionProgramaPort.byParticipanteId(actor.id())
                .orElseThrow(() -> new NoSuchElementException(
                        "No tenes un perfil de programa activo para editar"));
        if (command.personalChallengeName() != null) {
            participacion.renombrarRetoPersonal(command.personalChallengeName(), clock);
        }
        return saveParticipacionProgramaPort.save(participacion);
    }

    /**
     * Nota de estilo: el tipo de retorno usa el nombre calificado completo a proposito
     * — {@code com.renaser.os.users.api.ParticipacionPrograma} (proyeccion publica) es
     * un tipo DISTINTO de {@link ParticipacionPrograma} (agregado interno, importado
     * arriba); ambos comparten nombre simple por diseño (mismo caso que {@code User} vs
     * {@code UserJpaEntity} en otros agregados, aca ademas coinciden en simple name).
     */
    @Override
    public Optional<com.renaser.os.users.api.ParticipacionPrograma> deParticipante(UserId participanteId) {
        return consultarResumenParticipacionPort.resumenDe(participanteId);
    }

    @Override
    public List<UserId> miembrosActivosDeCelula(UUID celulaId) {
        return consultarResumenParticipacionPort.miembrosActivosDeCelula(celulaId);
    }

    @Override
    public List<UserId> miembrosDeCelula(UUID celulaId) {
        return consultarResumenParticipacionPort.miembrosDeCelula(celulaId);
    }

    @Override
    public List<UserId> usuariosActivosConRol(Set<UserRole> roles) {
        return consultarResumenParticipacionPort.usuariosActivosConRol(roles);
    }

    @Override
    public List<UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(Set<UserRole> roles) {
        return consultarResumenParticipacionPort.usuariosActivosConDiaPrograma(roles);
    }

    @Override
    public List<UserId> participantesInscritosActivos() {
        return consultarResumenParticipacionPort.participantesInscritosActivos();
    }

    @Override
    public int contarMiembrosDeCelula(UUID celulaId) {
        return consultarResumenParticipacionPort.contarMiembrosDeCelula(celulaId);
    }

    private void requireStaffRole(User actor) {
        if (!ROLES_CON_SEGUIMIENTO_OPCIONAL.contains(actor.role())) {
            throw new NotAuthorizedException(
                    "El seguimiento personal opcional es solo para MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST");
        }
    }

    /**
     * Panel admin de aprendices (gap #7). Sin un recurso previo por id que proteger
     * (es un listado): el gate de admin va primero.
     */
    @Override
    public PaginaTrainees listar(ListTraineesCommand command) {
        requireAdminGuard.requireAdminActivo(command.actorId());
        var contenido = consultarResumenParticipacionPort.listarAprendices(command.page() * command.size(),
                command.size());
        long total = consultarResumenParticipacionPort.contarAprendices();
        return new PaginaTrainees(contenido, total, command.page(), command.size());
    }

    /**
     * El recurso (el propio aprendiz) se carga PRIMERO — 404 si no existe — y el gate de
     * admin va DESPUES (docs/BITACORA_ERRORES.md E-42), fail-closed via
     * {@link RequireAdminGuard}.
     */
    @Override
    public TraineeDetail obtener(GetTraineeDetailCommand command) {
        User trainee = requireUsuario(command.traineeId());
        requireAdminGuard.requireAdminActivo(command.actorId());
        var participacion = consultarResumenParticipacionPort.resumenDe(command.traineeId())
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + command.traineeId()));
        return new TraineeDetail(trainee, participacion,
                loadUltimoAjusteDiaProgramaPort.ultimoDe(command.traineeId()).orElse(null));
    }

    /** Mismo orden que {@link #obtener}: recurso primero, gate de admin despues (E-42). */
    @Override
    @Transactional
    public void fijarDia(SetProgramDayCommand command) {
        ParticipacionPrograma participacion = loadParticipacionProgramaPort.byParticipanteId(command.traineeId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Participante no inscripto en el programa: " + command.traineeId()));
        requireAdminGuard.requireAdminActivo(command.actorId());

        int diaAnterior = participacion.diaPrograma();
        int ajusteAnterior = participacion.diasAjuste();

        participacion.fijarDia(command.newProgramDay(), clock);
        saveParticipacionProgramaPort.save(participacion);

        // D-82: la bitacora va en la MISMA transaccion que el ajuste. Si se guardara
        // aparte (o por evento async), un fallo dejaria el dia movido sin rastro de quien
        // lo movio -- que es exactamente el agujero que esta tabla viene a cerrar.
        saveAjusteDiaProgramaPort.save(AjusteDiaPrograma.registrar(idGenerator.newId(), command.traineeId(), diaAnterior,
                participacion.diaPrograma(), ajusteAnterior, participacion.diasAjuste(), command.motivo(),
                command.actorId(), clock));
        log.info("[users] dia de programa de {} ajustado de {} a {} por {}", command.traineeId(), diaAnterior,
                participacion.diaPrograma(), command.actorId());
    }

    /**
     * Gap #25 — recurso primero (participacion), gate de admin despues (mismo orden que
     * {@link #fijarDia}, E-42). No valida la celula contra `community`: ver javadoc de
     * {@link AssignTraineeCellUseCase}.
     */
    @Override
    @Transactional
    public void assign(AssignTraineeCellCommand command) {
        ParticipacionPrograma participacion = loadParticipacionProgramaPort.byParticipanteId(command.traineeId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Participante no inscripto en el programa: " + command.traineeId()));
        requireAdminGuard.requireAdminActivo(command.actorId());

        participacion.asignarCelula(command.celulaId(), clock);
        saveParticipacionProgramaPort.save(participacion);
    }

    /** Contraparte de {@link #assign}. Mismo orden E-42 que el resto del panel admin. */
    @Override
    @Transactional
    public void remove(RemoveTraineeCellCommand command) {
        ParticipacionPrograma participacion = loadParticipacionProgramaPort.byParticipanteId(command.traineeId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Participante no inscripto en el programa: " + command.traineeId()));
        requireAdminGuard.requireAdminActivo(command.actorId());

        participacion.quitarCelula(clock);
        saveParticipacionProgramaPort.save(participacion);
    }

    /**
     * {@link AsignacionCelulaPort} (users/api): delega en {@link #assign}/{@link #remove}
     * — la unica diferencia es que ESTA interfaz es la que `community` puede importar
     * (gap #25), porque `community` es quien valida que la celula exista antes de llamar.
     */
    @Override
    public void asignarCelula(UserId actorId, UserId traineeId, UUID celulaId) {
        assign(new AssignTraineeCellCommand(actorId, traineeId, celulaId));
    }

    @Override
    public void quitarCelula(UserId actorId, UserId traineeId) {
        remove(new RemoveTraineeCellCommand(actorId, traineeId));
    }

    private User requireUsuario(UserId id) {
        return loadUserPort.byId(id).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + id));
    }
}
