package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RadarServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T14:00:00Z"));

    @Mock
    private LoadRegistroRadarPort loadPort;
    @Mock
    private SaveRegistroRadarPort savePort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;

    private RadarService service;

    @BeforeEach
    void setUp() {
        service = new RadarService(loadPort, savePort, progresoPort, CLOCK);
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ProgresoParticipanteHabits trainee() {
        return new ProgresoParticipanteHabits(5, "America/Argentina/Buenos_Aires", RolParticipante.TRAINEE, false);
    }

    private static RegistrarCheckInRadarCommand comando(UserId actorId, UserId participanteId) {
        return new RegistrarCheckInRadarCommand(actorId, participanteId, "haciendo", "pensando", "sintiendo", 6,
                "evitando");
    }

    @Test
    void registrarRechazaActorQueNoEsElParticipante() {
        UserId dueno = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.registrar(comando(otro, dueno)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(savePort, never()).save(any());
        verify(progresoPort, never()).deParticipante(any());
    }

    @Test
    void registrarRechazaParticipanteInexistente() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(comando(dueno, dueno)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void registrarRechazaParticipanteSuspendido() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.registrar(comando(dueno, dueno)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(savePort, never()).save(any());
    }

    @Test
    void registrarRechazaRolQueNoEsTrainee() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(5, "UTC", RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.registrar(comando(dueno, dueno)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(savePort, never()).save(any());
    }

    @Test
    void registrarGuardaElCheckInParaElDuenoTrainee() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));

        RegistroRadar guardado = service.registrar(comando(dueno, dueno));

        assertThat(guardado.participanteId()).isEqualTo(dueno);
        assertThat(guardado.queHago()).isEqualTo("haciendo");
        assertThat(guardado.nivelEnergia()).isEqualTo(6);
        assertThat(guardado.creadoEn()).isEqualTo(CLOCK.now());
        verify(savePort).save(any());
    }

    @Test
    void ultimoRechazaActorQueNoEsElParticipante() {
        UserId dueno = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.ultimo(otro, dueno)).isInstanceOf(NotAuthorizedException.class);
        verify(loadPort, never()).ultimoDeParticipante(any());
    }

    @Test
    void ultimoDevuelveVacioCuandoNuncaHuboCheckIn() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));
        when(loadPort.ultimoDeParticipante(dueno)).thenReturn(Optional.empty());

        assertThat(service.ultimo(dueno, dueno)).isEmpty();
    }

    @Test
    void ultimoDevuelveElRegistroMasReciente() {
        UserId dueno = UserId.of(UUID.randomUUID());
        RegistroRadar ultimo = RegistroRadar.registrar(dueno, "h", "p", "s", 5, "e", CLOCK.now());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));
        when(loadPort.ultimoDeParticipante(dueno)).thenReturn(Optional.of(ultimo));

        assertThat(service.ultimo(dueno, dueno)).contains(ultimo);
    }

    @Test
    void historialRechazaActorQueNoEsElParticipante() {
        UserId dueno = UserId.of(UUID.randomUUID());
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.historial(otro, dueno, null, 20))
                .isInstanceOf(NotAuthorizedException.class);
        verify(loadPort, never()).historialDeParticipante(any(), any(), eq(20));
    }

    @Test
    void historialSinSiguienteCursorCuandoLaPaginaNoEstaLlena() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));
        List<RegistroRadar> pagina = List.of(RegistroRadar.registrar(dueno, "h", "p", "s", 5, "e", CLOCK.now()));
        when(loadPort.historialDeParticipante(dueno, null, 20)).thenReturn(pagina);

        HistorialRadarPage resultado = service.historial(dueno, dueno, null, 20);

        assertThat(resultado.entradas()).isEqualTo(pagina);
        assertThat(resultado.siguienteCursor()).isNull();
    }

    @Test
    void historialConSiguienteCursorCuandoLaPaginaEstaLlena() {
        UserId dueno = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));
        Instant t1 = CLOCK.now().minusSeconds(120);
        Instant t2 = CLOCK.now().minusSeconds(60);
        List<RegistroRadar> pagina = List.of(
                RegistroRadar.registrar(dueno, "h", "p", "s", 5, "e", t2),
                RegistroRadar.registrar(dueno, "h", "p", "s", 5, "e", t1));
        when(loadPort.historialDeParticipante(dueno, null, 2)).thenReturn(pagina);

        HistorialRadarPage resultado = service.historial(dueno, dueno, null, 2);

        assertThat(resultado.siguienteCursor()).isEqualTo(t1);
    }

    @Test
    void historialPropagaElCursorRecibidoAlPuerto() {
        UserId dueno = UserId.of(UUID.randomUUID());
        Instant cursor = CLOCK.now().minusSeconds(600);
        when(progresoPort.deParticipante(dueno)).thenReturn(Optional.of(trainee()));
        when(loadPort.historialDeParticipante(dueno, cursor, 20)).thenReturn(List.of());

        service.historial(dueno, dueno, cursor, 20);

        verify(loadPort).historialDeParticipante(dueno, cursor, 20);
    }
}
