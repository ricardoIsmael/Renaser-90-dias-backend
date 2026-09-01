package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.phasecontracts.api.ContratoFaseFinder;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.FirmarContratoUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ObtenerUrlFirmaContratoUseCase;
import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort;
import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort.ProgresoParticipante;
import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort.RolParticipante;
import com.renaser.os.phasecontracts.application.ports.out.contrato.LoadContratoPort;
import com.renaser.os.phasecontracts.application.ports.out.contrato.SaveContratoPort;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFaseId;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
public class ContratoService implements FirmarContratoUseCase, ConsultarContratosPendientesUseCase,
        ConsultarContratosUseCase, ObtenerUrlFirmaContratoUseCase, ContratoFaseFinder {

    private static final String TIPO_CONTENIDO_FIRMA = "image/svg+xml";
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);
    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(15);

    private static final Set<RolParticipante> ROLES_PUEDEN_FIRMAR = Set.of(RolParticipante.TRAINEE);
    private static final Set<RolParticipante> ROLES_PUEDEN_CONSULTAR =
            Set.of(RolParticipante.TRAINEE, RolParticipante.MENTOR);

    private final LoadContratoPort loadContratoPort;
    private final SaveContratoPort saveContratoPort;
    private final ConsultarProgresoParticipantePort progresoPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ContratoService(LoadContratoPort loadContratoPort, SaveContratoPort saveContratoPort,
                            ConsultarProgresoParticipantePort progresoPort, AlmacenamientoPort almacenamientoPort,
                            Clock clock, IdGenerator idGenerator) {
        this.loadContratoPort = loadContratoPort;
        this.saveContratoPort = saveContratoPort;
        this.progresoPort = progresoPort;
        this.almacenamientoPort = almacenamientoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public ContratoFase firmar(FirmarContratoCommand command) {
        ProgresoParticipante progreso = requireProgreso(command.participanteId(), ROLES_PUEDEN_FIRMAR);
        FasePrograma fase = FasePrograma.paraDiaPrograma(progreso.diaPrograma());

        if (fase != FasePrograma.FASE_1_RENACER) {
            Optional<ContratoFase> existente = loadContratoPort.porParticipanteYFase(command.participanteId(), fase);
            if (existente.isPresent()) {
                return existente.get(); // idempotente: nunca sobreescribe (service.ts:94-99)
            }
        }

        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
        ContratoFase firmado = ContratoFase.firmar(ContratoFaseId.of(idGenerator.newId()),
                command.participanteId(), progreso.diaPrograma(), clock);
        return saveContratoPort.save(firmado);
    }

    @Override
    public ContratoPendiente consultarPendiente(UserId participanteId) {
        ProgresoParticipante progreso = requireProgreso(participanteId, ROLES_PUEDEN_CONSULTAR);
        FasePrograma faseAFirmar = FasePrograma.faseAFirmarEnDia(progreso.diaPrograma());
        if (faseAFirmar == null) {
            return ContratoPendiente.ninguno();
        }
        boolean yaFirmado = loadContratoPort.porParticipanteYFase(participanteId, faseAFirmar).isPresent();
        return yaFirmado ? ContratoPendiente.ninguno() : ContratoPendiente.de(faseAFirmar);
    }

    @Override
    public List<ContratoConUrlLectura> consultarDeParticipante(UserId participanteId) {
        requireProgreso(participanteId, ROLES_PUEDEN_CONSULTAR);
        return loadContratoPort.todosDeParticipante(participanteId).stream()
                .map(this::conUrlLectura)
                .toList();
    }

    @Override
    public UrlFirmaContrato obtenerUrlSubida(ObtenerUrlFirmaContratoCommand command) {
        ProgresoParticipante progreso = requireProgreso(command.participanteId(), ROLES_PUEDEN_FIRMAR);
        FasePrograma fase = FasePrograma.paraDiaPrograma(progreso.diaPrograma());
        if (fase == FasePrograma.FASE_1_RENACER || !fase.firmaDesbloqueadaEnDia(progreso.diaPrograma())) {
            throw new IllegalArgumentException("Todavia no corresponde firmar ningun pacto de fase");
        }
        if (loadContratoPort.porParticipanteYFase(command.participanteId(), fase).isPresent()) {
            throw new IllegalStateException("El pacto de la fase " + fase.numero() + " ya fue firmado");
        }
        String ruta = ContratoFase.rutaFirma(command.participanteId(), fase);
        URI url = almacenamientoPort.firmarSubida(ruta, TIPO_CONTENIDO_FIRMA, VALIDEZ_URL_SUBIDA);
        return new UrlFirmaContrato(url, ContratoFase.BUCKET_DEFAULT, ruta);
    }

    @Override
    public boolean estaFirmado(UserId participanteId, int numeroFase) {
        return loadContratoPort.porParticipanteYFase(participanteId, FasePrograma.porNumero(numeroFase)).isPresent();
    }

    private ContratoConUrlLectura conUrlLectura(ContratoFase contrato) {
        URI url = almacenamientoPort.firmarLectura(contrato.rutaFirma(), VALIDEZ_URL_LECTURA);
        return new ContratoConUrlLectura(contrato, url);
    }

    /** SUSPENDIDO → 403 (paridad requireActiveTrainee). Rol fuera del set permitido → 403. Sin fila → 404. */
    private ProgresoParticipante requireProgreso(UserId participanteId, Set<RolParticipante> rolesPermitidos) {
        ProgresoParticipante progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (!rolesPermitidos.contains(progreso.rol())) {
            throw new NotAuthorizedException("Rol sin permiso para esta operacion: " + progreso.rol());
        }
        return progreso;
    }
}
