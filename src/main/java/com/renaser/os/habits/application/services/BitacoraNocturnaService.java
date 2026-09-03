package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.diario.ConsultarBitacoraNocturnaUseCase;
import com.renaser.os.habits.application.ports.in.diario.EscribirBitacoraNocturnaUseCase;
import com.renaser.os.habits.application.ports.out.diario.LoadEntradaDiarioPort;
import com.renaser.os.habits.application.ports.out.diario.SaveEntradaDiarioPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.habits.domain.model.diario.EntradaDiarioId;
import com.renaser.os.habits.domain.model.diario.TipoEntradaDiario;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Ver javadoc de {@link EscribirBitacoraNocturnaUseCase}. */
@Service
public class BitacoraNocturnaService implements EscribirBitacoraNocturnaUseCase, ConsultarBitacoraNocturnaUseCase {

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadEntradaDiarioPort loadPort;
    private final SaveEntradaDiarioPort savePort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public BitacoraNocturnaService(ConsultarProgresoParticipanteHabitsPort progresoPort,
                                    LoadEntradaDiarioPort loadPort, SaveEntradaDiarioPort savePort, Clock clock,
                                    IdGenerator idGenerator) {
        this.progresoPort = progresoPort;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public EntradaDiario escribir(EscribirBitacoraNocturnaCommand command) {
        ProgresoParticipanteHabits progreso = requireProgreso(command.actorId());
        LocalDate hoy = hoyDe(progreso);
        Instant ahora = clock.now();

        Optional<EntradaDiario> existente = loadPort.porParticipanteFechaYTipo(command.actorId(), hoy,
                TipoEntradaDiario.BITACORA_NOCTURNA);
        // Solo el camino de alta pide identidad nueva: si ya hay entrada de hoy se reusa la suya.
        EntradaDiario entrada = existente.orElseGet(() -> EntradaDiario.escribir(
                EntradaDiarioId.of(idGenerator.newId()), command.actorId(), hoy,
                TipoEntradaDiario.BITACORA_NOCTURNA, command.contenidoTexto(), ahora));
        if (existente.isPresent()) {
            entrada.actualizarTexto(command.contenidoTexto(), ahora);
        }
        if (command.audioBucket() != null && command.audioRuta() != null) {
            entrada.adjuntarAudio(command.audioBucket(), command.audioRuta(), ahora);
        }
        return savePort.save(entrada);
    }

    @Override
    public EstadoBitacoraHoy consultarHoy(UserId actorId) {
        ProgresoParticipanteHabits progreso = requireProgreso(actorId);
        LocalDate hoy = hoyDe(progreso);
        EntradaDiario entrada = loadPort.porParticipanteFechaYTipo(actorId, hoy, TipoEntradaDiario.BITACORA_NOCTURNA)
                .orElse(null);
        return new EstadoBitacoraHoy(hoy, entrada);
    }

    private LocalDate hoyDe(ProgresoParticipanteHabits progreso) {
        return clock.now().atZone(ZoneId.of(progreso.timezone())).toLocalDate();
    }

    private ProgresoParticipanteHabits requireProgreso(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }
}
