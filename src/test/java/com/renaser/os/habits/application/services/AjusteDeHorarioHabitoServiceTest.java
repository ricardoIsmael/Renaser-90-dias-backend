package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.AjustarHorarioHabitoUseCase.CambioDeHorario;
import com.renaser.os.habits.api.AjustarHorarioHabitoUseCase.CambioDeHorarioAplicado;
import com.renaser.os.habits.api.AjustarHorarioHabitoUseCase.EstadoDelDia;
import com.renaser.os.habits.api.AjustarHorarioHabitoUseCase.HorarioDeDiaDeLaSemana;
import com.renaser.os.habits.application.ports.in.preferencia.CambiarEstadoHabitoEnFechaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.CuotaEdicion;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.HorarioDeHabito;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.ResumenPreferenciasHorario;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.ResultadoEdicionPreferencia;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SaveCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.habits.domain.model.preferencia.HorarioPorFecha;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fachada de {@code habits.api} que usa el acompanante para escribir horarios: tiene que ser UNA
 * llamada al caso de uso de siempre, conservar el recordatorio y no reimplementar nada.
 *
 * <p>Las pruebas con el caso de uso REAL fijan el reloj a las 03:00 UTC (regla 02, E-91): en Lima
 * todavia es el dia anterior. Con la fecha del servidor, "manana" y "hoy" saldrian corridas un dia.
 */
@ExtendWith(MockitoExtension.class)
class AjusteDeHorarioHabitoServiceTest {

    /** 2026-09-10 03:00 UTC = 2026-09-09 22:00 en Lima. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-10T03:00:00Z"));
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 9, 9);
    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final HabitoId MEDITAR = HabitoId.of(UUID.randomUUID());

    @Mock private EditarPreferenciaHorarioUseCase editarPreferencia;
    @Mock private CambiarEstadoHabitoEnFechaUseCase cambiarEstado;
    @Mock private EditarHorarioSemanalUseCase horarioSemanal;
    @Mock private ConsultarPreferenciasHorarioUseCase consultarPreferencias;

    @Mock private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock private LoadHabitoPort loadHabitoPort;
    @Mock private LoadHorarioHabitoPort loadHorarioPort;
    @Mock private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock private SavePreferenciaHorarioPort savePreferenciaPort;
    @Mock private SaveCambioHorarioPendientePort saveCambioPendientePort;
    @Mock private LoadCambioHorarioPendientePort loadCambioPendientePort;
    @Mock private HistorialCambioHorarioPort historialPort;
    @Mock private LoadRegistroHabitoPort loadRegistroPort;

    private AjusteDeHorarioHabitoService conCasosDeUsoSimulados() {
        return new AjusteDeHorarioHabitoService(editarPreferencia, cambiarEstado, horarioSemanal,
                consultarPreferencias);
    }

    /** El PreferenciaHorarioService de verdad, con el reloj de madrugada UTC. */
    private AjusteDeHorarioHabitoService conCasoDeUsoReal() {
        PreferenciaHorarioService real = new PreferenciaHorarioService(progresoPort, loadHabitoPort, loadHorarioPort,
                loadPreferenciaPort, savePreferenciaPort, saveCambioPendientePort, loadCambioPendientePort,
                historialPort, loadRegistroPort, CLOCK);
        return new AjusteDeHorarioHabitoService(real, real, real, consultarPreferencias);
    }

    private void conRecordatorioVigente(LocalDate fecha, boolean activo, Integer minutos) {
        when(consultarPreferencias.consultar(APRENDIZ, fecha)).thenReturn(new ResumenPreferenciasHorario(
                List.of(new HorarioDeHabito(MEDITAR, "Meditar", LocalTime.of(6, 0), LocalTime.of(7, 0), true, activo,
                        minutos, null)),
                new CuotaEdicion(0, 3, 3, "FREE")));
    }

