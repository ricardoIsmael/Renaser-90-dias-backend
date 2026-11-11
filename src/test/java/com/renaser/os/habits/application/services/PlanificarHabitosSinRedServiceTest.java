package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase.CambiarEstadoHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.QuitarHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.QuitarHabitoDelPlanUseCase.QuitarHabitoCommand;
import com.renaser.os.habits.application.ports.in.preferencia.CambiarEstadoHabitoEnFechaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.ResultadoEdicionPreferencia;
import com.renaser.os.habits.application.ports.in.preferencia.PlanDeLaSemanaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.sinred.UltimaEdicionDelPlanDeHabitosPort;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.shared.application.EntregaSinRedDePrueba;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Planificar hábitos y alarmas sin red (spec 048, fase 3, FR-019): horario, aviso, alarma, «solo esta semana», repetir la semana y el plan de hábitos.
 * La persona lo hace el lunes 9 a las 21:40 de Lima (02:40 UTC del 10, el día local ANTERIOR en UTC) y la app lo envía el martes 10 a las 08:00 de Lima
 * (regla 02). Los servicios de siempre son dobles: aquí solo se prueba qué se aplica, con qué hora y qué gana en un choque.
 */
class PlanificarHabitosSinRedServiceTest {

    private static final Instant HECHO = Instant.parse("2026-11-10T02:40:00Z");
    private static final Instant LLEGA = Instant.parse("2026-11-10T13:00:00Z");
    private static final UUID ENVIO = UUID.fromString("5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10");
    private static final HechoSinRed SIN_RED = new HechoSinRed(HECHO, ENVIO, OrigenDeLaHora.RELOJ_DEL_SISTEMA);

    private final ConsultarProgresoParticipanteHabitsPort progreso = mock(ConsultarProgresoParticipanteHabitsPort.class);
    private final LoadHabitoPort habitos = mock(LoadHabitoPort.class);
    private final LoadDesbloqueoHabitoPort desbloqueos = mock(LoadDesbloqueoHabitoPort.class);
    private final UltimaEdicionDelPlanDeHabitosPort ultima = mock(UltimaEdicionDelPlanDeHabitosPort.class);
    private final EditarPreferenciaHorarioUseCase preferencias = mock(EditarPreferenciaHorarioUseCase.class);
    private final CambiarEstadoHabitoEnFechaUseCase estadoDelDia = mock(CambiarEstadoHabitoEnFechaUseCase.class);
    private final EditarHorarioSemanalUseCase semanal = mock(EditarHorarioSemanalUseCase.class);
    private final PlanDeLaSemanaUseCase planDeLaSemana = mock(PlanDeLaSemanaUseCase.class);
    private final RepetirSemanaUseCase repeticion = mock(RepetirSemanaUseCase.class);
    private final ElegirHabitoUseCase elegir = mock(ElegirHabitoUseCase.class);
    private final CambiarEstadoHabitoDelPlanUseCase cambiarEstado = mock(CambiarEstadoHabitoDelPlanUseCase.class);
    private final QuitarHabitoDelPlanUseCase quitar = mock(QuitarHabitoDelPlanUseCase.class);

    private EntregaSinRedDePrueba entrega;
    private PreferenciasSinRedService preferenciasSinRed;
    private PlanDeLaSemanaSinRedService semanaSinRed;
    private DesbloqueosSinRedService desbloqueosSinRed;
    private UserId persona;
    private HabitoId habito;

