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
import com.renaser.os.habits.domain.model.preferencia.AjusteDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.HorarioSemanal;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Spec 009 (D-283): como suena la alarma de un habito, general y por dia. NO consume el cupo semanal de
 * reacomodos —igual que el aviso de la 008—: elegir si vibra o si hay voz no reacomoda ningun horario.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CombinacionDeAlarmaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-20T04:00:00Z"));
    private static final UserId ACTOR = UserId.of(UUID.randomUUID());
    private static final CombinacionDeAlarma SOLO_VIBRAR = CombinacionDeAlarma.de(false, true, false, false);
    private static final CombinacionDeAlarma CON_VOZ = CombinacionDeAlarma.de(true, true, true, true);

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

    private PreferenciaHorario generalConAlarma(AjusteDeAlarma alarma) {
        var general = PreferenciaHorario.crear(ACTOR, habito.id(), LocalTime.of(7, 0), null, CLOCK.now());
        general.actualizarAlarma(alarma, CLOCK.now());
        when(loadPreferenciaPort.porParticipanteYHabito(ACTOR, habito.id())).thenReturn(Optional.of(general));
        return general;
    }

    // ---- la combinacion GENERAL ----------------------------------------------------------------

    @Test
    void guardaLaAlarmaGeneralEnLaPreferenciaQueYaExisteSinConsumirCupo() {
        var general = generalConAlarma(null);
        // El cupo semanal esta lleno: con la hora, esto rebotaria. Con la alarma, no se mira.
        when(historialPort.distintosHabitosCambiadosDesde(any(), any())).thenReturn(List.of(
                HabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID())));

        service.fijarCombinacion(ACTOR, habito.id(), SOLO_VIBRAR);

        var guardada = ArgumentCaptor.forClass(PreferenciaHorario.class);
        verify(savePreferenciaPort).save(guardada.capture());
        assertThat(guardada.getValue()).isSameAs(general);
        assertThat(guardada.getValue().alarma()).isEqualTo(AjusteDeAlarma.de(SOLO_VIBRAR));
        assertThat(guardada.getValue().horaDisparo()).as("la hora no se toca").isEqualTo(LocalTime.of(7, 0));
        verifyNoInteractions(historialPort, saveCambioPendientePort);
    }

    /** Sin fila no hay hora que congelar: nace sin hora, asi sigue la del catalogo. */
    @Test
    void sinPreferenciaCreaUnaSinHoraNiHoraLimite() {
        when(loadPreferenciaPort.porParticipanteYHabito(ACTOR, habito.id())).thenReturn(Optional.empty());

        service.fijarCombinacion(ACTOR, habito.id(), CON_VOZ);

        var guardada = ArgumentCaptor.forClass(PreferenciaHorario.class);
        verify(savePreferenciaPort).save(guardada.capture());
        assertThat(guardada.getValue().horaDisparo()).isNull();
        assertThat(guardada.getValue().horaLimite()).isNull();
        assertThat(guardada.getValue().alarma()).isEqualTo(AjusteDeAlarma.de(CON_VOZ));
        assertThat(guardada.getValue().esSoloDeAlarma()).isTrue();
    }

    /** Lo de hoy no se guarda: sin fila y sin nada que decir, no se crea una fila vacia. */
    @Test
    void sinPreferenciaYConLoDeHoyNoCreaNada() {
        when(loadPreferenciaPort.porParticipanteYHabito(ACTOR, habito.id())).thenReturn(Optional.empty());

        service.fijarCombinacion(ACTOR, habito.id(), CombinacionDeAlarma.COMO_HOY);

        verify(savePreferenciaPort, never()).save(any());
    }

    @Test
    void volverALoDeHoyEnLaGeneralQuitaLaAlarmaGuardada() {
        generalConAlarma(AjusteDeAlarma.de(SOLO_VIBRAR));

        service.fijarCombinacion(ACTOR, habito.id(), CombinacionDeAlarma.COMO_HOY);

        var guardada = ArgumentCaptor.forClass(PreferenciaHorario.class);
        verify(savePreferenciaPort).save(guardada.capture());
        assertThat(guardada.getValue().alarma()).isNull();
    }

    @Test
    void laGeneralValidaAlGuardarElHabitoYLaCuenta() {
        var otro = HabitoId.of(UUID.randomUUID());
        when(loadHabitoPort.byId(otro)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.fijarCombinacion(ACTOR, otro, SOLO_VIBRAR))
                .isInstanceOf(NoSuchElementException.class);

        var ajeno = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), "Mi reto",
                TipoHabito.CHECKBOX, "CUERPO", PlantillaHabitoPersonal.OTRO, "etiqueta", CLOCK.now());
        when(loadHabitoPort.byId(ajeno.id())).thenReturn(Optional.of(ajeno));
        assertThatThrownBy(() -> service.fijarCombinacion(ACTOR, ajeno.id(), SOLO_VIBRAR))
                .isInstanceOf(NotAuthorizedException.class);

        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(40, "America/Lima", RolParticipante.TRAINEE, true, false)));
        assertThatThrownBy(() -> service.fijarCombinacion(ACTOR, habito.id(), SOLO_VIBRAR))
                .isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(savePreferenciaPort);
    }

    // ---- la combinacion de UN dia --------------------------------------------------------------

    @Test
    void guardaLaAlarmaDelDiaSinConsumirCupoNiTocarLaHora() {
        generalConAlarma(null);
        when(historialPort.distintosHabitosCambiadosDesde(any(), any())).thenReturn(List.of(
                HabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID())));

        service.fijarCombinacion(ACTOR, habito.id(), DayOfWeek.SATURDAY, SOLO_VIBRAR);

        verify(savePreferenciaPort).fijarAlarmaParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SATURDAY,
                AjusteDeAlarma.de(SOLO_VIBRAR));
        verifyNoInteractions(historialPort, saveCambioPendientePort);
        verify(savePreferenciaPort, never()).saveParaDiaSemana(any(), any(), any());
        verify(savePreferenciaPort, never()).save(any());
    }

    /** Igual a la general (la resuelta, con lo de hoy por defecto) el dia vuelve a heredarla. */
    @Test
    void unaAlarmaIgualALaGeneralHeredaEnVezDeGuardarseComoPropia() {
        generalConAlarma(AjusteDeAlarma.de(SOLO_VIBRAR));

        service.fijarCombinacion(ACTOR, habito.id(), DayOfWeek.SATURDAY, SOLO_VIBRAR);

        verify(savePreferenciaPort).fijarAlarmaParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SATURDAY, null);
    }

    @Test
    void sinPreferenciaGeneralLaGeneralEsLaDeHoy() {
        when(loadPreferenciaPort.porParticipanteYHabito(ACTOR, habito.id())).thenReturn(Optional.empty());

        service.fijarCombinacion(ACTOR, habito.id(), DayOfWeek.SATURDAY, CombinacionDeAlarma.COMO_HOY);
        service.fijarCombinacion(ACTOR, habito.id(), DayOfWeek.SUNDAY, CON_VOZ);

        verify(savePreferenciaPort).fijarAlarmaParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SATURDAY, null);
        verify(savePreferenciaPort).fijarAlarmaParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SUNDAY,
                AjusteDeAlarma.de(CON_VOZ));
    }

    @Test
    void elDiaGuardaLaCombinacionEnteraAunqueSoloDifieraEnUnCampo() {
        generalConAlarma(AjusteDeAlarma.de(CombinacionDeAlarma.de(true, true, true, false)));

        service.fijarCombinacion(ACTOR, habito.id(), DayOfWeek.SATURDAY, CombinacionDeAlarma.de(true, true, true, true));

        verify(savePreferenciaPort).fijarAlarmaParaDiaSemana(ACTOR, habito.id(), DayOfWeek.SATURDAY,
                AjusteDeAlarma.de(CombinacionDeAlarma.de(true, true, true, true)));
    }

    @Test
    void elDiaValidaAlGuardarElHabitoYLaCuenta() {
        var otro = HabitoId.of(UUID.randomUUID());
        when(loadHabitoPort.byId(otro)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.fijarCombinacion(ACTOR, otro, DayOfWeek.SATURDAY, SOLO_VIBRAR))
                .isInstanceOf(NoSuchElementException.class);

        var ajeno = Habito.crearPersonal(HabitoId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()), "Mi reto",
                TipoHabito.CHECKBOX, "CUERPO", PlantillaHabitoPersonal.OTRO, "etiqueta", CLOCK.now());
        when(loadHabitoPort.byId(ajeno.id())).thenReturn(Optional.of(ajeno));
        assertThatThrownBy(() -> service.fijarCombinacion(ACTOR, ajeno.id(), DayOfWeek.SATURDAY, SOLO_VIBRAR))
                .isInstanceOf(NotAuthorizedException.class);

        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(40, "America/Lima", RolParticipante.TRAINEE, true, false)));
        assertThatThrownBy(() -> service.fijarCombinacion(ACTOR, habito.id(), DayOfWeek.SATURDAY, SOLO_VIBRAR))
                .isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(savePreferenciaPort);
    }

    // ---- consultar: los siete dias con la alarma resuelta --------------------------------------

    private HorarioSemanal fila(DayOfWeek dia, LocalTime hora, AjusteDeAlarma alarma) {
        return new HorarioSemanal(dia, PreferenciaHorario.crear(ACTOR, habito.id(), hora, null, CLOCK.now()), true,
                null, alarma);
    }

    private static DiaDeLaSemana del(List<DiaDeLaSemana> dias, DayOfWeek dia) {
        return dias.stream().filter(d -> d.diaSemana() == dia).findFirst().orElseThrow();
    }

    @Test
    void consultarDevuelveLaAlarmaPropiaOLaHeredadaDeLosSieteDias() {
        generalConAlarma(AjusteDeAlarma.de(CON_VOZ));
        when(loadPreferenciaPort.horarioSemanalDe(ACTOR, habito.id())).thenReturn(List.of(
                fila(DayOfWeek.SATURDAY, null, AjusteDeAlarma.de(SOLO_VIBRAR)),
                fila(DayOfWeek.MONDAY, LocalTime.of(6, 0), null)));

        var dias = service.consultar(ACTOR, habito.id());

        assertThat(dias).hasSize(7);
        var sabado = del(dias, DayOfWeek.SATURDAY);
        assertThat(sabado.alarma()).isEqualTo(SOLO_VIBRAR);
        assertThat(sabado.alarmaPropia()).isTrue();
        assertThat(sabado.propio()).as("la fila de solo alarma no trae hora").isFalse();
        var lunes = del(dias, DayOfWeek.MONDAY);
        assertThat(lunes.alarma()).as("hora propia, alarma heredada").isEqualTo(CON_VOZ);
        assertThat(lunes.alarmaPropia()).isFalse();
        assertThat(del(dias, DayOfWeek.TUESDAY).alarma()).isEqualTo(CON_VOZ);
        assertThat(del(dias, DayOfWeek.TUESDAY).alarmaPropia()).isFalse();
    }

    @Test
    void consultarSinPreferenciaGeneralHeredaLoDeHoy() {
        when(loadPreferenciaPort.porParticipanteYHabito(ACTOR, habito.id())).thenReturn(Optional.empty());
        when(loadPreferenciaPort.horarioSemanalDe(ACTOR, habito.id())).thenReturn(List.of());

        var dias = service.consultar(ACTOR, habito.id());

        assertThat(dias).allSatisfy(d -> {
            assertThat(d.alarma()).isEqualTo(CombinacionDeAlarma.COMO_HOY);
            assertThat(d.alarmaPropia()).isFalse();
        });
    }
}