    @Test
    @DisplayName("un cambio de hora reenvia el recordatorio vigente: el acompanante no apaga el aviso de nadie")
    void conservaElRecordatorio() {
        conRecordatorioVigente(null, true, 15);
        when(editarPreferencia.editar(any())).thenReturn(new ResultadoEdicionPreferencia(MEDITAR, LocalTime.of(6, 30),
                null, true, LocalDate.of(2026, 9, 10), 2, 1, 3, "WEEK"));

        CambioDeHorarioAplicado aplicado = conCasosDeUsoSimulados().cambiarHorario(
                new CambioDeHorario(APRENDIZ, MEDITAR.value(), LocalTime.of(6, 30), null, null));

        ArgumentCaptor<EditarPreferenciaHorarioCommand> comando =
                ArgumentCaptor.forClass(EditarPreferenciaHorarioCommand.class);
        verify(editarPreferencia).editar(comando.capture());
        assertThat(comando.getValue().recordatorioActivo()).isTrue();
        assertThat(comando.getValue().minutosRecordatorio()).isEqualTo(15);
        assertThat(comando.getValue().fecha()).isNull();
        assertThat(aplicado).isEqualTo(new CambioDeHorarioAplicado(LocalTime.of(6, 30), null,
                LocalDate.of(2026, 9, 10), 2, 1, 3, false));
    }

