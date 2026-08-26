package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder.UsuarioConDiaPrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.participante.ActivateSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.AssignMentorToTraineeUseCase;
import com.renaser.os.users.application.ports.in.participante.ConsultarSelfTrackingUseCase;
import com.renaser.os.users.application.ports.in.participante.DeactivateSelfTrackingUseCase;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.participante.ConsultarResumenParticipacionPort;
import com.renaser.os.users.application.ports.out.participante.DeleteParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.User;
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
        ConsultarSelfTrackingUseCase, AssignMentorToTraineeUseCase, ParticipacionProgramaFinder {

    /**
     * Mismo conjunto que {@code requireRole(auth.data, ['MENTOR', 'MENTOR_LEAD', 'ADMIN', 'ALCHEMIST'])}
     * del backend viejo (route.ts de activate-tracking). TRAINEE queda afuera a proposito:
     * su participacion es obligatoria y se crea al aprobar su cuenta, no por esta via
     * opcional de "seguimiento personal" de staff.
     */
    private static final Set<UserRole> ROLES_CON_SEGUIMIENTO_OPCIONAL =
            EnumSet.of(UserRole.MENTOR, UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST);

    private final RequireActiveUserGuard requireActiveUserGuard;
    private final LoadParticipacionProgramaPort loadParticipacionProgramaPort;
    private final SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    private final DeleteParticipacionProgramaPort deleteParticipacionProgramaPort;
    private final ConsultarResumenParticipacionPort consultarResumenParticipacionPort;
    private final LoadMentorProfilePort loadMentorProfilePort;
    private final Clock clock;

    public ParticipacionProgramaService(RequireActiveUserGuard requireActiveUserGuard,
                                         LoadParticipacionProgramaPort loadParticipacionProgramaPort,
                                         SaveParticipacionProgramaPort saveParticipacionProgramaPort,
                                         DeleteParticipacionProgramaPort deleteParticipacionProgramaPort,
                                         ConsultarResumenParticipacionPort consultarResumenParticipacionPort,
                                         LoadMentorProfilePort loadMentorProfilePort, Clock clock) {
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.loadParticipacionProgramaPort = loadParticipacionProgramaPort;
        this.saveParticipacionProgramaPort = saveParticipacionProgramaPort;
        this.deleteParticipacionProgramaPort = deleteParticipacionProgramaPort;
        this.consultarResumenParticipacionPort = consultarResumenParticipacionPort;
        this.loadMentorProfilePort = loadMentorProfilePort;
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

}
