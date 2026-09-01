package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase.PlanDesbloqueo;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesbloqueoHabitoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T10:00:00Z");

    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LoadDesbloqueoHabitoPort loadPort;

    private DesbloqueoHabitoService service;

    @BeforeEach
    void setUp() {
        service = new DesbloqueoHabitoService(progresoPort, loadPort);
    }

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
                List.of(DesbloqueoHabito.rehydrate(actor, habito, 5, AHORA, AHORA, AHORA)));

        PlanDesbloqueo plan = service.consultar(actor);

        assertThat(plan.enabled()).isTrue();
        assertThat(plan.items()).hasSize(1);
        assertThat(plan.items().get(0).diaDesbloqueo()).isEqualTo(5);
    }
}
