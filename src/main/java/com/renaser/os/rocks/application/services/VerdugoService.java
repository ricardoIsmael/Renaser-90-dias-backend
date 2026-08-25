package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.verdugo.ConsultarEventosVerdugoUseCase;
import com.renaser.os.rocks.application.ports.in.verdugo.RegistrarEventoVerdugoUseCase;
import com.renaser.os.rocks.application.ports.in.verdugo.ResolverEventosIgnoradosUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.verdugo.LoadEventoVerdugoPort;
import com.renaser.os.rocks.application.ports.out.verdugo.SaveEventoVerdugoPort;
import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class VerdugoService implements RegistrarEventoVerdugoUseCase, ConsultarEventosVerdugoUseCase,
        ResolverEventosIgnoradosUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerdugoService.class);

    private final LoadEventoVerdugoPort loadEventoVerdugoPort;
    private final SaveEventoVerdugoPort saveEventoVerdugoPort;
    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final Clock clock;

    public VerdugoService(LoadEventoVerdugoPort loadEventoVerdugoPort, SaveEventoVerdugoPort saveEventoVerdugoPort,
                           ConsultarProgresoParticipanteRocksPort progresoPort, Clock clock) {
        this.loadEventoVerdugoPort = loadEventoVerdugoPort;
        this.saveEventoVerdugoPort = saveEventoVerdugoPort;
        this.progresoPort = progresoPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EventoVerdugo registrar(RegistrarEventoVerdugoCommand command) {
        requireProgreso(command.actorId());
        EventoVerdugo evento = EventoVerdugo.registrar(command.actorId(), command.destinoTipo(),
                command.destinoId(), command.disparadoEn(), command.resultado(), clock);
        return saveEventoVerdugoPort.save(evento);
    }

    @Override
    public List<EventoVerdugo> misEventos(UserId actorId) {
        requireProgreso(actorId);
        return loadEventoVerdugoPort.deParticipante(actorId);
    }

    @Override
    @Transactional
    public void resolverPendientesDe(LocalDate fecha) {
        for (EventoVerdugo evento : loadEventoVerdugoPort.pendientesDeFecha(fecha)) {
            try {
                evento.resolverComoIgnorado(clock);
                saveEventoVerdugoPort.save(evento);
            } catch (RuntimeException e) {
                log.error("[rocks.VerdugoService] fallo resolviendo evento {} como IGNORADO: {}", evento.id(),
                        e.getMessage(), e);
            }
        }
    }

    /** SUSPENDIDO -> 403. Rol distinto de TRAINEE -> 403 (mismo criterio que el resto de `rocks`). */
    private void requireProgreso(UserId actorId) {
        var progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (progreso.rol() != RolParticipante.TRAINEE) {
            throw new NotAuthorizedException("Solo un aprendiz registra sus propios eventos Verdugo");
        }
    }
}
