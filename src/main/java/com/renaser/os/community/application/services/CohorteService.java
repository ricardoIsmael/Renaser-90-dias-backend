package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.cohorte.ActualizarCohorteUseCase;
import com.renaser.os.community.application.ports.in.cohorte.CambiarEstadoCohorteUseCase;
import com.renaser.os.community.application.ports.in.cohorte.ConsultarCohortesUseCase;
import com.renaser.os.community.application.ports.in.cohorte.CrearCohorteUseCase;
import com.renaser.os.community.application.ports.in.cohorte.EliminarCohorteUseCase;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.EliminarCohortePort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.cohorte.SaveCohortePort;
import com.renaser.os.community.domain.model.celula.Celula;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class CohorteService implements CrearCohorteUseCase, ActualizarCohorteUseCase, CambiarEstadoCohorteUseCase,
        EliminarCohorteUseCase, ConsultarCohortesUseCase {

    private final LoadCohortePort loadCohortePort;
    private final SaveCohortePort saveCohortePort;
    private final EliminarCohortePort eliminarCohortePort;
    private final LoadCelulaPort loadCelulaPort;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    public CohorteService(LoadCohortePort loadCohortePort, SaveCohortePort saveCohortePort,
                           EliminarCohortePort eliminarCohortePort, LoadCelulaPort loadCelulaPort,
                           UserSummaryFinder userSummaryFinder, Clock clock) {
        this.loadCohortePort = loadCohortePort;
        this.saveCohortePort = saveCohortePort;
        this.eliminarCohortePort = eliminarCohortePort;
        this.loadCelulaPort = loadCelulaPort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CohorteResumen crear(CrearCohorteCommand command) {
        requireAdmin(command.actorId());
        Cohorte cohorte = Cohorte.crear(command.nombre(), command.fechaInicio(), command.fechaFin(), clock.now());
        return aResumen(saveCohortePort.save(cohorte));
    }

    @Override
    @Transactional
    public CohorteResumen actualizar(ActualizarCohorteCommand command) {
        requireAdmin(command.actorId());
        Cohorte cohorte = requireCohorte(command.cohorteId());
        cohorte.actualizarDatos(command.nombre(), command.fechaInicio(),
                command.tocaFechaFin() ? command.fechaFin() : cohorte.fechaFin(), clock.now());
        return aResumen(saveCohortePort.save(cohorte));
    }

    @Override
    @Transactional
    public CohorteResumen cambiarEstado(CambiarEstadoCohorteCommand command) {
        requireAdmin(command.actorId());
        Cohorte cohorte = requireCohorte(command.cohorteId());
        cohorte.transicionarA(command.nuevoEstado(), clock.now());
        return aResumen(saveCohortePort.save(cohorte));
    }

    @Override
    @Transactional
    public void eliminar(EliminarCohorteCommand command) {
        requireAdmin(command.actorId());
        int celulas = loadCohortePort.contarCelulas(command.cohorteId());
        if (celulas > 0) {
            throw new IllegalStateException("No se puede eliminar: tiene " + celulas
                    + " celula(s) asociada(s). Elimina las celulas primero.");
        }
        eliminarCohortePort.eliminar(command.cohorteId());
    }

    @Override
    public List<CohorteResumen> listar(UserId actorId, EstadoCohorte filtroEstado) {
        UserSummary actor = requireActorActivo(actorId);
        if (actor.role() == UserRole.MENTOR) {
            Optional<Celula> propia = loadCelulaPort.porMentor(actorId);
            if (propia.isEmpty()) {
                return List.of();
            }
            return requireCohorte(propia.get().cohorteId()).estado() == EstadoCohorte.COMPLETADA
                    ? List.of()
                    : List.of(aResumen(propia.get().cohorteId()));
        }
        requireRolAdmin(actor);
        return loadCohortePort.listar(filtroEstado).stream().map(c -> aResumen(c.id())).toList();
    }

    @Override
    public CohorteResumen obtener(UserId actorId, CohorteId cohorteId) {
        UserSummary actor = requireActorActivo(actorId);
        if (actor.role() == UserRole.MENTOR) {
            Celula propia = loadCelulaPort.porMentor(actorId)
                    .orElseThrow(() -> new NotAuthorizedException("No lideras ninguna celula"));
            if (!propia.cohorteId().equals(cohorteId)) {
                throw new NotAuthorizedException("No tienes acceso a esta cohorte");
            }
        } else {
            requireRolAdmin(actor);
        }
        return aResumen(cohorteId);
    }

    private CohorteResumen aResumen(CohorteId id) {
        return aResumen(requireCohorte(id));
    }

    /** Proyeccion que la API devuelve al crear/actualizar/cambiar estado, armada DENTRO de
     * la transaccion de la mutacion: el controller ya no encadena "muto y despues
     * consulto" (dos transacciones, respuesta posiblemente desactualizada). */
    private CohorteResumen aResumen(Cohorte cohorte) {
        return new CohorteResumen(cohorte, loadCohortePort.contarCelulas(cohorte.id()));
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
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran cohortes");
        }
    }
}
