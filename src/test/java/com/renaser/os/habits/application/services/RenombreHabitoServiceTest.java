package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.renombre.QuitarRenombreHabitoUseCase.QuitarRenombreHabitoCommand;
import com.renaser.os.habits.application.ports.in.renombre.RenombrarHabitoUseCase.RenombrarHabitoCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.renombre.LoadRenombreHabitoPort;
import com.renaser.os.habits.application.ports.out.renombre.SaveRenombreHabitoPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.renombre.RenombreHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenombreHabitoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));

    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadRenombreHabitoPort loadPort;
    @Mock
    private SaveRenombreHabitoPort savePort;

    private RenombreHabitoService service;

    @BeforeEach
    void setUp() {
        service = new RenombreHabitoService(progresoPort, loadHabitoPort, loadPort, savePort, CLOCK);
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Habito habitoRenombrable(String claveSistema) {
        Habito base = Habito.crearDeSistema("Jugo verde", TipoHabito.CHECKBOX, "NUTRICION",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
        return Habito.rehydrate(base.id(), base.ambito(), null, base.titulo(), null, base.tipo(),
                base.categoriaClave(), null, claveSistema, base.exigenciaEvidencia(), false, false, false, null,
                null, null, null, true, CLOCK.now(), CLOCK.now());
    }

    @Test
    void rechazaHabitoNoRenombrable() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoRenombrable("DAILY_CLASS");
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(0, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.renombrar(new RenombrarHabitoCommand(actor, habito.id(), "Jugo de papaya",
                "Gastritis"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaFueraDeLaVentanaDelDiaCero() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoRenombrable("GREEN_JUICE");
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(1, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.renombrar(new RenombrarHabitoCommand(actor, habito.id(), "Jugo de papaya",
                "Gastritis"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void renombraUnaBebidaEnElDiaCero() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoRenombrable("WARM_LEMON_WATER");
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(0, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadPort.porParticipanteYHabito(actor, habito.id())).thenReturn(Optional.empty());

        RenombreHabito renombre = service.renombrar(new RenombrarHabitoCommand(actor, habito.id(), "Te de jengibre",
                "Reflujo"));

        assertThat(renombre.tituloPersonal()).isEqualTo("Te de jengibre");
        verify(savePort).save(any(RenombreHabito.class));
    }

    @Test
    void quitarBorraElRenombreExistente() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoRenombrable("GREEN_JUICE");
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(0, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));

        service.quitar(new QuitarRenombreHabitoCommand(actor, habito.id()));

        verify(savePort).borrar(actor, habito.id());
    }

    @Test
    void suspendidoRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(0, "UTC", RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.renombrar(new RenombrarHabitoCommand(actor, HabitoId.newId(), "titulo",
                "motivo"))).isInstanceOf(NotAuthorizedException.class);
    }
}
