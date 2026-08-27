package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.horarioadmin.ActualizarHorarioHabitoUseCase.ActualizarHorarioHabitoCommand;
import com.renaser.os.habits.application.ports.in.horarioadmin.CrearHorarioHabitoUseCase.CrearHorarioHabitoCommand;
import com.renaser.os.habits.application.ports.in.horarioadmin.EliminarHorarioHabitoUseCase.EliminarHorarioHabitoCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.SaveHorarioHabitoPort;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorarioHabitoAdminServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));

    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadPort;
    @Mock
    private SaveHorarioHabitoPort savePort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private HorarioHabitoAdminService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new HorarioHabitoAdminService(loadHabitoPort, loadPort, savePort,
                new HabitoAdminGuard(userSummaryFinder), CLOCK);
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(mentor))
                .thenReturn(Optional.of(new UserSummary(mentor, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Habito habitoExistente() {
        return Habito.crearDeSistema("Titulo", TipoHabito.CHECKBOX,
                new DetallesHabito(null, "CUERPO", ExigenciaEvidencia.OPCIONAL, false, false), CLOCK.now());
    }

    @Test
    void crearComoMentorEsRechazado() {
        var command = new CrearHorarioHabitoCommand(mentor, HabitoId.newId(), 1, null, TipoDia.DISCIPLINA, null,
                null);
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void crearSobreHabitoInexistenteFalla404() {
        HabitoId habitoId = HabitoId.newId();
        when(loadHabitoPort.byId(habitoId)).thenReturn(Optional.empty());
        var command = new CrearHorarioHabitoCommand(admin, habitoId, 1, null, TipoDia.DISCIPLINA, null, null);

        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void crearConHabitoExistenteFunciona() {
        Habito habito = habitoExistente();
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        var command = new CrearHorarioHabitoCommand(admin, habito.id(), 1, 10, TipoDia.DISCIPLINA,
                LocalTime.of(6, 0), LocalTime.of(9, 0));

        HorarioHabito creado = service.crear(command);

        assertThat(creado.diaInicio()).isEqualTo(1);
        assertThat(creado.diaFin()).isEqualTo(10);
    }

    @Test
    void actualizarConCamposOmitidosMantieneLosValoresActuales() {
        HorarioHabito existente = HorarioHabito.crear(HabitoId.newId(), 1, 10, TipoDia.DISCIPLINA,
                LocalTime.of(6, 0), LocalTime.of(9, 0), CLOCK.now());
        when(loadPort.byId(existente.id())).thenReturn(Optional.of(existente));
        var command = new ActualizarHorarioHabitoCommand(admin, existente.id(), null, null, null, null, null, false,
                false, false);

        HorarioHabito actualizado = service.actualizar(command);

        assertThat(actualizado.diaInicio()).isEqualTo(1);
        assertThat(actualizado.diaFin()).isEqualTo(10);
        assertThat(actualizado.tipoDia()).isEqualTo(TipoDia.DISCIPLINA);
        assertThat(actualizado.horaDisparo()).isEqualTo(LocalTime.of(6, 0));
        assertThat(actualizado.horaLimite()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void actualizarConLimpiarDiaFinLoDejaAbierto() {
        HorarioHabito existente = HorarioHabito.crear(HabitoId.newId(), 1, 10, TipoDia.DISCIPLINA, null, null,
                CLOCK.now());
        when(loadPort.byId(existente.id())).thenReturn(Optional.of(existente));
        var command = new ActualizarHorarioHabitoCommand(admin, existente.id(), null, null, null, null, null, true,
                false, false);

        HorarioHabito actualizado = service.actualizar(command);

        assertThat(actualizado.diaFin()).isNull();
    }

    @Test
    void actualizarConLimpiarHorasLasDejaNulas() {
        HorarioHabito existente = HorarioHabito.crear(HabitoId.newId(), 1, 10, TipoDia.DISCIPLINA,
                LocalTime.of(6, 0), LocalTime.of(9, 0), CLOCK.now());
        when(loadPort.byId(existente.id())).thenReturn(Optional.of(existente));
        var command = new ActualizarHorarioHabitoCommand(admin, existente.id(), null, null, null, null, null, false,
                true, true);

        HorarioHabito actualizado = service.actualizar(command);

        assertThat(actualizado.horaDisparo()).isNull();
        assertThat(actualizado.horaLimite()).isNull();
    }

    @Test
    void eliminarSobreHorarioInexistenteFalla404() {
        HorarioHabitoId id = HorarioHabitoId.newId();
        when(loadPort.byId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(new EliminarHorarioHabitoCommand(admin, id)))
                .isInstanceOf(NoSuchElementException.class);
    }
}
