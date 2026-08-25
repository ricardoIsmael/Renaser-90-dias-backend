package com.renaser.os.habits.application.services;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.EvidenciaRegistrada;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.RegistrarEvidenciaComando;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Servicio separado de {@link RegistroService} a propósito (CLAUDE.MD §5.4.8, límite
 * de tamaño de clase): {@code RegistroService} ya está cerca del techo de líneas y
 * "subir evidencia" es una operación independiente de "completar" — un hábito con
 * {@code ExigenciaEvidencia.OPCIONAL} puede completarse sin pasar por acá. Cierra D-H6
 * de {@code docs/MODULO_HABITS.md}.
 */
@Service
public class EvidenciaRegistroService implements SubirEvidenciaRegistroUseCase {

    private final LoadRegistroHabitoPort loadRegistroPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final RegistrarEvidenciaPort registrarEvidenciaPort;
    private final Clock clock;

    public EvidenciaRegistroService(LoadRegistroHabitoPort loadRegistroPort,
                                     ConsultarProgresoParticipanteHabitsPort progresoPort,
                                     RegistrarEvidenciaPort registrarEvidenciaPort, Clock clock) {
        this.loadRegistroPort = loadRegistroPort;
        this.progresoPort = progresoPort;
        this.registrarEvidenciaPort = registrarEvidenciaPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EvidenciaRegistrada subir(SubirEvidenciaRegistroCommand command) {
        RegistroHabito registro = requireRegistro(command.registroId());
        requireSelf(command.actorId(), registro.participanteId());

        var comando = new RegistrarEvidenciaComando(command.actorId(),
                new DestinoEvidencia.RegistroHabito(registro.id().value()), command.tipo(), command.bucket(),
                command.rutaStorage(), command.contenidoTexto(), command.timestampExif(), command.gpsLat(),
                command.gpsLng(), false, clock.now());
        return registrarEvidenciaPort.registrar(comando);
    }

    private RegistroHabito requireRegistro(RegistroHabitoId id) {
        return loadRegistroPort.byId(id).orElseThrow(() -> new NoSuchElementException("Registro no encontrado: " + id));
    }

    /** Mismo criterio que {@code RegistroService.requireSelf}: pertenencia Y estado de cuenta. */
    private void requireSelf(UserId actorId, UserId participanteId) {
        if (!actorId.equals(participanteId)) {
            throw new NotAuthorizedException("Solo el propio participante puede subir evidencia de sus habitos");
        }
        var progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}
