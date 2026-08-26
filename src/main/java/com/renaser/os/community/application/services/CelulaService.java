package com.renaser.os.community.application.services;

import com.renaser.os.community.api.CelulaCreadaEvent;
import com.renaser.os.community.api.CelulaFinder;
import com.renaser.os.community.application.ports.in.celula.ActualizarCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.CrearCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.EliminarCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ProgramarSesionCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.QuitarMentorCelulaUseCase;
import com.renaser.os.community.application.ports.out.celula.EliminarCelulaPort;
import com.renaser.os.community.application.ports.out.celula.ExistePerfilMentorPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarCelulaDeParticipantePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarMiembrosCelulaPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class CelulaService implements CrearCelulaUseCase, ActualizarCelulaUseCase, AsignarMentorCelulaUseCase,
        QuitarMentorCelulaUseCase, ProgramarSesionCelulaUseCase, EliminarCelulaUseCase, ConsultarCelulasUseCase,
        ConsultarMiCelulaUseCase, CelulaFinder {

    private final LoadCelulaPort loadCelulaPort;
    private final SaveCelulaPort saveCelulaPort;
    private final EliminarCelulaPort eliminarCelulaPort;
    private final LoadCohortePort loadCohortePort;
    private final ExistePerfilMentorPort existePerfilMentorPort;
    private final ConsultarMiembrosCelulaPort consultarMiembrosCelulaPort;
    private final ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort;
    private final ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    private final UserSummaryFinder userSummaryFinder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CelulaService(LoadCelulaPort loadCelulaPort, SaveCelulaPort saveCelulaPort,
                          EliminarCelulaPort eliminarCelulaPort, LoadCohortePort loadCohortePort,
                          ExistePerfilMentorPort existePerfilMentorPort,
                          ConsultarMiembrosCelulaPort consultarMiembrosCelulaPort,
                          ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort,
                          ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort, UserSummaryFinder userSummaryFinder,
                          ApplicationEventPublisher events, Clock clock) {
        this.loadCelulaPort = loadCelulaPort;
        this.saveCelulaPort = saveCelulaPort;
        this.eliminarCelulaPort = eliminarCelulaPort;
        this.loadCohortePort = loadCohortePort;
        this.existePerfilMentorPort = existePerfilMentorPort;
        this.consultarMiembrosCelulaPort = consultarMiembrosCelulaPort;
        this.consultarCelulaDeParticipantePort = consultarCelulaDeParticipantePort;
        this.consultarPerfilUsuarioPort = consultarPerfilUsuarioPort;
        this.userSummaryFinder = userSummaryFinder;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Celula crear(CrearCelulaCommand command) {
        requireAdmin(command.actorId());
        requireCohorte(command.cohorteId());
        Celula celula = Celula.crear(command.nombre(), command.cohorteId(), command.urlVideollamada(), clock.now());
        Celula guardada = saveCelulaPort.save(celula);
        events.publishEvent(new CelulaCreadaEvent(guardada.id().value(), clock.now()));
        return guardada;
    }

    @Override
    @Transactional
    public Celula actualizar(ActualizarCelulaCommand command) {
        requireAdmin(command.actorId());
        Celula celula = requireCelula(command.celulaId());
        requireCohorteNoCompletada(celula.cohorteId());
        celula.actualizarDatos(command.nombre(), command.urlVideollamada(), command.tocaUrlVideollamada(),
                clock.now());
        return saveCelulaPort.save(celula);
    }

    @Override
    @Transactional
    public Celula asignar(AsignarMentorCelulaCommand command) {
        requireAdmin(command.actorId());
        Celula celula = requireCelula(command.celulaId());
        UserSummary lider = userSummaryFinder.findById(command.mentorId())
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + command.mentorId()));
        if (lider.status() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("El usuario seleccionado no esta activo");
        }
        if (lider.role() != UserRole.MENTOR && lider.role() != UserRole.ADMIN && lider.role() != UserRole.ALCHEMIST) {
            throw new IllegalArgumentException("El usuario seleccionado no puede liderar una celula");
        }
        if (!existePerfilMentorPort.existe(command.mentorId())) {
            throw new IllegalStateException(
                    "El usuario todavia no tiene un perfil de mentor (perfiles_mentor) — debe crearse desde "
                            + "el modulo de usuarios antes de poder liderar una celula");
        }
        Optional<Celula> otraCelula = loadCelulaPort.porMentor(command.mentorId());
        if (otraCelula.isPresent() && !otraCelula.get().id().equals(celula.id())) {
            throw new IllegalStateException("Ese mentor ya lidera otra celula");
        }
        celula.asignarMentor(command.mentorId(), clock.now());
        return saveCelulaPort.save(celula);
    }

    @Override
    @Transactional
    public Celula quitar(QuitarMentorCelulaCommand command) {
        requireAdmin(command.actorId());
        Celula celula = requireCelula(command.celulaId());
        celula.quitarMentor(clock.now());
        return saveCelulaPort.save(celula);
    }

    @Override
    @Transactional
    public Celula programar(ProgramarSesionCelulaCommand command) {
        requireAdmin(command.actorId());
        Celula celula = requireCelula(command.celulaId());
        celula.programarSesion(command.proximaSesionEn(), clock.now());
        return saveCelulaPort.save(celula);
    }

    @Override
    @Transactional
    public void eliminar(EliminarCelulaCommand command) {
        requireAdmin(command.actorId());
        requireCelula(command.celulaId());
        eliminarCelulaPort.eliminar(command.celulaId());
    }

    @Override
    public List<CelulaResumen> listarPorCohorte(UserId actorId, CohorteId cohorteId) {
        UserSummary actor = requireActorActivo(actorId);
        List<Celula> celulas;
        if (actor.role() == UserRole.MENTOR) {
            celulas = loadCelulaPort.porMentor(actorId)
                    .filter(c -> c.cohorteId().equals(cohorteId))
                    .map(List::of).orElseGet(List::of);
        } else {
            requireRolAdmin(actor);
            celulas = loadCelulaPort.porCohorte(cohorteId);
        }
        return celulas.stream().map(this::aResumen).toList();
    }

    @Override
    public CelulaDetalle obtener(UserId actorId, CelulaId celulaId) {
        UserSummary actor = requireActorActivo(actorId);
        Celula celula = requireCelula(celulaId);
        if (actor.role() == UserRole.MENTOR) {
            if (celula.mentorId() == null || !celula.mentorId().equals(actorId)) {
                throw new NotAuthorizedException("No lideras esta celula");
            }
        } else {
            requireRolAdmin(actor);
        }
        PerfilBasico mentor = celula.mentorId() != null ? perfilBasico(celula.mentorId()) : null;
        List<PerfilBasico> miembros = consultarMiembrosCelulaPort.deCelula(celulaId).stream()
                .map(this::perfilBasico).toList();
        return new CelulaDetalle(celula, mentor, miembros);
    }

    @Override
    public Optional<MiCelula> miCelula(UserId traineeId) {
        requireActorActivo(traineeId);
        CelulaId celulaId = consultarCelulaDeParticipantePort.celulaDeUsuario(traineeId).orElse(null);
        if (celulaId == null) {
            return Optional.empty();
        }
        Celula celula = requireCelula(celulaId);
        Cohorte cohorte = requireCohorte(celula.cohorteId());
        PerfilBasico mentor = celula.mentorId() != null ? perfilBasico(celula.mentorId()) : null;
        int cantidadMiembros = consultarMiembrosCelulaPort.contarMiembros(celulaId);
        int totalCelulas = loadCelulaPort.porCohorte(celula.cohorteId()).size();
        return Optional.of(new MiCelula(celula, cohorte, mentor, cantidadMiembros, totalCelulas));
    }

    @Override
    public List<PerfilBasico> misCompaneros(UserId traineeId) {
        requireActorActivo(traineeId);
        return consultarCelulaDeParticipantePort.celulaDeUsuario(traineeId)
                .map(celulaId -> consultarMiembrosCelulaPort.deCelula(celulaId).stream()
                        .map(this::perfilBasico).toList())
                .orElseGet(List::of);
    }

    private CelulaResumen aResumen(Celula celula) {
        int cantidad = consultarMiembrosCelulaPort.contarMiembros(celula.id());
        PerfilBasico mentor = celula.mentorId() != null ? perfilBasico(celula.mentorId()) : null;
        return new CelulaResumen(celula, cantidad, mentor);
    }

    private PerfilBasico perfilBasico(UserId usuarioId) {
        return consultarPerfilUsuarioPort.porId(usuarioId)
                .map(p -> new PerfilBasico(p.id(), p.nombreCompleto(), p.avatarUrl()))
                .orElse(new PerfilBasico(usuarioId, null, null));
    }

    private void requireCohorteNoCompletada(CohorteId cohorteId) {
        if (requireCohorte(cohorteId).estado() == EstadoCohorte.COMPLETADA) {
            throw new NotAuthorizedException("No se pueden modificar celulas de una cohorte completada");
        }
    }

    private Celula requireCelula(CelulaId id) {
        return loadCelulaPort.porId(id).orElseThrow(() -> new NoSuchElementException("Celula no encontrada: " + id));
    }

    private Cohorte requireCohorte(CohorteId id) {
        return loadCohortePort.porId(id).orElseThrow(() -> new NoSuchElementException("Cohorte no encontrada: " + id));
    }

    private UserSummary requireActorActivo(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        return actor;
    }

    private void requireAdmin(UserId actorId) {
        requireRolAdmin(requireActorActivo(actorId));
    }

    private void requireRolAdmin(UserSummary actor) {
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran celulas");
        }
    }
    /** {@link CelulaFinder} — contrato publico consumido por `calendar` (D-41). */
    @Override
    public Optional<UserId> mentorDe(UUID celulaId) {
        return loadCelulaPort.porId(new CelulaId(celulaId)).map(Celula::mentorId);
    }

}