    @Test
    @DisplayName("el rechazo del caso de uso (sin cupo) sube tal cual: no se traga ni se reinterpreta")
    void elRechazoSubeTalCual() {
        conRecordatorioVigente(null, false, null);
        when(editarPreferencia.editar(any())).thenThrow(new IllegalStateException("Esta semana ya reacomodaste 3"));

        assertThatThrownBy(() -> conCasosDeUsoSimulados().cambiarHorario(
                new CambioDeHorario(APRENDIZ, MEDITAR.value(), LocalTime.of(6, 30), null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("dia concreto y dia de semana son una llamada directa a su caso de uso")
    void delegaDiaYDiaDeSemana() {
        AjusteDeHorarioHabitoService servicio = conCasosDeUsoSimulados();

        servicio.cambiarEstadoDelDia(APRENDIZ, new EstadoDelDia(MEDITAR.value(), HOY_EN_LIMA, false));
        servicio.fijarDiaDeLaSemana(new HorarioDeDiaDeLaSemana(APRENDIZ, MEDITAR.value(), DayOfWeek.MONDAY,
                LocalTime.of(5, 0), null));
        servicio.apagarDiaDeLaSemana(APRENDIZ, MEDITAR.value(), DayOfWeek.TUESDAY);
        servicio.quitarDiaDeLaSemana(APRENDIZ, MEDITAR.value(), DayOfWeek.WEDNESDAY);

        verify(cambiarEstado).cambiarEstadoEnFecha(APRENDIZ, MEDITAR, HOY_EN_LIMA, false);
        verify(horarioSemanal).fijar(APRENDIZ, MEDITAR, DayOfWeek.MONDAY, LocalTime.of(5, 0), null);
        verify(horarioSemanal).apagar(APRENDIZ, MEDITAR, DayOfWeek.TUESDAY);
        verify(horarioSemanal).quitar(APRENDIZ, MEDITAR, DayOfWeek.WEDNESDAY);
    }

    @Test
    @DisplayName("03:00 UTC: el cambio general rige desde manana EN LIMA (10/09), no desde el 11/09 del servidor")
    void cambioGeneralRigeDesdeMananaEnLima() {
        enLimaDiaTres();
        conRecordatorioVigente(null, true, 10);
        when(loadRegistroPort.porParticipanteHabitoYFecha(APRENDIZ, MEDITAR, HOY_EN_LIMA)).thenReturn(Optional.empty());
        when(loadPreferenciaPort.porParticipanteYHabito(APRENDIZ, MEDITAR)).thenReturn(Optional.empty());

        CambioDeHorarioAplicado aplicado = conCasoDeUsoReal().cambiarHorario(
                new CambioDeHorario(APRENDIZ, MEDITAR.value(), LocalTime.of(6, 30), LocalTime.of(7, 30), null));

        assertThat(aplicado.rigeDesde()).isEqualTo(HOY_EN_LIMA.plusDays(1));
        assertThat(aplicado.semanaDeAcomodoLibre()).isTrue();
        ArgumentCaptor<CambioHorarioPendiente> pendiente = ArgumentCaptor.forClass(CambioHorarioPendiente.class);
        verify(saveCambioPendientePort).save(pendiente.capture());
        assertThat(pendiente.getValue().fechaEfectiva()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(pendiente.getValue().minutosRecordatorio()).isEqualTo(10);
        verify(savePreferenciaPort).save(any(PreferenciaHorario.class));
    }

    @Test
    @DisplayName("03:00 UTC: un cambio solo para el 10/09 es futuro en Lima y se acepta (con la fecha del servidor seria 'hoy')")
    void cambioPuntualParaMananaEnLima() {
        LocalDate manana = LocalDate.of(2026, 9, 10);
        enLimaDiaTres();
        conRecordatorioVigente(manana, false, null);

        CambioDeHorarioAplicado aplicado = conCasoDeUsoReal().cambiarHorario(
                new CambioDeHorario(APRENDIZ, MEDITAR.value(), LocalTime.of(6, 30), null, manana));

        assertThat(aplicado.rigeDesde()).isEqualTo(manana);
        ArgumentCaptor<HorarioPorFecha> horario = ArgumentCaptor.forClass(HorarioPorFecha.class);
        verify(savePreferenciaPort).saveParaFecha(horario.capture());
        assertThat(horario.getValue().fecha()).isEqualTo(manana);
    }

    @Test
    @DisplayName("03:00 UTC: apagar HOY en Lima (09/09) se acepta; con la fecha del servidor seria un dia pasado")
    void apagarHoyEnLima() {
        enLimaDiaTres();

        conCasoDeUsoReal().cambiarEstadoDelDia(APRENDIZ, new EstadoDelDia(MEDITAR.value(), HOY_EN_LIMA, false));

        ArgumentCaptor<HorarioPorFecha> horario = ArgumentCaptor.forClass(HorarioPorFecha.class);
        verify(savePreferenciaPort).saveParaFecha(horario.capture());
        assertThat(horario.getValue().fecha()).isEqualTo(HOY_EN_LIMA);
        assertThat(horario.getValue().activo()).isFalse();
    }

    @Test
    @DisplayName("un obligatorio no se apaga: la regla es la del caso de uso, no una copia")
    void obligatorioNoSeApaga() {
        when(progresoPort.deParticipante(APRENDIZ)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(3, "America/Lima", RolParticipante.TRAINEE, false, true)));
        when(loadHabitoPort.byId(MEDITAR)).thenReturn(Optional.of(habito(false)));

        assertThatThrownBy(() -> conCasoDeUsoReal().cambiarEstadoDelDia(APRENDIZ,
                new EstadoDelDia(MEDITAR.value(), HOY_EN_LIMA.plusDays(1), false)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Dia 3, coherente con HOY_EN_LIMA (arranco el 2026-09-07): semana de acomodo, sin cupo que mirar. */
    private void enLimaDiaTres() {
        when(progresoPort.deParticipante(APRENDIZ)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(3, "America/Lima", RolParticipante.TRAINEE, false, true)));
        when(loadHabitoPort.byId(MEDITAR)).thenReturn(Optional.of(habito(true)));
    }

    private static Habito habito(boolean desactivable) {
        return Habito.rehydrate(MEDITAR, AmbitoHabito.SISTEMA, null, "Meditar", null, TipoHabito.CHECKBOX, "MENTE",
                null, null, ExigenciaEvidencia.OPCIONAL, false, false, desactivable, false, null, null, null, null,
                true, CLOCK.now(), CLOCK.now());
    }
}
