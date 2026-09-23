package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HorarioDelDiaFinder.HorarioResuelto;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorariosDelDia;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.CambioProgramado;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.CuotaEdicion;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.HorarioDeHabito;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.ResumenPreferenciasHorario;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorarioDelDiaFinderServiceTest {

    /**
     * 2026-09-10 03:00 UTC = 2026-09-09 22:00 en Lima (miercoles). Hora elegida A PROPOSITO entre
     * 00:00 y 05:00 UTC (regla 02, E-91): cae en el dia local ANTERIOR. Un reloj a las 10:00 UTC
     * esconderia el bug de tomar la fecha del servidor como "hoy".
     */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-10T03:00:00Z"));
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 9, 9);
    /** Coherente con HOY_EN_LIMA: dia 12 => arranco el 2026-08-29. */
    private static final int DIA_DE_HOY = 12;

    @Mock
    private ConsultarPreferenciasHorarioUseCase consultarUseCase;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock
    private LoadDesbloqueoHabitoPort loadDesbloqueoPort;

    private HorarioDelDiaFinderService finder;
    private UserId aprendiz;

    @BeforeEach
    void setUp() {
        finder = new HorarioDelDiaFinderService(consultarUseCase, progresoPort, loadHabitoPort, loadPreferenciaPort,
                loadDesbloqueoPort, CLOCK);
        aprendiz = UserId.of(UUID.randomUUID());
        lenient().when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(DIA_DE_HOY, "America/Lima", RolParticipante.TRAINEE, false, true)));
    }

    @Test
    @DisplayName("sin fecha, 'hoy' es el dia de Lima y no el del servidor (03:00 UTC = dia anterior)")
    void hoyEsElDiaLocalDelParticipante() {
        when(consultarUseCase.consultar(aprendiz, HOY_EN_LIMA)).thenReturn(resumen(List.of(), cuotaLibre()));

        HorariosDelDia dia = finder.deFecha(aprendiz, null);

        assertThat(dia.fecha()).isEqualTo(HOY_EN_LIMA);
        assertThat(dia.diaPrograma()).isEqualTo(DIA_DE_HOY);
        verify(consultarUseCase).consultar(aprendiz, HOY_EN_LIMA);
    }

    @Test
    @DisplayName("una fecha explicita pasa tal cual y su dia de programa se deriva de la distancia a hoy")
    void fechaExplicitaDerivaSuDiaDePrograma() {
        LocalDate sabado = LocalDate.of(2026, 9, 12);
        when(consultarUseCase.consultar(aprendiz, sabado)).thenReturn(resumen(List.of(), cuotaLibre()));

        HorariosDelDia dia = finder.deFecha(aprendiz, sabado);

        assertThat(dia.fecha()).isEqualTo(sabado);
        assertThat(dia.diaPrograma()).isEqualTo(DIA_DE_HOY + 3);
        verify(loadHabitoPort, never()).porIds(any());
    }

    @Test
    @DisplayName("marca apagado (por fecha o por dia de semana), pausado y obligatorio, sobre el dia de Lima")
    void marcaApagadoPausadoYObligatorio() {
        Habito meditar = habito("Meditar", true);
        Habito leer = habito("Leer", true);
        Habito dormir = habito("Dormir", false);
        Habito correr = habito("Correr", true);
        when(consultarUseCase.consultar(aprendiz, HOY_EN_LIMA)).thenReturn(resumen(List.of(
                vista(meditar, null), vista(leer, null), vista(dormir, null), vista(correr, null)), cuotaLibre()));
        when(loadPreferenciaPort.habitosApagadosEn(aprendiz, HOY_EN_LIMA)).thenReturn(List.of(correr.id()));
        when(loadPreferenciaPort.habitosApagadosEnDiaSemana(aprendiz, DayOfWeek.WEDNESDAY))
                .thenReturn(List.of(meditar.id()));
        // Pausado desde el 2026-09-05 (hora de Lima), sin fecha de fin: cubre el 2026-09-09.
        when(loadDesbloqueoPort.deParticipante(aprendiz)).thenReturn(List.of(DesbloqueoHabito.rehydrate(aprendiz,
                leer.id(), 1, CLOCK.now(), CLOCK.now(), CLOCK.now(), Instant.parse("2026-09-05T15:00:00Z"), null)));
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(meditar, leer, dormir, correr));

        List<HorarioResuelto> habitos = finder.deFecha(aprendiz, null).habitos();

        assertThat(habitos).extracting(HorarioResuelto::titulo, HorarioResuelto::apagado, HorarioResuelto::pausado,
                        HorarioResuelto::obligatorio)
                .containsExactly(
                        tuple("Meditar", true, false, false),
                        tuple("Leer", false, true, false),
                        tuple("Dormir", false, false, true),
                        tuple("Correr", true, false, false));
    }

    @Test
    @DisplayName("traduce horario, cambio programado y cuota semanal sin recalcular nada")
    void traduceHorarioYCuota() {
        Habito meditar = habito("Meditar", true);
        LocalDate efectiva = HOY_EN_LIMA.plusDays(3);
        when(consultarUseCase.consultar(aprendiz, HOY_EN_LIMA)).thenReturn(resumen(
                List.of(vista(meditar, new CambioProgramado(LocalTime.of(7, 0), LocalTime.of(9, 0), efectiva))),
                new CuotaEdicion(1, 2, 3, "WEEK")));
        when(loadPreferenciaPort.habitosApagadosEn(aprendiz, HOY_EN_LIMA)).thenReturn(List.of());
        when(loadPreferenciaPort.habitosApagadosEnDiaSemana(aprendiz, DayOfWeek.WEDNESDAY)).thenReturn(List.of());
        when(loadDesbloqueoPort.deParticipante(aprendiz)).thenReturn(List.of());
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(meditar));

        HorariosDelDia dia = finder.deFecha(aprendiz, null);

        HorarioResuelto horario = dia.habitos().get(0);
        assertThat(horario.habitoId()).isEqualTo(meditar.id().value());
        assertThat(horario.horaDisparo()).isEqualTo(LocalTime.of(6, 0));
        assertThat(horario.horaLimite()).isEqualTo(LocalTime.of(8, 0));
        assertThat(horario.personalizado()).isTrue();
        assertThat(horario.cambioProgramado().desde()).isEqualTo(efectiva);
        assertThat(dia.cuota().usados()).isEqualTo(1);
        assertThat(dia.cuota().restantes()).isEqualTo(2);
        assertThat(dia.cuota().limite()).isEqualTo(3);
        assertThat(dia.cuota().semanaDeAcomodoLibre()).isFalse();
    }

    @Test
    @DisplayName("un participante sin programa no llega al caso de uso")
    void sinParticipacionFalla() {
        UserId desconocido = UserId.of(UUID.randomUUID());
        when(progresoPort.deParticipante(desconocido)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.deFecha(desconocido, null)).isInstanceOf(NoSuchElementException.class);
        verify(consultarUseCase, never()).consultar(any(), any());
    }

    private static ResumenPreferenciasHorario resumen(List<HorarioDeHabito> habitos, CuotaEdicion cuota) {
        return new ResumenPreferenciasHorario(habitos, cuota);
    }

    private static CuotaEdicion cuotaLibre() {
        return new CuotaEdicion(0, 3, 3, "FREE");
    }

    private static HorarioDeHabito vista(Habito habito, CambioProgramado cambio) {
        return new HorarioDeHabito(habito.id(), habito.titulo(), LocalTime.of(6, 0), LocalTime.of(8, 0), true,
                false, null, cambio);
    }

    private static Habito habito(String titulo, boolean desactivable) {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null, titulo, null,
                TipoHabito.CHECKBOX, "MENTE", null, null, ExigenciaEvidencia.OPCIONAL, false, false, desactivable,
                false, null, null, null, null, true, CLOCK.now(), CLOCK.now());
    }
}
