package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.eleccion.ElegirDiaSemanalUseCase.ElegirDiaSemanalCommand;
import com.renaser.os.habits.application.ports.out.eleccion.SaveEleccionDiaSemanalPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.eleccion.EleccionDiaSemanal;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EleccionDiaSemanalServiceTest {

    /** Lunes 2026-08-24, 09:00 UTC. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T09:00:00Z"));

    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private SaveEleccionDiaSemanalPort savePort;

    private EleccionDiaSemanalService service;

    @BeforeEach
    void setUp() {
        service = new EleccionDiaSemanalService(progresoPort, loadHabitoPort, savePort, CLOCK);
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Habito habitoDeEleccionSemanal() {
        Habito base = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Dia sin celular", TipoHabito.CHECKBOX,
                "MENTE", ExigenciaEvidencia.OPCIONAL, CLOCK.now());
        return Habito.rehydrate(base.id(), base.ambito(), null, base.titulo(), null, base.tipo(),
                base.categoriaClave(), null, "PHONE_FREE_DAY", base.exigenciaEvidencia(), false, false, true, null,
                null, null, null, true, CLOCK.now(), CLOCK.now());
    }

    @Test
    void diaCeroRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(0, "UTC", RolParticipante.TRAINEE, false)));

        assertThatThrownBy(() -> service.elegir(new ElegirDiaSemanalCommand(actor, HabitoId.of(UUID.randomUUID()),
                LocalDate.of(2026, 8, 25)))).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void habitoSinEleccionSemanalRechazado() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Otro", TipoHabito.CHECKBOX, "MENTE",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.elegir(new ElegirDiaSemanalCommand(actor, habito.id(),
                LocalDate.of(2026, 8, 25)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fechaFueraDeLaSemanaRechazada() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeEleccionSemanal();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.elegir(new ElegirDiaSemanalCommand(actor, habito.id(),
                LocalDate.of(2026, 9, 1)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eligeUnDiaDeLaSemanaVigenteYReemplazaCualquierEleccionPrevia() {
        UserId actor = UserId.of(UUID.randomUUID());
        Habito habito = habitoDeEleccionSemanal();
        when(progresoPort.deParticipante(actor)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));

        EleccionDiaSemanal eleccion = service.elegir(new ElegirDiaSemanalCommand(actor, habito.id(),
                LocalDate.of(2026, 8, 26)));

        assertThat(eleccion.fechaEjecucion()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(eleccion.semanaInicio()).isEqualTo(LocalDate.of(2026, 8, 24)); // lunes de esa semana
        verify(savePort).borrarDeSemana(actor, habito.id(), LocalDate.of(2026, 8, 24));
    }
}
