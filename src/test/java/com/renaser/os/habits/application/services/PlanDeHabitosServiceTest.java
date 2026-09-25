package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.PlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.habits.api.PlanDeHabitosPort.HabitoSemanal;
import com.renaser.os.habits.api.PlanDeHabitosPort.PlanDeHabitos;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase.CambiarEstadoHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.in.eleccion.ElegirDiaSemanalUseCase;
import com.renaser.os.habits.application.ports.in.eleccion.ElegirDiaSemanalUseCase.ElegirDiaSemanalCommand;
import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase.HabitoConDias;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.eleccion.LoadEleccionDiaSemanalPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.eleccion.EleccionDiaSemanal;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fachada de {@code habits.api.PlanDeHabitosPort}: junta el plan con las reglas que ya existen
 * y delega las escrituras en los casos de uso de siempre, sin reimplementar sus guardas.
 */
@ExtendWith(MockitoExtension.class)
class PlanDeHabitosServiceTest {

    /**
     * 2026-09-28 03:00 UTC = LUNES en el servidor, pero DOMINGO 2026-09-27 22:00 en Lima. Hora
     * elegida a proposito entre 00:00 y 05:00 UTC (regla 02, E-91): con la fecha del servidor, la
     * "semana" seria la del 28/09 y ofreceria siete dias que en Lima todavia no empezaron.
     */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-28T03:00:00Z"));
    private static final LocalDate DOMINGO_EN_LIMA = LocalDate.of(2026, 9, 27);
    private static final LocalDate LUNES_DE_ESA_SEMANA = LocalDate.of(2026, 9, 21);
    /** Coherente con DOMINGO_EN_LIMA: dia 10 => arranco el 2026-09-18. */
    private static final int DIA_DE_HOY = 10;

    @Mock
    private ConsultarMisHabitosUseCase misHabitosUseCase;
    @Mock
    private ElegirHabitoUseCase elegirHabitoUseCase;
    @Mock
    private CambiarEstadoHabitoDelPlanUseCase cambiarEstadoUseCase;
    @Mock
    private ElegirDiaSemanalUseCase elegirDiaUseCase;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    @Mock
    private LoadEleccionDiaSemanalPort loadEleccionPort;

    private PlanDeHabitosService servicio;
    private UserId aprendiz;

