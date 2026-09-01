package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase.ConsultarHabitosDeAprendizCommand;
import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase.VistaHabitosDeAprendiz;
import com.renaser.os.habits.application.ports.out.habitosaprendiz.LeerHabitosPersonalizadosPort;
import com.renaser.os.habits.application.ports.out.habitosaprendiz.LeerHabitosPersonalizadosPort.FilaHabitoDeAprendiz;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
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

/** Caso de uso con los puertos simulados: sin Spring, sin Postgres. */
@ExtendWith(MockitoExtension.class)
class HabitosDeAprendizAdminServiceTest {

    /** Martes 2026-08-25, 10:00 UTC — dia habil (TipoDia.DISCIPLINA), lunes de esa semana: 24. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));

    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LeerHabitosPersonalizadosPort leerHabitosPort;
    @Mock
    private HistorialCambioHorarioPort historialPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private HabitosDeAprendizAdminService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId adminSuspendido = UserId.of(UUID.randomUUID());
    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final HabitoId habitoId = HabitoId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new HabitosDeAprendizAdminService(new HabitoAdminGuard(userSummaryFinder), progresoPort,
                leerHabitosPort, historialPort, CLOCK);
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(mentor))
                .thenReturn(Optional.of(new UserSummary(mentor, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(adminSuspendido)).thenReturn(Optional.of(new UserSummary(
                adminSuspendido, "Admin suspendido", null, UserRole.ADMIN, UserStatus.SUSPENDED)));
        lenient().when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(30, "America/Lima", RolParticipante.TRAINEE, false)));
        lenient().when(historialPort.distintosHabitosCambiadosDesde(eq(aprendiz), any())).thenReturn(List.of());
        lenient().when(leerHabitosPort.deAprendiz(eq(aprendiz), anyInt(), any(), any())).thenReturn(List.of());
    }

    private static FilaHabitoDeAprendiz fila(HabitoId id, LocalTime disparoCatalogo, LocalTime disparoPreferencia) {
        return new FilaHabitoDeAprendiz(id, "Agua al despertar", null, false, TipoHabito.CHECKBOX, "CUERPO", false,
                disparoCatalogo, LocalTime.of(9, 0), disparoPreferencia, null, null, null, null, null, null, null,
                null, null);
    }

    @Test
    void mentorRecibe403() {
        var command = new ConsultarHabitosDeAprendizCommand(mentor, aprendiz);

        assertThatThrownBy(() -> service.consultar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(leerHabitosPort, never()).deAprendiz(any(), anyInt(), any(), any());
    }

    @Test
    void adminSuspendidoRecibe403AunqueSuTokenSeaValido() {
        var command = new ConsultarHabitosDeAprendizCommand(adminSuspendido, aprendiz);

        assertThatThrownBy(() -> service.consultar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(leerHabitosPort, never()).deAprendiz(any(), anyInt(), any(), any());
    }

    @Test
    void aprendizInexistenteEs404() {
        UserId desconocido = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(desconocido)).thenReturn(Optional.empty());
        var command = new ConsultarHabitosDeAprendizCommand(admin, desconocido);

        assertThatThrownBy(() -> service.consultar(command)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void unAprendizSuspendidoIgualSePuedeAuditar() {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(30, "America/Lima", RolParticipante.TRAINEE, true)));

        var vista = service.consultar(new ConsultarHabitosDeAprendizCommand(admin, aprendiz));

        assertThat(vista.aprendizId()).isEqualTo(aprendiz);
    }

    @Test
    void resuelveElContextoEnLaZonaDelAprendizNoEnLaDelServidor() {
        service.consultar(new ConsultarHabitosDeAprendizCommand(admin, aprendiz));

        // 2026-08-25T10:00Z es el martes 25 a las 05:00 en Lima -> semana que arranca el lunes 24
        verify(leerHabitosPort).deAprendiz(aprendiz, 30, TipoDia.DISCIPLINA, LocalDate.of(2026, 8, 24));
    }

    @Test
    void laPreferenciaDelAprendizPisaAlHorarioDelCatalogo() {
        when(leerHabitosPort.deAprendiz(eq(aprendiz), anyInt(), any(), any()))
                .thenReturn(List.of(fila(habitoId, LocalTime.of(6, 0), LocalTime.of(7, 30))));

        var vista = service.consultar(new ConsultarHabitosDeAprendizCommand(admin, aprendiz));

        assertThat(vista.habitos()).singleElement().satisfies(habito -> {
            assertThat(habito.horaDisparo()).isEqualTo(LocalTime.of(7, 30));
            assertThat(habito.horaLimite()).isEqualTo(LocalTime.of(9, 0));
            assertThat(habito.horarioPersonalizado()).isTrue();
        });
    }

    @Test
    void sinPreferenciaRigeElHorarioDelCatalogo() {
        when(leerHabitosPort.deAprendiz(eq(aprendiz), anyInt(), any(), any()))
                .thenReturn(List.of(fila(habitoId, LocalTime.of(6, 0), null)));

        var vista = service.consultar(new ConsultarHabitosDeAprendizCommand(admin, aprendiz));

        assertThat(vista.habitos()).singleElement().satisfies(habito -> {
            assertThat(habito.horaDisparo()).isEqualTo(LocalTime.of(6, 0));
            assertThat(habito.horarioPersonalizado()).isFalse();
        });
    }

    @Test
    void elDesbloqueoSinElegidoEnEsRellenoAutomatico() {
        var conDesbloqueo = new FilaHabitoDeAprendiz(habitoId, "Ayuno", "Mi ayuno", true, TipoHabito.CHECKBOX,
                "CUERPO", true, null, null, null, null, true, 15, null, null, null, 12, false,
                LocalDate.of(2026, 8, 27));
        when(leerHabitosPort.deAprendiz(eq(aprendiz), anyInt(), any(), any())).thenReturn(List.of(conDesbloqueo));

        var vista = service.consultar(new ConsultarHabitosDeAprendizCommand(admin, aprendiz));

        assertThat(vista.habitos()).singleElement().satisfies(habito -> {
            assertThat(habito.esPersonal()).isTrue();
            assertThat(habito.tituloPersonal()).isEqualTo("Mi ayuno");
            assertThat(habito.desbloqueo().diaDesbloqueo()).isEqualTo(12);
            assertThat(habito.desbloqueo().elegidoPorLaPersona()).isFalse();
            assertThat(habito.diaSemanalElegido()).isEqualTo(LocalDate.of(2026, 8, 27));
            assertThat(habito.cambioPendiente()).isNull();
            assertThat(habito.minutosRecordatorio()).isEqualTo(15);
        });
    }

    @Test
    void laCuotaSemanalSeCuentaConElHistorial() {
        when(historialPort.distintosHabitosCambiadosDesde(eq(aprendiz), any()))
                .thenReturn(List.of(HabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID())));

        VistaHabitosDeAprendiz vista = service.consultar(new ConsultarHabitosDeAprendizCommand(admin, aprendiz));

        assertThat(vista.cuota().usados()).isEqualTo(2);
        assertThat(vista.cuota().restantes()).isEqualTo(1);
        assertThat(vista.cuota().limite()).isEqualTo(PreferenciaHorarioService.WEEKLY_SCHEDULE_EDIT_LIMIT);
        assertThat(vista.cuota().periodo()).isEqualTo("WEEK");
    }

    @Test
    void laSemanaDeCuotaEsLaDePrograma() {
        // dia 30 -> offset (30-1)%7 = 1 dia atras desde el lunes 25 en Lima -> domingo 24
        service.consultar(new ConsultarHabitosDeAprendizCommand(admin, aprendiz));

        verify(historialPort).distintosHabitosCambiadosDesde(aprendiz, LocalDate.of(2026, 8, 24));
    }

    @Test
    void enLaSemanaDeAcomodoElPeriodoEsFree() {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(3, "America/Lima", RolParticipante.TRAINEE, false)));

        var vista = service.consultar(new ConsultarHabitosDeAprendizCommand(admin, aprendiz));

        assertThat(vista.cuota().periodo()).isEqualTo("FREE");
    }
}
