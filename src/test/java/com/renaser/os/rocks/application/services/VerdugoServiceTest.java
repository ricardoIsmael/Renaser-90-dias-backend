package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.verdugo.RegistrarEventoVerdugoUseCase.RegistrarEventoVerdugoCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.verdugo.LoadEventoVerdugoPort;
import com.renaser.os.rocks.application.ports.out.verdugo.SaveEventoVerdugoPort;
import com.renaser.os.rocks.application.ports.out.verdugo.VerificarDestinoVerdugoPort;
import com.renaser.os.rocks.domain.model.verdugo.DestinoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugoId;
import com.renaser.os.rocks.domain.model.verdugo.ResultadoVerdugo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerdugoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T21:00:00Z"));
    /** Id fijo que devuelve el IdGenerator mockeado, mismo espiritu que el FixedClock de arriba. */
    private static final UUID ID_GENERADO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private LoadEventoVerdugoPort loadEventoVerdugoPort;
    @Mock
    private SaveEventoVerdugoPort saveEventoVerdugoPort;
    @Mock
    private ConsultarProgresoParticipanteRocksPort progresoPort;
    @Mock
    private LoadRocaDiariaPort loadRocaDiariaPort;
    @Mock
    private VerificarDestinoVerdugoPort verificarDestinoPort;
    @Mock
    private IdGenerator idGenerator;

    private VerdugoService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new VerdugoService(loadEventoVerdugoPort, saveEventoVerdugoPort, progresoPort, loadRocaDiariaPort,
                verificarDestinoPort, CLOCK, idGenerator);
        actorId = UserId.of(UUID.randomUUID());
        // lenient: varios casos cortan antes de generar id (autorizacion, destino ajeno, barrido).
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
    }

    /** Roca del propio actor, para los casos donde la pertenencia no es lo que se prueba. */
    private com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria rocaDe(UserId dueno) {
        return com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria.planificar(
                com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId.of(UUID.randomUUID()), dueno,
                CLOCK.today(), 1, "Roca de prueba", null, 5, false,
                com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo.CUERPO, null, null, null, CLOCK);
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
        var roca = rocaDe(actorId);
        when(loadRocaDiariaPort.byId(roca.id())).thenReturn(Optional.of(roca));
        when(saveEventoVerdugoPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new RegistrarEventoVerdugoCommand(actorId, DestinoVerdugo.ROCA_DIARIA, roca.id().value(),
                CLOCK.now(), ResultadoVerdugo.POSTERGADO);
        EventoVerdugo evento = service.registrar(command);

        assertThat(evento.resultado()).isEqualTo(ResultadoVerdugo.POSTERGADO);
    }

    @Test
    @DisplayName("E-38: no se puede registrar un evento Verdugo contra la roca de OTRO participante")
    void rocaDeOtroParticipanteRechazada() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(false)));
        var rocaAjena = rocaDe(UserId.of(UUID.randomUUID()));
        when(loadRocaDiariaPort.byId(rocaAjena.id())).thenReturn(Optional.of(rocaAjena));

        var command = new RegistrarEventoVerdugoCommand(actorId, DestinoVerdugo.ROCA_DIARIA,
                rocaAjena.id().value(), CLOCK.now(), ResultadoVerdugo.POSTERGADO);

        assertThatThrownBy(() -> service.registrar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveEventoVerdugoPort, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("E-38: no se puede registrar un evento Verdugo contra el registro de habito de OTRO participante")
    void registroHabitoDeOtroParticipanteRechazado() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(false)));
        UUID registroAjeno = UUID.randomUUID();
        when(verificarDestinoPort.registroHabitoPerteneceA(registroAjeno, actorId)).thenReturn(false);

        var command = new RegistrarEventoVerdugoCommand(actorId, DestinoVerdugo.REGISTRO_HABITO, registroAjeno,
                CLOCK.now(), ResultadoVerdugo.POSTERGADO);

        assertThatThrownBy(() -> service.registrar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveEventoVerdugoPort, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("E-38: destino inexistente -> 404, no 403 (no existe, no es un problema de permiso)")
    void rocaInexistenteDevuelveNotFound() {
        when(progresoPort.deParticipante(actorId)).thenReturn(Optional.of(progreso(false)));
        UUID inexistente = UUID.randomUUID();
        when(loadRocaDiariaPort.byId(com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId.of(inexistente)))
                .thenReturn(Optional.empty());

        var command = new RegistrarEventoVerdugoCommand(actorId, DestinoVerdugo.ROCA_DIARIA, inexistente,
                CLOCK.now(), ResultadoVerdugo.POSTERGADO);

        assertThatThrownBy(() -> service.registrar(command)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("el barrido nocturno resuelve pendientes como IGNORADO y no tumba el resto si uno falla")
    void barridoNocturnoResuelvePendientesComoIgnorado() {
        EventoVerdugo pendiente1 = EventoVerdugo.rehydrate(EventoVerdugoId.of(UUID.randomUUID()), actorId,
                DestinoVerdugo.ROCA_DIARIA, UUID.randomUUID(), CLOCK.now(), null, null, CLOCK.now(), CLOCK.now());
        EventoVerdugo pendiente2 = EventoVerdugo.rehydrate(EventoVerdugoId.of(UUID.randomUUID()), actorId,
                DestinoVerdugo.ROCA_DIARIA, UUID.randomUUID(), CLOCK.now(), null, null, CLOCK.now(), CLOCK.now());
        when(loadEventoVerdugoPort.pendientesDeFecha(CLOCK.today())).thenReturn(List.of(pendiente1, pendiente2));
        when(saveEventoVerdugoPort.save(pendiente1)).thenThrow(new RuntimeException("fallo simulado"));

        service.resolverPendientesDe(CLOCK.today());

        verify(saveEventoVerdugoPort).save(pendiente2);
        assertThat(pendiente2.resultado()).isEqualTo(ResultadoVerdugo.IGNORADO);
    }
}