    @BeforeEach
    void setUp() {
        servicio = new PlanDeHabitosService(misHabitosUseCase, elegirHabitoUseCase, cambiarEstadoUseCase, elegirDiaUseCase, progresoPort,
                loadDesbloqueoPort, loadEleccionPort, CLOCK);
        aprendiz = UserId.of(UUID.randomUUID());
        lenient().when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(progreso(DIA_DE_HOY, false)));
    }

    @Test
    @DisplayName("'hoy' es el domingo de Lima y no el lunes del servidor: solo queda el domingo para elegir")
    void hoyYSemanaEnLaZonaDelParticipante() {
        Habito correr = habito("Correr", true, true);
        when(misHabitosUseCase.consultar(aprendiz)).thenReturn(List.of(conDias(correr)));
        when(loadEleccionPort.deHabitoEnSemana(aprendiz, correr.id(), LUNES_DE_ESA_SEMANA)).thenReturn(List.of(
                EleccionDiaSemanal.rehydrate(aprendiz, correr.id(), LocalDate.of(2026, 9, 24), LUNES_DE_ESA_SEMANA,
                        CLOCK.now())));

        PlanDeHabitos plan = servicio.planDe(aprendiz);

        assertThat(plan.hoy()).isEqualTo(DOMINGO_EN_LIMA);
        assertThat(plan.semanales()).containsExactly(new HabitoSemanal(correr.id().value(), "Correr",
                LocalDate.of(2026, 9, 24), List.of(DOMINGO_EN_LIMA)));
    }

    @Test
    @DisplayName("marca obligatorio y pausado HOY con la regla del dominio; una pausa vencida ya no cuenta")
    void marcaObligatorioYPausado() {
        Habito leer = habito("Leer", true, false);
        Habito dormir = habito("Dormir", false, false);
        Habito meditar = habito("Meditar", true, false);
        when(misHabitosUseCase.consultar(aprendiz)).thenReturn(List.of(conDias(leer), conDias(dormir),
                conDias(meditar)));
        when(loadDesbloqueoPort.deParticipante(aprendiz)).thenReturn(List.of(
                pausado(leer, DOMINGO_EN_LIMA), sinPausa(dormir), pausado(meditar, DOMINGO_EN_LIMA.minusDays(1))));

        List<HabitoDelPlan> habitos = servicio.planDe(aprendiz).habitos();

        assertThat(habitos).extracting(HabitoDelPlan::titulo, HabitoDelPlan::obligatorio, HabitoDelPlan::pausadoHoy)
                .containsExactly(tuple("Leer", false, true), tuple("Dormir", true, false),
                        tuple("Meditar", false, false));
    }

    @Test
    @DisplayName("E-245: trae todos los que ve, obligatorios marcados; sin fila de desbloqueo no esta pausado")
    void todosLosQueVe() {
        Habito clase = habito("Clase diaria", false, false);
        Habito leer = habito("Leer", true, false);
        Habito escritura = habito("Escritura libre nocturna", true, false);
        when(misHabitosUseCase.consultar(aprendiz)).thenReturn(List.of(conDias(clase), conDias(leer),
                conDias(escritura)));
        when(loadDesbloqueoPort.deParticipante(aprendiz)).thenReturn(List.of(pausado(leer, DOMINGO_EN_LIMA)));

        List<HabitoDelPlan> habitos = servicio.planDe(aprendiz).habitos();

        assertThat(habitos).extracting(HabitoDelPlan::titulo, HabitoDelPlan::obligatorio, HabitoDelPlan::pausadoHoy)
                .containsExactly(tuple("Clase diaria", true, false), tuple("Leer", false, true),
                        tuple("Escritura libre nocturna", false, false));
    }

    @Test
    @DisplayName("en el Dia 0 no hay dias elegibles, igual que la guarda de ElegirDiaSemanalUseCase")
    void diaCeroSinDiasElegibles() {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(progreso(0, false)));
        Habito correr = habito("Correr", true, true);
        when(misHabitosUseCase.consultar(aprendiz)).thenReturn(List.of(conDias(correr)));

        assertThat(servicio.planDe(aprendiz).semanales().getFirst().diasElegibles()).isEmpty();
    }

    @Test
    @DisplayName("una cuenta suspendida no lee el plan")
    void suspendida() {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(progreso(DIA_DE_HOY, true)));

        assertThatThrownBy(() -> servicio.planDe(aprendiz)).isInstanceOf(NotAuthorizedException.class);
        verify(misHabitosUseCase, never()).consultar(any());
    }

    @Test
    @DisplayName("pausar asegura la fila y despues pausa, como el interruptor de Plan (D-99); reactivar y elegir dia delegan")
    void escriturasDelegan() {
        UUID habitoId = UUID.randomUUID();
        LocalDate hasta = LocalDate.of(2026, 10, 1);

        servicio.pausar(aprendiz, habitoId, hasta);
        servicio.reactivar(aprendiz, habitoId);
        servicio.elegirDiaSemanal(aprendiz, habitoId, DOMINGO_EN_LIMA);

        InOrder orden = inOrder(elegirHabitoUseCase, cambiarEstadoUseCase);
        orden.verify(elegirHabitoUseCase).elegir(new ElegirHabitoCommand(aprendiz, HabitoId.of(habitoId), null));
        orden.verify(cambiarEstadoUseCase).cambiarEstado(
                new CambiarEstadoHabitoCommand(aprendiz, HabitoId.of(habitoId), false, hasta));
        verify(cambiarEstadoUseCase).cambiarEstado(
                new CambiarEstadoHabitoCommand(aprendiz, HabitoId.of(habitoId), true, null));
        verify(elegirDiaUseCase).elegir(new ElegirDiaSemanalCommand(aprendiz, HabitoId.of(habitoId), DOMINGO_EN_LIMA));
    }

    @Test
    @DisplayName("el rechazo del caso de uso sube tal cual: traducirlo es del llamador")
    void rechazoSube() {
        UUID habitoId = UUID.randomUUID();
        when(cambiarEstadoUseCase.cambiarEstado(any()))
                .thenThrow(new IllegalStateException("Este habito es obligatorio y no se puede pausar"));

        assertThatThrownBy(() -> servicio.pausar(aprendiz, habitoId, null)).isInstanceOf(IllegalStateException.class);
    }

    private static ProgresoParticipanteHabits progreso(int dia, boolean suspendido) {
        return new ProgresoParticipanteHabits(dia, "America/Lima", RolParticipante.TRAINEE, suspendido, true);
    }

    private DesbloqueoHabito pausado(Habito habito, LocalDate hasta) {
        // Pausado el 2026-09-20 a las 15:00 UTC (10:00 en Lima).
        return DesbloqueoHabito.rehydrate(aprendiz, habito.id(), 1, CLOCK.now(), CLOCK.now(), CLOCK.now(),
                Instant.parse("2026-09-20T15:00:00Z"), hasta);
    }

    private DesbloqueoHabito sinPausa(Habito habito) {
        return DesbloqueoHabito.rehydrate(aprendiz, habito.id(), 1, CLOCK.now(), CLOCK.now(), CLOCK.now());
    }

    private static HabitoConDias conDias(Habito habito) {
        return new HabitoConDias(habito, Set.of(DayOfWeek.values()), 1, 0);
    }

    private static Habito habito(String titulo, boolean desactivable, boolean eleccionDiaSemanal) {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null, titulo, null,
                TipoHabito.CHECKBOX, "MENTE", null, null, ExigenciaEvidencia.OPCIONAL, false, false, desactivable,
                eleccionDiaSemanal, null, null, null, null, true, CLOCK.now(), CLOCK.now());
    }
}
