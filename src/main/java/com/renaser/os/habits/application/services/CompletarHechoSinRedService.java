package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.CompletarHechoSinRedUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Spec 048: lo marcado sin red. Decide si la hora real está en regla y deja el cálculo de puntos a
 * {@link RegistroService#completarConHoraReal}, el mismo de siempre pero con esa hora.
 *
 * <p>En regla (D14): no está en el futuro (tolerancia {@link #TOLERANCIA_FUTURO}), cae dentro del día del registro en la zona
 * de la persona y llega a lo sumo {@link #PLAZO_DE_LLEGADA} después.
 */
@Service
class CompletarHechoSinRedService implements CompletarHechoSinRedUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompletarHechoSinRedService.class);

    static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(2);
    static final Duration PLAZO_DE_LLEGADA = Duration.ofHours(48);

    private final RegistroService registros;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final EnviosSinRedPort envios;
    private final Clock clock;

    CompletarHechoSinRedService(RegistroService registros, LoadRegistroHabitoPort loadRegistroPort,
                                ConsultarProgresoParticipanteHabitsPort progresoPort, EnviosSinRedPort envios, Clock clock) {
        this.registros = registros;
        this.loadRegistroPort = loadRegistroPort;
        this.progresoPort = progresoPort;
        this.envios = envios;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CompletadoSinRed completar(CompletarRegistroCommand command, HechoSinRed sinRed) {
        Optional<EnviosSinRedPort.EnvioRegistrado> previo = envios.buscar(sinRed.idCliente(), command.actorId());
        if (previo.isPresent()) {
            return new CompletadoSinRed(registroDe(command), motivo(previo.get().motivoSinPuntos()));
        }
        RegistroHabito registro = registroDe(command);
        if (!registro.participanteId().equals(command.actorId())) {
            throw new NotAuthorizedException("Solo el propio participante puede operar sobre sus hábitos");
        }
        if (registro.estado() == EstadoRegistro.COMPLETADO) {
            return recordar(sinRed, registro, MotivoSinPuntos.YA_ESTABA_HECHO);
        }
        ZoneId zona = progresoPort.deParticipante(command.actorId())
                .map(p -> ZoneId.of(p.timezone()))
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + command.actorId()));
        MotivoSinPuntos motivo = revisar(sinRed.hechoEn(), clock.now(), registro, zona);
        RegistroHabito hecho = registros.completarConHoraReal(command, sinRed.hechoEn(), motivo == null);
        log.info("[offline] registro {} hecho {} (hora {}), llegó {}: {}", hecho.id().value(), sinRed.hechoEn(),
                sinRed.origenDeLaHora(), clock.now(), motivo == null ? "en regla" : motivo);
        return recordar(sinRed, hecho, motivo);
    }

    /** {@code null} si está en regla; si no, el motivo por el que queda sin puntos. */
    static MotivoSinPuntos revisar(Instant hechoEn, Instant ahora, RegistroHabito registro, ZoneId zona) {
        if (hechoEn.isAfter(ahora.plus(TOLERANCIA_FUTURO))) {
            return MotivoSinPuntos.HORA_FUTURA;
        }
        if (Duration.between(hechoEn, ahora).compareTo(PLAZO_DE_LLEGADA) > 0) {
            return MotivoSinPuntos.LLEGO_DESPUES_DE_48_H;
        }
        if (hechoEn.atZone(zona).toLocalDate().isBefore(registro.fechaEjecucion()) || registro.suDiaYaTermino(zona, hechoEn)) {
            return MotivoSinPuntos.FUERA_DE_SU_DIA;
        }
        return null;
    }

    private CompletadoSinRed recordar(HechoSinRed sinRed, RegistroHabito registro, MotivoSinPuntos motivo) {
        envios.registrar(sinRed.idCliente(), registro.participanteId(), registro.id().value(), motivo == null ? null : motivo.name());
        return new CompletadoSinRed(registro, motivo);
    }

    private RegistroHabito registroDe(CompletarRegistroCommand command) {
        return loadRegistroPort.byId(command.registroId())
                .orElseThrow(() -> new NoSuchElementException("Registro no encontrado: " + command.registroId().value()));
    }

    private static MotivoSinPuntos motivo(String guardado) {
        return guardado == null ? null : MotivoSinPuntos.valueOf(guardado);
    }
}
