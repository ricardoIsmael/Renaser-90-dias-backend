package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase.DiaDeLaSemana;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.preferencia.CambioDeFecha;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.FechaPropia;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.shared.domain.Formacion;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Spec 028 (D-296): «Solo esta semana». Hoy es el miercoles 4 de noviembre de 2026 en Lima (a las 09:00 UTC del 4 son las 04:00 en Lima).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanDeLaSemanaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-04T15:00:00Z"));
    private static final UserId ACTOR = UserId.of(UUID.randomUUID());
    private static final SemanaCalendaria SEMANA = SemanaCalendaria.desde(LocalDate.of(2026, 11, 2));

    @Mock ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock SavePreferenciaHorarioPort savePreferenciaPort;
    @Mock EditarHorarioSemanalUseCase planDeSiempre;
    @Mock CatalogoAplicable catalogoAplicable;

    private PlanDeLaSemanaService service;
    private Habito habito;

    @BeforeEach
    void setUp() {
        service = new PlanDeLaSemanaService(progresoPort, loadPreferenciaPort, savePreferenciaPort, planDeSiempre,
                catalogoAplicable, CLOCK);
        habito = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Gimnasio", TipoHabito.CHECKBOX, "CUERPO",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(20, "America/Lima", RolParticipante.TRAINEE, false, true)));
        when(catalogoAplicable.aplicablesPara(eq(ACTOR), any(), any())).thenReturn(List.of(habito));
        when(planDeSiempre.consultar(ACTOR, habito.id())).thenReturn(sieteDias());
        when(loadPreferenciaPort.fechasPropiasDeUnHabitoEntre(eq(ACTOR), eq(habito.id()), any(), any())).thenReturn(List.of());
    }

    /** El plan de siempre: todos los dias a las 07:00 con aviso de 10 minutos. */
    private static List<DiaDeLaSemana> sieteDias() {
        return Stream.of(DayOfWeek.values())
                .map(d -> new DiaDeLaSemana(d, LocalTime.of(7, 0), null, false, true, true, List.of(10), false,
                        CombinacionDeAlarma.COMO_HOY, false))
                .toList();
    }

    private CambioDeFecha mover(LocalDate fecha, int hora) {
        return new CambioDeFecha(fecha, false, null, LocalTime.of(hora, 0), null, null, null);
    }

    // ---- consultar -----------------------------------------------------------------------------

    @Test
    void consultarDevuelveLasSieteFechasConLoPropioEncima() {
        var sabado = new FechaPropia(habito.id(), LocalDate.of(2026, 11, 7), true, LocalTime.of(9, 0), null, null, null);
        when(loadPreferenciaPort.fechasPropiasDeUnHabitoEntre(ACTOR, habito.id(), SEMANA.lunes(), SEMANA.domingo()))
                .thenReturn(List.of(sabado));

        var dias = service.consultar(ACTOR, habito.id(), SEMANA);

        assertThat(dias).hasSize(7);
        assertThat(dias.get(5).propioDeLaFecha()).isTrue();
        assertThat(dias.get(5).dia().horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(dias.get(4).dia().horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        // hoy es miercoles 4: el jueves ya se puede cambiar, el miercoles no.
        assertThat(dias.get(2).editable()).isFalse();
        assertThat(dias.get(3).editable()).isTrue();
    }

    @Test
    void unHabitoQueNoLeCorrespondeEsUn404SinEscribirNada() {
        when(catalogoAplicable.aplicablesPara(eq(ACTOR), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.guardar(ACTOR, habito.id(), SEMANA, List.of(mover(LocalDate.of(2026, 11, 7), 9))))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.consultar(ACTOR, habito.id(), SEMANA)).isInstanceOf(NoSuchElementException.class);

        verify(savePreferenciaPort, never()).guardarFechasPropias(any(), anyCollection());
    }

    @Test
    void unaCuentaSuspendidaNoPuedeNada() {
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(20, "America/Lima", RolParticipante.TRAINEE, true, true)));

        assertThatThrownBy(() -> service.guardar(ACTOR, habito.id(), SEMANA, List.of(mover(LocalDate.of(2026, 11, 7), 9))))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(savePreferenciaPort);
    }

    // ---- guardar -------------------------------------------------------------------------------

    @Test
    void guardaSoloLoTocadoDeCadaFechaEnUnaSolaEscritura() {
        service.guardar(ACTOR, habito.id(), SEMANA, List.of(mover(LocalDate.of(2026, 11, 7), 9),
                new CambioDeFecha(LocalDate.of(2026, 11, 8), false, null, null, null, RecordatorioDelDia.con(List.of(30, 0)), null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<FechaPropia>> filas = ArgumentCaptor.forClass(Collection.class);
        verify(savePreferenciaPort).guardarFechasPropias(eq(ACTOR), filas.capture());
        assertThat(filas.getValue()).hasSize(2);
        var sabado = filas.getValue().stream().filter(f -> f.fecha().equals(LocalDate.of(2026, 11, 7))).findFirst().orElseThrow();
        assertThat(sabado.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.recordatorio()).isNull();
        var domingo = filas.getValue().stream().filter(f -> f.fecha().equals(LocalDate.of(2026, 11, 8))).findFirst().orElseThrow();
        assertThat(domingo.horaDisparo()).isNull();
        assertThat(domingo.recordatorio().antelaciones()).containsExactly(30, 0);
    }

    @Test
    void hoyYLosDiasPasadosSeRechazanYNoSeEscribeNada() {
        assertThatThrownBy(() -> service.guardar(ACTOR, habito.id(), SEMANA,
                List.of(mover(LocalDate.of(2026, 11, 7), 9), mover(LocalDate.of(2026, 11, 4), 9))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.guardar(ACTOR, habito.id(), SEMANA, List.of(mover(LocalDate.of(2026, 11, 3), 9))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(savePreferenciaPort, never()).guardarFechasPropias(any(), anyCollection());
    }

    @Test
    void unaFechaDeOtraSemanaORepetidaOSinCambiosSeRechaza() {
        assertThatThrownBy(() -> service.guardar(ACTOR, habito.id(), SEMANA, List.of(mover(LocalDate.of(2026, 11, 9), 9))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.guardar(ACTOR, habito.id(), SEMANA,
                List.of(mover(LocalDate.of(2026, 11, 7), 9), mover(LocalDate.of(2026, 11, 7), 10))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.guardar(ACTOR, habito.id(), SEMANA, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void siUnCambioDelLoteNoValeNoSeGuardaNingunoDeLosDemas() {
        // 23:59 no cabe en el dia (D-122): el sabado es valido, el domingo no; el lote entero se rechaza.
        assertThatThrownBy(() -> service.guardar(ACTOR, habito.id(), SEMANA, List.of(mover(LocalDate.of(2026, 11, 7), 9),
                new CambioDeFecha(LocalDate.of(2026, 11, 8), false, null, LocalTime.of(23, 59), null, null, null))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(savePreferenciaPort, never()).guardarFechasPropias(any(), anyCollection());
        verify(savePreferenciaPort, never()).borrarParaFechas(any(), anyCollection());
    }

    @Test
    void unHabitoObligatorioNoSePuedeApagarUnDia() {
        var obligatorio = Habito.rehydrate(habito.id(), com.renaser.os.habits.domain.model.habito.AmbitoHabito.SISTEMA, null,
                "Despertar", null, TipoHabito.CHECKBOX, "CUERPO", null, "WAKE_UP", ExigenciaEvidencia.OPCIONAL, false, false, false,
                false, null, null, null, null, true, CLOCK.now(), CLOCK.now());
        when(catalogoAplicable.aplicablesPara(eq(ACTOR), any(), any())).thenReturn(List.of(obligatorio));
        when(planDeSiempre.consultar(ACTOR, obligatorio.id())).thenReturn(sieteDias());
        when(loadPreferenciaPort.fechasPropiasDeUnHabitoEntre(eq(ACTOR), eq(obligatorio.id()), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.guardar(ACTOR, obligatorio.id(), SEMANA,
                List.of(new CambioDeFecha(LocalDate.of(2026, 11, 7), false, false, null, null, null, null))))
                .isInstanceOf(IllegalStateException.class);
        verify(savePreferenciaPort, never()).guardarFechasPropias(any(), anyCollection());
    }

    @Test
    void quedarIgualAlPlanDeSiempreBorraLaFilaQueYaEstaba() {
        var existente = new FechaPropia(habito.id(), LocalDate.of(2026, 11, 7), true, LocalTime.of(9, 0), null, null, null);
        when(loadPreferenciaPort.fechasPropiasDeUnHabitoEntre(ACTOR, habito.id(), SEMANA.lunes(), SEMANA.domingo()))
                .thenReturn(List.of(existente));

        service.guardar(ACTOR, habito.id(), SEMANA, List.of(mover(LocalDate.of(2026, 11, 7), 7)));

        verify(savePreferenciaPort).borrarParaFechas(ACTOR, List.of(new SavePreferenciaHorarioPort.FechaABorrar(habito.id(), LocalDate.of(2026, 11, 7))));
    }

    @Test
    void reiniciarUnDiaLoBorra() {
        var existente = new FechaPropia(habito.id(), LocalDate.of(2026, 11, 7), true, LocalTime.of(9, 0), null, null, null);
        when(loadPreferenciaPort.fechasPropiasDeUnHabitoEntre(ACTOR, habito.id(), SEMANA.lunes(), SEMANA.domingo()))
                .thenReturn(List.of(existente));

        service.guardar(ACTOR, habito.id(), SEMANA, List.of(CambioDeFecha.restablecer(LocalDate.of(2026, 11, 7))));

        verify(savePreferenciaPort).borrarParaFechas(ACTOR, List.of(new SavePreferenciaHorarioPort.FechaABorrar(habito.id(), LocalDate.of(2026, 11, 7))));
    }

    // ---- semanas por venir, hasta el final del programa (2026-11-02) ---------------------------

    /** Hoy es el dia 20 de un programa de 90: el dia 90 es el 2027-01-13 (hoy + 70). Una semana vale si su lunes no pasa de ese dia. */
    private static final LocalDate ULTIMO_DIA_DEL_PROGRAMA = LocalDate.of(2027, 1, 13);

    private void conPrograma(int diaPrograma, int duracionDias) {
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(diaPrograma, "America/Lima", RolParticipante.TRAINEE, false, true, null,
                        Formacion.POR_DEFECTO, duracionDias)));
    }

    @Test
    void unaSemanaLejanaDentroDelProgramaSeLeeYSeGuarda() {
        var lejana = SemanaCalendaria.desde(LocalDate.of(2027, 1, 4));
        assertThat(lejana.lunes()).isBefore(ULTIMO_DIA_DEL_PROGRAMA);

        assertThat(service.consultar(ACTOR, habito.id(), lejana)).hasSize(7);
        service.guardar(ACTOR, habito.id(), lejana, List.of(mover(LocalDate.of(2027, 1, 9), 9)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<FechaPropia>> filas = ArgumentCaptor.forClass(Collection.class);
        verify(savePreferenciaPort).guardarFechasPropias(eq(ACTOR), filas.capture());
        assertThat(filas.getValue()).extracting(FechaPropia::fecha).containsExactly(LocalDate.of(2027, 1, 9));
    }

    @Test
    void laUltimaSemanaDelProgramaSePuedeAunqueTermineAMitad() {
        // El lunes 11 de enero es antes del dia 90 (13 de enero): la semana existe aunque el programa termine el miercoles.
        var ultima = SemanaCalendaria.desde(LocalDate.of(2027, 1, 11));
        assertThat(service.consultar(ACTOR, habito.id(), ultima)).hasSize(7);
    }

    @Test
    void unaSemanaQueEmpiezaDespuesDelUltimoDiaDelProgramaSeRechazaSinEscribirNada() {
        var fuera = SemanaCalendaria.desde(LocalDate.of(2027, 1, 18));
        assertThat(fuera.lunes()).isAfter(ULTIMO_DIA_DEL_PROGRAMA);

        assertThatThrownBy(() -> service.consultar(ACTOR, habito.id(), fuera)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.guardar(ACTOR, habito.id(), fuera, List.of(mover(LocalDate.of(2027, 1, 23), 9))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(savePreferenciaPort, never()).guardarFechasPropias(any(), anyCollection());
        verify(savePreferenciaPort, never()).borrarParaFechas(any(), anyCollection());
        // Ni siquiera se busca el habito: se rechaza antes.
        verifyNoInteractions(catalogoAplicable);
    }

    @Test
    void unProgramaDeUnAnioDejaPlanificarMuchoMasLejos() {
        conPrograma(20, 365);
        var enOctubre = SemanaCalendaria.desde(LocalDate.of(2027, 10, 4));
        assertThat(service.consultar(ACTOR, habito.id(), enOctubre)).hasSize(7);

        // El dia 365 es el 2027-11-03 (hoy + 345): el lunes 8 de noviembre ya no existe para esta persona.
        assertThatThrownBy(() -> service.consultar(ACTOR, habito.id(), SemanaCalendaria.desde(LocalDate.of(2027, 11, 8))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laSemanaEnCursoYLasPasadasNuncaSeAcotan() {
        // Una persona que ya paso del dia 90 sigue pudiendo leer la semana en curso y las anteriores.
        conPrograma(95, 90);
        assertThat(service.consultar(ACTOR, habito.id(), SEMANA)).hasSize(7);
        assertThat(service.consultar(ACTOR, habito.id(), SemanaCalendaria.desde(LocalDate.of(2026, 10, 19)))).hasSize(7);
        // Pero no se planifica una semana por venir: ya no tiene programa.
        assertThatThrownBy(() -> service.consultar(ACTOR, habito.id(), SEMANA.siguiente())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinDiaDeProgramaNoHayConQueAcotar() {
        conPrograma(0, 90);
        assertThat(service.consultar(ACTOR, habito.id(), SemanaCalendaria.desde(LocalDate.of(2027, 6, 7)))).hasSize(7);
    }

    // ---- volver al plan de siempre -------------------------------------------------------------

    @Test
    void volverAlPlanDeSiempreBorraSoloDesdeManana() {
        service.volverAlPlanDeSiempre(ACTOR, habito.id(), SEMANA);

        verify(savePreferenciaPort).borrarFechasPropias(ACTOR, List.of(habito.id()), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 8));
    }

    @Test
    void volverAlPlanDeSiempreDeUnaSemanaPasadaNoBorraNada() {
        service.volverAlPlanDeSiempre(ACTOR, habito.id(), SEMANA.siguiente().siguiente().siguiente());
        verify(savePreferenciaPort).borrarFechasPropias(ACTOR, List.of(habito.id()), LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 29));

        var semanaPasada = SemanaCalendaria.desde(LocalDate.of(2026, 10, 19));
        service.volverAlPlanDeSiempre(ACTOR, habito.id(), semanaPasada);
        verify(savePreferenciaPort, never()).borrarFechasPropias(any(), anyCollection(), eq(LocalDate.of(2026, 10, 19)), any());
    }
}
