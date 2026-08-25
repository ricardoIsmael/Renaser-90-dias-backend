package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.verdugo.RegistrarEventoVerdugoUseCase.RegistrarEventoVerdugoCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.verdugo.LoadEventoVerdugoPort;
import com.renaser.os.rocks.application.ports.out.verdugo.SaveEventoVerdugoPort;
import com.renaser.os.rocks.domain.model.verdugo.DestinoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugoId;
import com.renaser.os.rocks.domain.model.verdugo.ResultadoVerdugo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerdugoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T21:00:00Z"));

    @Mock
    private LoadEventoVerdugoPort loadEventoVerdugoPort;
    @Mock
    private SaveEventoVerdugoPort saveEventoVerdugoPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;

    private VerdugoService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new VerdugoService(loadEventoVerdugoPort, saveEventoVerdugoPort, progresoPort, CLOCK);
        actorId = UserId.of(UUID.randomUUID());
    }

    private static ProgresoParticipanteRocks progreso(RolParticipante rol, boolean suspendido) {
        return new ProgresoParticipanteRocks(20, LocalDate.of(2026, 1, 5), ZoneOffset.UTC, rol, suspendido);
    }

    private static ProgresoParticipanteRocks progreso(boolean suspendido) {
        return progreso(RolParticipante.TRAINEE, suspendido);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: rol sin permiso (no TRAINEE) -> NotAuthorizedException")
    void rolSinPermisoRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(RolParticipante.MENTOR, false)));

        var command = new RegistrarEventoVerdugoCommand(actorId, DestinoVerdugo.ROCA_DIARIA, UUID.randomUUID(),
                CLOCK.now(), ResultadoVerdugo.COMPLETADO);
        assertThatThrownBy(() -> service.registrar(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("CLAUDE.MD §0.3: actor SUSPENDIDO -> NotAuthorizedException")
    void actorSuspendidoRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(true)));

        var command = new RegistrarEventoVerdugoCommand(actorId, DestinoVerdugo.ROCA_DIARIA, UUID.randomUUID(),
                CLOCK.now(), ResultadoVerdugo.COMPLETADO);
        assertThatThrownBy(() -> service.registrar(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("IGNORADO rechazado ya en el comando (self-validating), antes de llegar al servicio")
    void ignoradoRechazadoEnElComando() {
        assertThatThrownBy(() -> new RegistrarEventoVerdugoCommand(actorId, DestinoVerdugo.ROCA_DIARIA,
                UUID.randomUUID(), CLOCK.now(), ResultadoVerdugo.IGNORADO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registraElEventoConElResultadoDelCliente() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(false)));
        when(saveEventoVerdugoPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new RegistrarEventoVerdugoCommand(actorId, DestinoVerdugo.ROCA_DIARIA, UUID.randomUUID(),
                CLOCK.now(), ResultadoVerdugo.POSTERGADO);
        EventoVerdugo evento = service.registrar(command);

        assertThat(evento.resultado()).isEqualTo(ResultadoVerdugo.POSTERGADO);
    }

    @Test
    @DisplayName("el barrido nocturno resuelve pendientes como IGNORADO y no tumba el resto si uno falla")
    void barridoNocturnoResuelvePendientesComoIgnorado() {
        EventoVerdugo pendiente1 = EventoVerdugo.rehydrate(EventoVerdugoId.newId(), actorId,
                DestinoVerdugo.ROCA_DIARIA, UUID.randomUUID(), CLOCK.now(), null, null, CLOCK.now(), CLOCK.now());
        EventoVerdugo pendiente2 = EventoVerdugo.rehydrate(EventoVerdugoId.newId(), actorId,
                DestinoVerdugo.ROCA_DIARIA, UUID.randomUUID(), CLOCK.now(), null, null, CLOCK.now(), CLOCK.now());
        when(loadEventoVerdugoPort.pendientesDeFecha(CLOCK.today())).thenReturn(List.of(pendiente1, pendiente2));
        when(saveEventoVerdugoPort.save(pendiente1)).thenThrow(new RuntimeException("fallo simulado"));

        service.resolverPendientesDe(CLOCK.today());

        verify(saveEventoVerdugoPort).save(pendiente2);
        assertThat(pendiente2.resultado()).isEqualTo(ResultadoVerdugo.IGNORADO);
    }
}
