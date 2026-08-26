package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase.EstadoEspiritu;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.EntregarResumenEspirituCommand;
import com.renaser.os.habits.application.ports.out.espiritu.AudioCatalogPort;
import com.renaser.os.habits.application.ports.out.espiritu.AudioCatalogPort.AudioEspiritu;
import com.renaser.os.habits.application.ports.out.espiritu.LoadRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.espiritu.SaveRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspiritu;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EspirituServiceTest {

    /** 09:00 UTC — despues de las 07:00 de desbloqueo. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T09:00:00Z"));

    @Mock
    private LoadRegistroEspirituPort loadPort;
    @Mock
    private SaveRegistroEspirituPort savePort;
    @Mock
    private AudioCatalogPort audioCatalogPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;

    private EspirituService service;

    @BeforeEach
    void setUp() {
        service = new EspirituService(loadPort, savePort, audioCatalogPort, progresoPort, CLOCK);
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UserId trainee() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    void consultarRechazaSuspendido() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void consultarRechazaRolDistintoDeTrainee() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.MENTOR, false)));

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void entregarRechazaSuspendido() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.entregar(new EntregarResumenEspirituCommand(actor, 1, "resumen")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void entregarRechazaRolDistintoDeTrainee() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.ADMIN, false)));

        assertThatThrownBy(() -> service.entregar(new EntregarResumenEspirituCommand(actor, 1, "resumen")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void desbloqueaElPrimerAudioCuandoNoHayTrackYElDiaDeProgramaAlcanza() {
        UserId actor = trainee();
        // diaPrograma 8 -> audioDay 1 (AUDIO_UNLOCK_START_DAY = 7)
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());
        when(audioCatalogPort.porDia(1)).thenReturn(
                Optional.of(new AudioEspiritu(1, "Dia 1", "drive-1", "audio/mpeg", 1000)));
        when(loadPort.todosDe(actor)).thenReturn(List.of());
        when(audioCatalogPort.todos()).thenReturn(List.of());

        service.consultar(actor);

        verify(savePort).save(any(RegistroEspiritu.class));
    }

    @Test
    void noDesbloqueaNadaSiElDiaDeProgramaNoAlcanzaTodavia() {
        UserId actor = trainee();
        // diaPrograma 5 -> audioDay -2, todavia no arranca Espiritu
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());
        when(loadPort.todosDe(actor)).thenReturn(List.of());
        when(audioCatalogPort.todos()).thenReturn(List.of());

        service.consultar(actor);

        verify(savePort, never()).save(any());
    }

    @Test
    void entregaATiempoDevuelveOnTimeVerdadero() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false)));
        RegistroEspiritu registro = RegistroEspiritu.desbloquear(actor, 1, CLOCK.now(),
                CLOCK.now().plusSeconds(3600), CLOCK.now());
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.of(registro));
        when(loadPort.porParticipanteYDia(actor, 1)).thenReturn(Optional.of(registro));

        var resultado = service.entregar(new EntregarResumenEspirituCommand(actor, 1, "mi resumen"));

        assertThat(resultado.aTiempo()).isTrue();
        verify(savePort).save(registro);
    }

    @Test
    void entregarUnDiaNoDesbloqueadoLanza() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(8, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());
        when(loadPort.porParticipanteYDia(actor, 5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entregar(new EntregarResumenEspirituCommand(actor, 5, "resumen")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void vistaMarcaComoLockedUnDiaDelCatalogoSinTrack() {
        UserId actor = trainee();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(3, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.ultimoDe(actor)).thenReturn(Optional.empty());
        when(loadPort.todosDe(actor)).thenReturn(List.of());
        when(audioCatalogPort.todos()).thenReturn(
                List.of(new AudioEspiritu(1, "Dia 1", "drive-1", "audio/mpeg", 1000)));

        EstadoEspiritu estado = service.consultar(actor);

        assertThat(estado.dias()).hasSize(1);
        assertThat(estado.dias().get(0).estado()).isEqualTo("LOCKED");
        assertThat(estado.diaActual()).isNull();
    }
}
