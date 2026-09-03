package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase.PlanDesbloqueo;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.desbloqueo.SaveDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** NOTA: pruebas escritas en esta pasada, no verificadas con {@code ./mvnw} (regla del encargo). */
@ExtendWith(MockitoExtension.class)
class DesbloqueoHabitoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));

    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LoadDesbloqueoHabitoPort loadPort;
    @Mock
    private SaveDesbloqueoHabitoPort savePort;
    @Mock
    private LoadHabitoPort loadHabitoPort;

    private DesbloqueoHabitoService service;

    @BeforeEach
    void setUp() {
        service = new DesbloqueoHabitoService(progresoPort, loadPort, savePort, loadHabitoPort, CLOCK);
    }

    private static Habito habitoDeSistemaActivo() {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Meditar", TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
    }

    // ---- consultar (comportamiento preexistente, sin cambios de contrato) ----

    @Test
    void suspendidoRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.consultar(actor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void sinDesbloqueosDevuelveEnabledFalso() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.deParticipante(actor)).thenReturn(List.of());

        PlanDesbloqueo plan = service.consultar(actor);

        assertThat(plan.enabled()).isFalse();
        assertThat(plan.items()).isEmpty();
    }

    @Test
    void conDesbloqueosDevuelveEnabledVerdaderoYLosItems() {
        UserId actor = UserId.of(UUID.randomUUID());
        HabitoId habito = HabitoId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        when(loadPort.deParticipante(actor)).thenReturn(
                List.of(DesbloqueoHabito.rehydrate(actor, habito, 5, CLOCK.now(), CLOCK.now(), CLOCK.now())));

        PlanDesbloqueo plan = service.consultar(actor);

        assertThat(plan.enabled()).isTrue();
        assertThat(plan.items()).hasSize(1);
        assertThat(plan.items().get(0).diaDesbloqueo()).isEqualTo(5);
    }

    // ---- elegir (nuevo) ----

    @Test
    void elegirDiaCeroRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        HabitoId habitoId = HabitoId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(0, "UTC", RolParticipante.TRAINEE, false)));

        assertThatThrownBy(() -> service.elegir(new ElegirHabitoCommand(actor, habitoId)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void elegirHabitoPersonalRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito personal = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), actor, "Mi reto", TipoHabito.CHECKBOX,
                "CUERPO", PlantillaHabitoPersonal.OTRO, "etiqueta", CLOCK.now());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(personal.id())).thenReturn(Optional.of(personal));

        assertThatThrownBy(() -> service.elegir(new ElegirHabitoCommand(actor, personal.id())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elegirHabitoInactivoRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeSistemaActivo();
        habito.desactivar(CLOCK.now());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.elegir(new ElegirHabitoCommand(actor, habito.id())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eligeUnHabitoDeCatalogoYDevuelveElDesbloqueoAsegurado() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeSistemaActivo();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        DesbloqueoHabito esperado = DesbloqueoHabito.rehydrate(actor, habito.id(), 10, CLOCK.now(), CLOCK.now(),
                CLOCK.now());
        when(loadPort.deParticipanteYHabito(actor, habito.id())).thenReturn(Optional.of(esperado));

        DesbloqueoHabito resultado = service.elegir(new ElegirHabitoCommand(actor, habito.id()));

        assertThat(resultado).isEqualTo(esperado);
        verify(savePort).elegirSiFalta(actor, habito.id(), 10, CLOCK.now(), CLOCK.now());
    }

    @Test
    void elegirElMismoHabitoDosVecesEsIdempotente() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeSistemaActivo();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(10, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        DesbloqueoHabito primero = DesbloqueoHabito.rehydrate(actor, habito.id(), 10, CLOCK.now(), CLOCK.now(),
                CLOCK.now());
        // Ambas llamadas relean el mismo estado canonico persistido (DO NOTHING preserva el primer valor).
        when(loadPort.deParticipanteYHabito(actor, habito.id())).thenReturn(Optional.of(primero));

        DesbloqueoHabito resultado1 = service.elegir(new ElegirHabitoCommand(actor, habito.id()));
        DesbloqueoHabito resultado2 = service.elegir(new ElegirHabitoCommand(actor, habito.id()));

        assertThat(resultado1).isEqualTo(resultado2).isEqualTo(primero);
        verify(savePort, times(2)).elegirSiFalta(actor, habito.id(), 10, CLOCK.now(), CLOCK.now());
    }
}
