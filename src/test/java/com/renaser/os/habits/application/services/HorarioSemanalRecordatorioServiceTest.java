package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase.DiaDeLaSemana;
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
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.preferencia.HorarioSemanal;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Spec 008 (D-282): el aviso propio de un dia de la semana. NO consume el cupo semanal de reacomodos
 * (decision del dueno, 2026-10-20): cambiar cuanto antes suena el aviso no reacomoda ningun horario.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HorarioSemanalRecordatorioServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-20T04:00:00Z"));
    private static final UserId ACTOR = UserId.of(UUID.randomUUID());

    @Mock ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock LoadHabitoPort loadHabitoPort;
    @Mock LoadHorarioHabitoPort loadHorarioPort;
    @Mock LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock SavePreferenciaHorarioPort savePreferenciaPort;
    @Mock SaveCambioHorarioPendientePort saveCambioPendientePort;
    @Mock LoadCambioHorarioPendientePort loadCambioPendientePort;
    @Mock HistorialCambioHorarioPort historialPort;
    @Mock LoadRegistroHabitoPort loadRegistroPort;

    private PreferenciaHorarioService service;
    private Habito habito;

    @BeforeEach
    void setUp() {
        service = new PreferenciaHorarioService(progresoPort, loadHabitoPort, loadHorarioPort, loadPreferenciaPort,
                savePreferenciaPort, saveCambioPendientePort, loadCambioPendientePort, historialPort, loadRegistroPort,
                CLOCK);
        habito = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Gimnasio", TipoHabito.CHECKBOX, "CUERPO",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
        when(loadHabitoPort.byId(habito.id())).thenReturn(Optional.of(habito));
        // Dia 40 del programa, fuera de la semana de acomodo libre: aca el cupo SI cobraria.
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(40, "America/Lima", RolParticipante.TRAINEE, false, false)));
        when(loadCambioPendientePort.porParticipanteYHabito(any(), any())).thenReturn(Optional.empty());
    }

    private void generalConAviso(Integer... antelaciones) {
        var general = PreferenciaHorario.crear(ACTOR, habito.id(), LocalTime.of(7, 0), null, CLOCK.now());
        general.actualizarRecordatorioConAntelaciones(true, List.of(antelaciones), CLOCK.now());
        when(loadPreferenciaPort.porParticipanteYHabito(ACTOR, habito.id())).thenReturn(Optional.of(general));
    }

    private void generalSinAviso() {
        var general = PreferenciaHorario.crear(ACTOR, habito.id(), LocalTime.of(7, 0), null, CLOCK.now());
        general.actualizarRecordatorio(false, null, CLOCK.now());
        when(loadPreferenciaPort.porParticipanteYHabito(ACTOR, habito.id())).thenReturn(Optional.of(general));
    }

    // ---- fijarRecordatorio ---------------------------------------------------------------------

    @Test
    void guardaElAvisoDelDiaSinConsumirCupo() {
        generalConAviso(10);
        // El cupo semanal esta lleno: con la hora, esto rebotaria. Con el aviso, no se mira.
        when(historialPort.distintosHabitosCambiadosDesde(any(), any())).thenReturn(List.of(
                HabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID())));

        service.fijarRecordatorio(ACTOR, habito.id(), DayOfWeek.SATURDAY, true, List.of(30));

        verify(savePreferenciaPort).fijarRecordatorioParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SATURDAY,
                RecordatorioDelDia.con(List.of(30)));
        verifyNoInteractions(historialPort, saveCambioPendientePort);
        verify(savePreferenciaPort, never()).saveParaDiaSemana(any(), any(), any());
    }

    @Test
    void guardaVariasAntelacionesOrdenadas() {
        generalConAviso(10);

        service.fijarRecordatorio(ACTOR, habito.id(), DayOfWeek.SATURDAY, true, List.of(0, 30));

        verify(savePreferenciaPort).fijarRecordatorioParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SATURDAY,
                RecordatorioDelDia.con(List.of(30, 0)));
    }

    @Test
    void unAvisoIgualAlGeneralHeredaEnVezDeGuardarseComoPropio() {
        generalConAviso(30, 10);

        service.fijarRecordatorio(ACTOR, habito.id(), DayOfWeek.SATURDAY, true, List.of(10, 30));

        verify(savePreferenciaPort).fijarRecordatorioParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SATURDAY, null);
    }

    @Test
    void sinAvisoCuandoElGeneralTampocoAvisaTambienHereda() {
        generalSinAviso();

        service.fijarRecordatorio(ACTOR, habito.id(), DayOfWeek.SATURDAY, false, List.of());

        verify(savePreferenciaPort).fijarRecordatorioParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SATURDAY, null);
    }

    @Test
    void sinAvisoCuandoElGeneralAvisaSeGuardaComoSinAvisoPropio() {
        generalConAviso(10);

        service.fijarRecordatorio(ACTOR, habito.id(), DayOfWeek.SATURDAY, false, null);

        verify(savePreferenciaPort).fijarRecordatorioParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SATURDAY,
                RecordatorioDelDia.sinAviso());
    }

    @Test
    void sinPreferenciaGeneralElGeneralEsSinAviso() {
        when(loadPreferenciaPort.porParticipanteYHabito(ACTOR, habito.id())).thenReturn(Optional.empty());

        service.fijarRecordatorio(ACTOR, habito.id(), DayOfWeek.SATURDAY, true, List.of(30));

        verify(savePreferenciaPort).fijarRecordatorioParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SATURDAY,
                RecordatorioDelDia.con(List.of(30)));
    }

    @Test
    void rechazaUnAvisoActivoSinAntelacionesYUnoFueraDeRangoSinGuardarNada() {
        generalConAviso(10);

        assertThatThrownBy(() -> service.fijarRecordatorio(ACTOR, habito.id(), DayOfWeek.SATURDAY, true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.fijarRecordatorio(ACTOR, habito.id(), DayOfWeek.SATURDAY, true, List.of(-5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 0 y");
        assertThatThrownBy(() -> service.fijarRecordatorio(ACTOR, habito.id(), DayOfWeek.SATURDAY, true, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(savePreferenciaPort);
    }

    @Test
    void unHabitoInexistenteEsNoEncontrado() {
        var otro = HabitoId.of(UUID.randomUUID());
        when(loadHabitoPort.byId(otro)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fijarRecordatorio(ACTOR, otro, DayOfWeek.SATURDAY, true, List.of(30)))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(savePreferenciaPort);
    }

    @Test
    void unHabitoPersonalAjenoSeRechaza() {
        var ajeno = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), "Mi reto",
                TipoHabito.CHECKBOX, "CUERPO", PlantillaHabitoPersonal.OTRO, "etiqueta", CLOCK.now());
        when(loadHabitoPort.byId(ajeno.id())).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.fijarRecordatorio(ACTOR, ajeno.id(), DayOfWeek.SATURDAY, true, List.of(30)))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(savePreferenciaPort);
    }

    @Test
    void unaCuentaSuspendidaSeRechaza() {
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(40, "America/Lima", RolParticipante.TRAINEE, true, false)));

        assertThatThrownBy(() -> service.fijarRecordatorio(ACTOR, habito.id(), DayOfWeek.SATURDAY, true, List.of(30)))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(savePreferenciaPort);
    }

    // ---- consultar: los siete dias con el aviso resuelto ---------------------------------------

    private HorarioSemanal fila(DayOfWeek dia, LocalTime hora, boolean activo, RecordatorioDelDia aviso) {
        return new HorarioSemanal(dia, PreferenciaHorario.crear(ACTOR, habito.id(), hora, null, CLOCK.now()),
                activo, aviso);
    }

    private DiaDeLaSemana del(List<DiaDeLaSemana> dias, DayOfWeek dia) {
        return dias.stream().filter(d -> d.diaSemana() == dia).findFirst().orElseThrow();
    }

    @Test
    void consultarDevuelveLosSieteDiasConElAvisoPropioOHeredado() {
        generalConAviso(10);
        when(loadPreferenciaPort.horarioSemanalDe(ACTOR, habito.id())).thenReturn(List.of(
                fila(DayOfWeek.SATURDAY, LocalTime.of(9, 0), true, RecordatorioDelDia.con(List.of(30))),
                fila(DayOfWeek.SUNDAY, null, true, RecordatorioDelDia.sinAviso()),
                fila(DayOfWeek.MONDAY, LocalTime.of(6, 0), true, null)));

        var dias = service.consultar(ACTOR, habito.id());

        assertThat(dias).hasSize(7);
        var sabado = del(dias, DayOfWeek.SATURDAY);
        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.propio()).isTrue();
        assertThat(sabado.recordatorioActivo()).isTrue();
        assertThat(sabado.antelaciones()).containsExactly(30);
        assertThat(sabado.recordatorioPropio()).isTrue();

        var domingo = del(dias, DayOfWeek.SUNDAY);
        assertThat(domingo.horaDisparo()).as("fila de solo aviso: la hora es la general").isEqualTo(LocalTime.of(7, 0));
        assertThat(domingo.propio()).isFalse();
        assertThat(domingo.recordatorioActivo()).isFalse();
        assertThat(domingo.antelaciones()).isEmpty();
        assertThat(domingo.recordatorioPropio()).isTrue();

        var lunes = del(dias, DayOfWeek.MONDAY);
        assertThat(lunes.propio()).isTrue();
        assertThat(lunes.recordatorioActivo()).as("hora propia, aviso heredado").isTrue();
        assertThat(lunes.antelaciones()).containsExactly(10);
        assertThat(lunes.recordatorioPropio()).isFalse();

        var martes = del(dias, DayOfWeek.TUESDAY);
        assertThat(martes.propio()).isFalse();
        assertThat(martes.antelaciones()).containsExactly(10);
        assertThat(martes.recordatorioPropio()).isFalse();
    }

    @Test
    void consultarSinPreferenciaGeneralHeredaSinAviso() {
        when(loadPreferenciaPort.porParticipanteYHabito(ACTOR, habito.id())).thenReturn(Optional.empty());
        when(loadPreferenciaPort.horarioSemanalDe(ACTOR, habito.id())).thenReturn(List.of());

        var dias = service.consultar(ACTOR, habito.id());

        assertThat(dias).hasSize(7).allSatisfy(d -> {
            assertThat(d.recordatorioActivo()).isFalse();
            assertThat(d.recordatorioPropio()).isFalse();
            assertThat(d.antelaciones()).isEmpty();
        });
    }
}
