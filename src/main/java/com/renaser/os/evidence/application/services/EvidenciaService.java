package com.renaser.os.evidence.application.services;

import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ConsultarEvidenciaUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ProcesarColaValidacionUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.RevisarManualmenteUseCase;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.evidencia.SaveEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.ia.ResultadoValidacionIA;
import com.renaser.os.evidence.application.ports.out.ia.ValidacionIAPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Único servicio de aplicación del módulo. Implementa {@link RegistrarEvidenciaPort}
 * directamente (es, a la vez, el puerto público y el "in" de esa operación — ver su
 * javadoc) además de los casos de uso internos (consulta, revisión manual, anulación,
 * cola de validación).
 */
@Service
public class EvidenciaService implements RegistrarEvidenciaPort, ConsultarEvidenciaUseCase, RevisarManualmenteUseCase,
        AnularVeredictoUseCase, ProcesarColaValidacionUseCase {

    private static final int TAMANO_LOTE = 25;

    private final LoadEvidenciaPort loadEvidenciaPort;
    private final SaveEvidenciaPort saveEvidenciaPort;
    private final ValidacionIAPort validacionIAPort;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    public EvidenciaService(LoadEvidenciaPort loadEvidenciaPort, SaveEvidenciaPort saveEvidenciaPort,
                             ValidacionIAPort validacionIAPort, UserSummaryFinder userSummaryFinder, Clock clock) {
        this.loadEvidenciaPort = loadEvidenciaPort;
        this.saveEvidenciaPort = saveEvidenciaPort;
        this.validacionIAPort = validacionIAPort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    /**
     * Defensa en profundidad (CLAUDE.MD §0.3): {@code rocks}/{@code habits} ya validan
     * que el actor no esté suspendido antes de llamar acá, pero {@code evidence} no
     * confía ciegamente en el llamador — vuelve a chequear el estado de la cuenta.
     */
    @Override
    @Transactional
    public EvidenciaRegistrada registrar(RegistrarEvidenciaComando comando) {
        requireActivo(comando.participanteId());
        Evidencia evidencia = Evidencia.registrar(comando.participanteId(), comando.destino(), comando.tipo(),
                comando.bucket(), comando.rutaStorage(), comando.contenidoTexto(), comando.timestampExif(),
                comando.gpsLat(), comando.gpsLng(), comando.esPrincipal(), comando.subidaEn(), clock);
        Evidencia guardada = saveEvidenciaPort.save(evidencia);
        return new EvidenciaRegistrada(guardada.id().value(), guardada.estadoValidacion());
    }

    @Override
    public Evidencia porId(UserId actorId, EvidenciaId evidenciaId) {
        Evidencia evidencia = requireEvidencia(evidenciaId);
        requireDuenoOAdmin(actorId, evidencia);
        return evidencia;
    }

    @Override
    @Transactional
    public Evidencia revisar(RevisarManualmenteCommand command) {
        requireAdmin(command.actorId());
        Evidencia evidencia = requireEvidencia(command.evidenciaId());
        evidencia.revisarManualmente(command.aprobar(), command.notas());
        return saveEvidenciaPort.save(evidencia);
    }

    @Override
    @Transactional
    public Evidencia anular(AnularVeredictoCommand command) {
        requireAdmin(command.actorId());
        Evidencia evidencia = requireEvidencia(command.evidenciaId());
        evidencia.anularVeredicto(command.notas());
        return saveEvidenciaPort.save(evidencia);
    }

    /**
     * SIN IA en este alcance: {@link ValidacionIAPort} siempre responde
     * {@code NO_DISPONIBLE} ({@code NoOpValidacionIAAdapter}), así que cada corrida
     * incrementa {@code intentosIa} hasta el fallback a {@code REVISION_MANUAL} — ver
     * javadoc de {@link ProcesarColaValidacionUseCase}.
     */
    @Override
    @Transactional
    public int procesarLote() {
        List<Evidencia> pendientes = loadEvidenciaPort.pendientesLote(clock.now(), TAMANO_LOTE);
        for (Evidencia evidencia : pendientes) {
            procesarUna(evidencia);
        }
        return pendientes.size();
    }

    private void procesarUna(Evidencia evidencia) {
        ResultadoValidacionIA resultado = validacionIAPort.validar(evidencia);
        switch (resultado) {
            case APROBADA -> evidencia.aprobarPorIa();
            case RECHAZADA -> evidencia.rechazarPorIa("Rechazada por validacion IA");
            case NO_DISPONIBLE -> evidencia.registrarIntentoFallido();
        }
        saveEvidenciaPort.save(evidencia);
    }

    private Evidencia requireEvidencia(EvidenciaId id) {
        return loadEvidenciaPort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Evidencia no encontrada: " + id));
    }

    private void requireDuenoOAdmin(UserId actorId, Evidencia evidencia) {
        if (actorId.equals(evidencia.participanteId())) {
            return;
        }
        requireAdmin(actorId);
    }

    private void requireActivo(UserId actorId) {
        UserSummary actor = requireActor(actorId);
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }

    private void requireAdmin(UserId actorId) {
        UserSummary actor = requireActor(actorId);
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran evidencia ajena");
        }
    }

    private UserSummary requireActor(UserId actorId) {
        return userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
    }
}