    @BeforeEach
    void preparar() {
        FixedClock reloj = FixedClock.at(LLEGA);
        entrega = EntregaSinRedDePrueba.nueva(reloj);
        preferenciasSinRed = new PreferenciasSinRedService(progreso, habitos, ultima, preferencias, estadoDelDia, semanal, entrega.puerto(), reloj);
        semanaSinRed = new PlanDeLaSemanaSinRedService(progreso, habitos, ultima, planDeLaSemana, repeticion, entrega.puerto(), reloj);
        desbloqueosSinRed = new DesbloqueosSinRedService(progreso, habitos, desbloqueos, ultima, elegir, cambiarEstado, quitar, entrega.puerto(), reloj);
        persona = UserId.of(UUID.randomUUID());
        habito = HabitoId.of(UUID.randomUUID());
        when(progreso.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(10, "America/Lima", RolParticipante.TRAINEE, false, true)));
        when(habitos.byId(habito)).thenReturn(Optional.of(mock(Habito.class)));
        when(ultima.deLaPreferencia(any(), any())).thenReturn(Optional.empty());
        when(ultima.delDiaDeLaSemana(any(), any(), any())).thenReturn(Optional.empty());
        when(ultima.delasFechas(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(ultima.delDesbloqueo(any(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void terminar() {
        entrega.terminar();
    }

    private static HechoSinRed otroEnvio() {
        return new HechoSinRed(HECHO, UUID.randomUUID(), null);
    }

    @Test
    @DisplayName("cambiar la hora de un día de la semana sin red corre con la HORA REAL (la noche del lunes): ahí se mide la ventana y la cuota")
    void fijaElDiaConLaHoraReal() { // spec 048 FR-019
        preferenciasSinRed.fijarDiaDeLaSemana(persona, habito, DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(8, 0), SIN_RED);

        verify(semanal).fijar(persona, habito, DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(8, 0), HECHO);
        assertThat(entrega.registro(ENVIO).orElseThrow().tipo()).isEqualTo("HABITO_PREFERENCIA");
    }

    @Test
    @DisplayName("la hora, el aviso y la alarma de un día se aplican con la hora real y miran la fila de ese día de la semana")
    void avisoYAlarmaDelDia() { // spec 048 FR-019
        preferenciasSinRed.fijarAvisoDelDia(persona, habito, DayOfWeek.TUESDAY, true, List.of(15), otroEnvio());
        preferenciasSinRed.fijarAlarmaDelDia(persona, habito, DayOfWeek.TUESDAY, CombinacionDeAlarma.COMO_HOY, otroEnvio());
        preferenciasSinRed.apagarDiaDeLaSemana(persona, habito, DayOfWeek.TUESDAY, otroEnvio());

        verify(semanal).fijarRecordatorio(persona, habito, DayOfWeek.TUESDAY, true, List.of(15), HECHO);
        verify(semanal).fijarCombinacion(persona, habito, DayOfWeek.TUESDAY, CombinacionDeAlarma.COMO_HOY, HECHO);
        verify(semanal).apagar(persona, habito, DayOfWeek.TUESDAY, HECHO);
        verify(ultima, times(3)).delDiaDeLaSemana(persona, habito, DayOfWeek.TUESDAY);
    }

    @Test
    @DisplayName("la alarma general y la hora de una fecha miran sus propias filas (preferencias_horario y horarios por fecha)")
    void filasDeLaAlarmaGeneralYDeUnaFecha() { // spec 048 FR-019
        LocalDate fecha = LocalDate.of(2026, 11, 12);
        preferenciasSinRed.fijarAlarma(persona, habito, CombinacionDeAlarma.COMO_HOY, otroEnvio());
        preferenciasSinRed.cambiarEstadoDelDia(persona, habito, fecha, false, otroEnvio());

        verify(preferencias).fijarCombinacion(persona, habito, CombinacionDeAlarma.COMO_HOY, HECHO);
        verify(estadoDelDia).cambiarEstadoEnFecha(persona, habito, fecha, false, HECHO);
        verify(ultima).deLaPreferencia(persona, habito);
        verify(ultima).delasFechas(persona, habito, fecha, fecha);
    }

    @Test
    @DisplayName("PATCH de la hora general: la edición corre con la hora real; sin fecha mira la preferencia y con fecha, esa fecha")
    void editarPreferencia() { // spec 048 FR-019
        EditarPreferenciaHorarioCommand comando = new EditarPreferenciaHorarioCommand(persona, habito, LocalTime.of(6, 0), LocalTime.of(8, 0),
                true, 15, null, null);
        ResultadoEdicionPreferencia resultado = new ResultadoEdicionPreferencia(habito, LocalTime.of(6, 0), LocalTime.of(8, 0), true,
                LocalDate.of(2026, 11, 10), 1, 2, 3, "WEEK");
        when(preferencias.editar(comando, HECHO)).thenReturn(resultado);

        assertThat(preferenciasSinRed.editar(comando, SIN_RED)).isSameAs(resultado);

        verify(ultima).deLaPreferencia(persona, habito);
    }

    @Test
    @DisplayName("D13: si la fila de ese día cambió en el servidor DESPUÉS de lo que hiciste, gana el servidor y no se aplica; una anterior no choca")
    void ganaElCambioMasNuevo() { // spec 048 FR-019
        when(ultima.delDiaDeLaSemana(persona, habito, DayOfWeek.MONDAY)).thenReturn(Optional.of(HECHO.plusSeconds(30)));

        assertThatThrownBy(() -> preferenciasSinRed.fijarDiaDeLaSemana(persona, habito, DayOfWeek.MONDAY, LocalTime.of(6, 0), null, SIN_RED))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
        verify(semanal, never()).fijar(any(), any(), any(), any(), any(), any());
        assertThat(entrega.noAplicado(ENVIO)).contains(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);

        when(ultima.delDiaDeLaSemana(persona, habito, DayOfWeek.MONDAY)).thenReturn(Optional.of(HECHO.minusSeconds(3600)));
        preferenciasSinRed.fijarDiaDeLaSemana(persona, habito, DayOfWeek.MONDAY, LocalTime.of(6, 0), null, otroEnvio());
        verify(semanal, times(1)).fijar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("un hábito que ya no existe es RECURSO_YA_NO_EXISTE; un DELETE sobre algo que ya no está se aplica sin más")
    void habitoQueYaNoExiste() { // spec 048 FR-019
        HabitoId fantasma = HabitoId.of(UUID.randomUUID());
        when(habitos.byId(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> preferenciasSinRed.fijarDiaDeLaSemana(persona, fantasma, DayOfWeek.MONDAY, LocalTime.of(6, 0), null, SIN_RED))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));

        preferenciasSinRed.quitarDiaDeLaSemana(persona, fantasma, DayOfWeek.MONDAY, otroEnvio());
        verify(semanal, never()).quitar(any(), any(), any());
    }

    @Test
    @DisplayName("el mismo envío dos veces se aplica una sola vez")
    void envioRepetido() { // spec 048 FR-008
        preferenciasSinRed.quitarDiaDeLaSemana(persona, habito, DayOfWeek.FRIDAY, SIN_RED);
        preferenciasSinRed.quitarDiaDeLaSemana(persona, habito, DayOfWeek.FRIDAY, SIN_RED);

        verify(semanal, times(1)).quitar(persona, habito, DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("una cuenta suspendida es el 403 de siempre, antes de anotar nada")
    void cuentaSuspendida() { // spec 048 FR-019
        when(progreso.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(10, "America/Lima", RolParticipante.TRAINEE, true, true)));

        assertThatThrownBy(() -> preferenciasSinRed.fijarDiaDeLaSemana(persona, habito, DayOfWeek.MONDAY, LocalTime.of(6, 0), null, SIN_RED))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(entrega.registro(ENVIO)).isEmpty();
    }

    // ─── solo esta semana, repetir, deshacer ───────────────────────────────

    @Test
    @DisplayName("«solo esta semana» corre con la hora real, mira las fechas propias de la semana y gana el servidor si cambiaron después")
    void soloEstaSemana() { // spec 048 FR-019
        SemanaCalendaria semana = SemanaCalendaria.desde(LocalDate.of(2026, 11, 16));
        semanaSinRed.guardar(persona, habito, semana, List.of(), SIN_RED);
        verify(planDeLaSemana).guardar(persona, habito, semana, List.of(), HECHO);
        verify(ultima).delasFechas(persona, habito, semana.lunes(), semana.domingo());

        when(ultima.delasFechas(persona, habito, semana.lunes(), semana.domingo())).thenReturn(Optional.of(HECHO.plusSeconds(5)));
        assertThatThrownBy(() -> semanaSinRed.guardar(persona, habito, semana, List.of(), otroEnvio()))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
    }

    @Test
    @DisplayName("repetir y deshacer una semana tocan el plan de TODOS los hábitos de esa semana y corren con la hora real")
    void repetirYDeshacer() { // spec 048 FR-019
        SemanaCalendaria modelo = SemanaCalendaria.desde(LocalDate.of(2026, 11, 9));
        SemanaCalendaria destino = SemanaCalendaria.desde(LocalDate.of(2026, 11, 16));
        when(repeticion.repetir(persona, modelo, destino, true, HECHO)).thenReturn(new RepetirSemanaUseCase.ResultadoDeRepeticion(destino, 3, 0, 1, List.of()));
        when(repeticion.deshacerSemana(persona, destino, HECHO)).thenReturn(3);

        assertThat(semanaSinRed.repetir(persona, modelo, destino, true, SIN_RED).diasCopiados()).isEqualTo(3);
        assertThat(semanaSinRed.deshacerSemana(persona, destino, otroEnvio())).isEqualTo(3);

        verify(ultima, times(2)).delasFechas(persona, null, destino.lunes(), destino.domingo());
    }

    // ─── plan de hábitos ───────────────────────────────────────────────────

    private ElegirHabitoCommand elegirHabito() {
        return new ElegirHabitoCommand(persona, habito, null);
    }

    @Test
    @DisplayName("elegir un hábito del plan corre con la hora real; si ese hábito cambió después en el servidor, gana el servidor")
    void elegirDelPlan() { // spec 048 FR-019
        DesbloqueoHabito puesto = mock(DesbloqueoHabito.class);
        when(elegir.elegir(elegirHabito(), HECHO)).thenReturn(puesto);

        assertThat(desbloqueosSinRed.elegir(elegirHabito(), SIN_RED)).isSameAs(puesto);

        when(ultima.delDesbloqueo(persona, habito)).thenReturn(Optional.of(HECHO.plusSeconds(1)));
        assertThatThrownBy(() -> desbloqueosSinRed.elegir(elegirHabito(), otroEnvio())).isInstanceOfSatisfying(
                NoAplicadoSinRedException.class, e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
    }

    @Test
    @DisplayName("pausar un hábito que ya no está en el plan es RECURSO_YA_NO_EXISTE; sacar del plan lo que ya no está se aplica sin más")
    void pausarYQuitar() { // spec 048 FR-019
        when(desbloqueos.deParticipanteYHabito(persona, habito)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> desbloqueosSinRed.cambiarEstado(new CambiarEstadoHabitoCommand(persona, habito, false, null), SIN_RED))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));

        desbloqueosSinRed.quitar(new QuitarHabitoCommand(persona, habito), otroEnvio());
        verify(quitar, never()).quitar(any());
        verify(cambiarEstado, never()).cambiarEstado(any(), eq(HECHO));
    }
}
