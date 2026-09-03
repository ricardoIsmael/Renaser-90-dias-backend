package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.grabacionv90.ListarGrabacionesV90UseCase;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.RegistrarGrabacionV90UseCase;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.DespacharValidacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.LoadGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.SaveGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.media.LoadMediaPort;
import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * NO implementa {@code ProcesarValidacionV90UseCase} (ver {@link ProcesarValidacionV90Service} —
 * separado a proposito, E-34 en {@code docs/BITACORA_ERRORES.md}: evita una dependencia
 * circular con el adapter {@code @Async} que dispara ese caso de uso).
 */
@Service
public class GrabacionV90Service implements RegistrarGrabacionV90UseCase, ListarGrabacionesV90UseCase,
        ValidarV90UseCase {

    private final LoadGrabacionV90Port loadGrabacionPort;
    private final SaveGrabacionV90Port saveGrabacionPort;
    private final LoadMediaPort loadMediaPort;
    private final DespacharValidacionV90Port despacharPort;
    private final ConsultarActorPort actorPort;
    private final Clock clock;

    public GrabacionV90Service(LoadGrabacionV90Port loadGrabacionPort, SaveGrabacionV90Port saveGrabacionPort,
                                LoadMediaPort loadMediaPort, DespacharValidacionV90Port despacharPort,
                                ConsultarActorPort actorPort, Clock clock) {
        this.loadGrabacionPort = loadGrabacionPort;
        this.saveGrabacionPort = saveGrabacionPort;
        this.loadMediaPort = loadMediaPort;
        this.despacharPort = despacharPort;
        this.actorPort = actorPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public GrabacionV90 registrar(RegistrarGrabacionV90Command command) {
        requireActorActivo(command.usuarioId());
        loadMediaPort.porIdYUsuario(command.mediaId(), command.usuarioId())
                .orElseThrow(() -> new NoSuchElementException("Media no encontrada para este usuario: " + command.mediaId()));

        GrabacionV90 grabacion = loadGrabacionPort
                .porSlot(command.usuarioId(), command.fase(), command.eje(), command.indice())
                .orElseGet(() -> GrabacionV90.crearSlot(command.usuarioId(), command.fase(), command.eje(),
                        command.indice(), command.clavePregunta(), clock));

        grabacion.marcarGrabada(command.mediaId(), command.duracionSegundos(), command.transcripcion(), clock);
        return saveGrabacionPort.guardar(grabacion);
    }

    @Override
    public List<GrabacionV90> listar(UserId usuarioId) {
        requireActorActivo(usuarioId);
        return loadGrabacionPort.todasDeUsuario(usuarioId);
    }

    /**
     * C-3 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): {@code
     * requireGrabacionPropiaParaEscritura} lee CON bloqueo pesimista, asi que dos POST
     * concurrentes sobre la misma grabacion ya no pueden leer ambas {@code PENDIENTE} antes
     * de que cualquiera escriba — la segunda transaccion queda bloqueada hasta que la primera
     * comprometa, y al leer despues ve el {@code PROCESANDO} ya comprometido. En ese caso no
     * se vuelve a llamar a {@code procesarIntentoDeValidacion} (que rechazaria con un 409) ni
     * se despacha una segunda validacion IA: se responde el mismo 202 idempotente que ya
     * define el contrato (CLAUDE.MD §7) — el aprendiz que doble-clickea o cuyo cliente
     * reintenta por timeout ve exactamente el mismo resultado que si su request hubiera sido
     * la unica.
     */
    @Override
    @Transactional
    public void solicitarValidacion(SolicitarValidacionV90Command command) {
        requireActorActivo(command.usuarioId());
        GrabacionV90 grabacion = requireGrabacionPropiaParaEscritura(command.usuarioId(), command.grabacionId());
        if (grabacion.estadoIa() == EstadoIAv90.PROCESANDO) {
            return;
        }
        grabacion.procesarIntentoDeValidacion(clock);
        saveGrabacionPort.guardar(grabacion);
        despacharDespuesDelCommit(command.usuarioId(), command.grabacionId());
    }

    /**
     * El hilo {@code @Async} corre en su propia transaccion, sobre otra conexion — si se
     * dispara ANTES de que esta transaccion haga commit, bajo READ_COMMITTED puede leer el
     * {@code intentosIa}/{@code estadoIa} previos (todavia no comprometidos) y pisarlos al
     * guardar (lost update, auditoria de concurrencia, docs/BITACORA_ERRORES.md E-37).
     * Mismo patron que {@code chat.MensajeService.publicarDespuesDelCommit}.
     */
    private void despacharDespuesDelCommit(UserId usuarioId, long grabacionId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            despacharPort.despachar(usuarioId, grabacionId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                despacharPort.despachar(usuarioId, grabacionId);
            }
        });
    }

    @Override
    public EstadoValidacionV90 consultarEstado(ConsultarEstadoV90Query query) {
        requireActorActivo(query.usuarioId());
        GrabacionV90 grabacion = requireGrabacionPropia(query.usuarioId(), query.grabacionId());
        return new EstadoValidacionV90(grabacion.estadoIa(), grabacion.intentosIa(), grabacion.feedbackIa());
    }

    private GrabacionV90 requireGrabacionPropia(UserId usuarioId, long grabacionId) {
        GrabacionV90 grabacion = loadGrabacionPort.porId(grabacionId)
                .orElseThrow(() -> new NoSuchElementException("Grabacion no encontrada: " + grabacionId));
        if (!grabacion.usuarioId().equals(usuarioId)) {
            throw new NotAuthorizedException("Esta grabacion no pertenece al usuario");
        }
        return grabacion;
    }

    /** Igual que {@link #requireGrabacionPropia}, pero con bloqueo pesimista (C-3) — solo
     * para el camino de escritura de {@link #solicitarValidacion}. Las lecturas puras
     * ({@link #consultarEstado}) siguen sin lock: el polling ocurre mucho mas seguido que la
     * escritura y no tiene sentido pagar contencion de fila por el. */
    private GrabacionV90 requireGrabacionPropiaParaEscritura(UserId usuarioId, long grabacionId) {
        GrabacionV90 grabacion = loadGrabacionPort.porIdParaEscritura(grabacionId)
                .orElseThrow(() -> new NoSuchElementException("Grabacion no encontrada: " + grabacionId));
        if (!grabacion.usuarioId().equals(usuarioId)) {
            throw new NotAuthorizedException("Esta grabacion no pertenece al usuario");
        }
        return grabacion;
    }

    private void requireActorActivo(UserId actorId) {
        ConsultarActorPort.ActorOnboarding actor = actorPort.deActor(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}
