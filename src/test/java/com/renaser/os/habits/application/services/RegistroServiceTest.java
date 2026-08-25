package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.api.ResumenAjustePuntos;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadRegistroHabitoPort loadRegistroPort;
    @Mock
    private SaveRegistroHabitoPort saveRegistroPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private AjustarPuntosPort ajustarPuntosPort;
    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    private RegistroService service;

    @BeforeEach
    void setUp() {
        service = new RegistroService(loadRegistroPort, saveRegistroPort, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, progresoPort, ajustarPuntosPort, events, CLOCK);
        lenient().when(saveRegistroPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    private static Habito habitoCheckbox() {
        return Habito.crearDeSistema("Meditar", TipoHabito.CHECKBOX, "MENTE",
                com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia.OPCIONAL, CLOCK.now());
    }

    private RegistroHabito registroPendiente(UserId participanteId, HabitoId habitoId) {
        return RegistroHabito.generar(participanteId, habitoId, LocalDate.of(2026, 8, 24), 5, TipoDia.DISCIPLINA,
                false, CLOCK.now());
    }

    @Test
    @DisplayName("consultar(): un actor distinto del participante recibe NotAuthorizedException (CLAUDE.MD §0.3)")
    void consultarRechazaActorAjeno() {
        UserId actor = participante();
        UserId otro = participante();
        assertThatThrownBy(() -> service.consultar(actor, otro, LocalDate.now()))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void consultarDelegaAlPuertoParaElPropioParticipante() {
        UserId participante = participante();
        when(progresoPort.deParticipante(participante)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(loadRegistroPort.porParticipanteYFecha(participante, LocalDate.of(2026, 8, 24)))
                .thenReturn(List.of());
        List<RegistroHabito> resultado = service.consultar(participante, participante, LocalDate.of(2026, 8, 24));
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("completar(): actor distinto del dueno del registro -> NotAuthorizedException")
    void completarRechazaActorAjeno() {
        UserId dueno = participante();
        UserId otro = participante();
        Habito habito = habitoCheckbox();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));

        assertThatThrownBy(() -> service.completar(
                new CompletarRegistroCommand(otro, registro.id(), null, null)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("completar sin horario configurado: 0 puntos, sin llamada a AjustarPuntosPort (aplyHabitAward viejo: sin ventana, sin award)")
    void completarSinHorarioNoOtorgaPuntos() {
        UserId dueno = participante();
        Habito habito = habitoCheckbox();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of());
        when(loadPreferenciaPort.porParticipanteYHabito(dueno, habito.id())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));

        RegistroHabito resultado = service.completar(
                new CompletarRegistroCommand(dueno, registro.id(), "listo", null));

        assertThat(resultado.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
        assertThat(resultado.puntosOtorgados()).isZero();
        verify(ajustarPuntosPort, never()).ajustar(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("completar a tiempo con horario configurado: otorga 10 puntos via AjustarPuntosPort, motivo HABIT_COMPLETED")
    void completarATiempoOtorgaDiezPuntos() {
        UserId dueno = participante();
        Habito habito = habitoCheckbox();
        RegistroHabito registro = registroPendiente(dueno, habito.id());
        var horario = com.renaser.os.habits.domain.model.horario.HorarioHabito.crear(habito.id(), 1, null,
                TipoDia.TODOS, java.time.LocalTime.of(6, 0), java.time.LocalTime.of(23, 0), CLOCK.now());

        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        when(loadHorarioPort.porHabito(habito.id())).thenReturn(List.of(horario));
        when(loadPreferenciaPort.porParticipanteYHabito(dueno, habito.id())).thenReturn(Optional.empty());
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));
        when(ajustarPuntosPort.ajustar(any(), any(), anyInt(), any()))
                .thenReturn(new ResumenAjustePuntos(dueno, 10, 110));

        RegistroHabito resultado = service.completar(
                new CompletarRegistroCommand(dueno, registro.id(), null, null));

        assertThat(resultado.puntosOtorgados()).isEqualTo(10);
        verify(ajustarPuntosPort).ajustar(eq(dueno), eq(MotivoPuntos.HABIT_COMPLETED), eq(10), any());
    }

    @Test
    void completarUnHabitoBloqueoRechazado() {
        UserId dueno = participante();
        Habito bloqueo = Habito.crearDeSistema("Santuario", TipoHabito.BLOQUEO, "MENTE",
                com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia.OPCIONAL, CLOCK.now());
        RegistroHabito registro = registroPendiente(dueno, bloqueo.id());
        when(loadRegistroPort.byIdParaEscritura(registro.id())).thenReturn(Optional.of(registro));
        when(loadHabitoPort.byId(bloqueo.id())).thenReturn(Optional.of(bloqueo));
        when(progresoPort.deParticipante(dueno)).thenReturn(
                Optional.of(new ProgresoParticipanteHabits(5, "UTC", RolParticipante.TRAINEE, false)));

        assertThatThrownBy(() -> service.completar(
                new CompletarRegistroCommand(dueno, registro.id(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expirarPendientesAnterioresADelegaYCuentaLosExpirados() {
        RegistroHabito r1 = registroPendiente(participante(), HabitoId.newId());
        RegistroHabito r2 = registroPendiente(participante(), HabitoId.newId());
        when(loadRegistroPort.enEstadoConFechaAnteriorA(EstadoRegistro.PENDIENTE, LocalDate.of(2026, 8, 24)))
                .thenReturn(List.of(r1, r2));

        int expirados = service.expirarPendientesAnterioresA(LocalDate.of(2026, 8, 24));

        assertThat(expirados).isEqualTo(2);
        assertThat(r1.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
        assertThat(r2.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
    }
}
