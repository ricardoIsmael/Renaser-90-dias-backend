package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.HabitoExcluido;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.preferencia.FechaPropia;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
 * Spec 028 (D-296): repetir la semana. Hoy es el miercoles 28 de octubre de 2026 en Lima; la semana en curso es la del 26 y la siguiente la del 2 de
 * noviembre. El reloj va a las 03:00 UTC del 29 a proposito: en Lima todavia es la noche del 28 (el dia local, no el de UTC).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepeticionDeSemanaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-29T03:00:00Z"));
    private static final UserId ACTOR = UserId.of(UUID.randomUUID());
    private static final SemanaCalendaria MODELO = SemanaCalendaria.desde(LocalDate.of(2026, 10, 26));
    private static final SemanaCalendaria DESTINO = MODELO.siguiente();

    @Mock ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock LoadHabitoPort loadHabitoPort;
    @Mock LoadPreferenciaHorarioPort loadPreferenciaPort;
    @Mock SavePreferenciaHorarioPort savePreferenciaPort;
    @Mock CatalogoAplicable catalogoAplicable;

    private RepeticionDeSemanaService service;
    private Habito despertar;
    private Habito gimnasio;

    @BeforeEach
    void setUp() {
        service = new RepeticionDeSemanaService(progresoPort, loadHabitoPort, loadPreferenciaPort, savePreferenciaPort,
                catalogoAplicable, CLOCK);
        despertar = habito("Despertar");
        gimnasio = habito("Gimnasio");
        progreso(20, false);
        when(catalogoAplicable.aplicablesPara(eq(ACTOR), any(), any())).thenReturn(List.of(despertar, gimnasio));
        filas(List.of(), List.of());
    }

    private Habito habito(String titulo) {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), titulo, TipoHabito.CHECKBOX, "CUERPO",
                ExigenciaEvidencia.OPCIONAL, CLOCK.now());
    }

    private void progreso(int dia, boolean suspendido) {
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(dia, "America/Lima", RolParticipante.TRAINEE, suspendido, true)));
    }

    private void filas(List<FechaPropia> modelo, List<FechaPropia> destino) {
        when(loadPreferenciaPort.fechasPropiasEntre(ACTOR, MODELO.lunes(), MODELO.domingo())).thenReturn(modelo);
        when(loadPreferenciaPort.fechasPropiasEntre(ACTOR, DESTINO.lunes(), DESTINO.domingo())).thenReturn(destino);
    }

    private FechaPropia sabado(Habito habito, LocalDate fecha, int hora) {
        return new FechaPropia(habito.id(), fecha, true, LocalTime.of(hora, 0), null, null, null);
    }

    private FechaPropia sabadoModelo(Habito habito, int hora) {
        return sabado(habito, LocalDate.of(2026, 10, 31), hora);
    }

    private FechaPropia sabadoDestino(Habito habito, int hora) {
        return sabado(habito, LocalDate.of(2026, 11, 7), hora);
    }

    // ---- ofrecer -------------------------------------------------------------------------------

    @Test
    void sinCambiosPropiosEnLaModeloNoHayOferta() {
        assertThat(service.ofrecer(ACTOR)).isEmpty();
    }

    @Test
    void conUnCambioEnLaModeloYLaSiguienteSinNadaHayOferta() {
        filas(List.of(sabadoModelo(despertar, 9)), List.of());

        var oferta = service.ofrecer(ACTOR).orElseThrow();

        assertThat(oferta.modelo()).isEqualTo(MODELO);
        assertThat(oferta.destino()).isEqualTo(DESTINO);
        assertThat(oferta.reemplazaLoPropio()).isFalse();
        assertThat(oferta.huella()).hasSize(16);
        assertThat(oferta.habitos()).singleElement().satisfies(h -> {
            assertThat(h.titulo()).isEqualTo("Despertar");
            assertThat(h.dias()).singleElement().satisfies(d -> {
                assertThat(d.fecha()).isEqualTo(LocalDate.of(2026, 11, 7));
                assertThat(d.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
                assertThat(d.vuelveAlPlanDeSiempre()).isFalse();
            });
        });
    }

    @Test
    void siLaSiguienteYaEsIgualNoHayOferta() {
        filas(List.of(sabadoModelo(despertar, 9)), List.of(sabadoDestino(despertar, 9)));

        assertThat(service.ofrecer(ACTOR)).isEmpty();
    }

    @Test
    void siLaSiguienteTieneOtroCambioLaOfertaDiceQueLoReemplaza() {
        filas(List.of(sabadoModelo(despertar, 9)), List.of(sabadoDestino(despertar, 10)));

        var oferta = service.ofrecer(ACTOR).orElseThrow();

        assertThat(oferta.reemplazaLoPropio()).isTrue();
        assertThat(oferta.habitos().getFirst().dias().getFirst().reemplaza()).isTrue();
    }

    @Test
    void soloLoDeLaSiguienteSinNadaEnLaModeloNoEsUnaOferta() {
        filas(List.of(), List.of(sabadoDestino(despertar, 10)));

        assertThat(service.ofrecer(ACTOR)).isEmpty();
    }

    @Test
    void sinElDia1NoSeOfreceNada() {
        progreso(0, false);
        filas(List.of(sabadoModelo(despertar, 9)), List.of());

        assertThat(service.ofrecer(ACTOR)).isEmpty();
    }

    @Test
    void nuncaSeOfreceUnaSemanaQueEmpiezaDespuesDelDia90() {
        // Dia 87 un miercoles: el lunes de la semana siguiente es el dia 92.
        progreso(87, false);
        filas(List.of(sabadoModelo(despertar, 9)), List.of());

        assertThat(service.ofrecer(ACTOR)).isEmpty();
    }

    @Test
    void conUnProgramaDe365DiasLaOfertaSigueDespuesDelDia90() {
        // Diplomado de 1 año (spec 022): el mismo día 87 está lejos del final; el tope no es 90 para todos.
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(new ProgresoParticipanteHabits(87, "America/Lima",
                RolParticipante.TRAINEE, false, true, null, com.renaser.os.shared.domain.Formacion.DIPLOMADO, 365)));
        filas(List.of(sabadoModelo(despertar, 9)), List.of());

        assertThat(service.ofrecer(ACTOR)).isPresent();
    }

    @Test
    void unHabitoQueYaNoLeCorrespondeSeDejaAfueraConSuNombre() {
        var viejo = habito("Hábito viejo");
        filas(List.of(sabadoModelo(despertar, 9), sabadoModelo(viejo, 8)), List.of());
        when(loadHabitoPort.porIds(Set.of(viejo.id()))).thenReturn(List.of(viejo));

        var oferta = service.ofrecer(ACTOR).orElseThrow();

        assertThat(oferta.habitos()).extracting(h -> h.titulo()).containsExactly("Despertar");
        assertThat(oferta.excluidos()).singleElement().satisfies(e -> {
            assertThat(e.titulo()).isEqualTo("Hábito viejo");
            assertThat(e.motivo()).isEqualTo(HabitoExcluido.NO_DISPONIBLE);
        });
    }

    @Test
    void lasFilasDeLaModeloDeUnHabitoAjenoNoCuentanComoCambiosPropios() {
        var viejo = habito("Hábito viejo");
        filas(List.of(sabadoModelo(viejo, 8)), List.of());
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(viejo));

        assertThat(service.ofrecer(ACTOR)).isEmpty();
    }

    @Test
    void unaCuentaSuspendidaNoRecibeOferta() {
        progreso(20, true);

        assertThatThrownBy(() -> service.ofrecer(ACTOR)).isInstanceOf(NotAuthorizedException.class);
    }

    // ---- repetir -------------------------------------------------------------------------------

    @Test
    void repetirEscribeLaFilaEnLaMismaFechaDeLaSiguiente() {
        filas(List.of(sabadoModelo(despertar, 9)), List.of());

        var resultado = service.repetir(ACTOR, MODELO, DESTINO, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<FechaPropia>> escritas = ArgumentCaptor.forClass(Collection.class);
        verify(savePreferenciaPort).guardarFechasPropias(eq(ACTOR), escritas.capture());
        assertThat(escritas.getValue()).containsExactly(sabadoDestino(despertar, 9));
        assertThat(resultado.diasCopiados()).isEqualTo(1);
        assertThat(resultado.diasQueVuelvenAlPlan()).isZero();
        assertThat(resultado.habitos()).isEqualTo(1);
    }

    @Test
    void sinConfirmarNoReemplazaLoPropioDeLaSiguiente() {
        filas(List.of(sabadoModelo(despertar, 9)), List.of(sabadoDestino(despertar, 10)));

        assertThatThrownBy(() -> service.repetir(ACTOR, MODELO, DESTINO, false)).isInstanceOf(IllegalStateException.class);

        verify(savePreferenciaPort, never()).guardarFechasPropias(any(), anyCollection());
        verify(savePreferenciaPort, never()).borrarParaFechas(any(), anyCollection());
    }

    @Test
    void confirmadoReemplazaYBorraLoQueSobra() {
        filas(List.of(sabadoModelo(despertar, 9)),
                List.of(sabadoDestino(despertar, 10), sabado(gimnasio, LocalDate.of(2026, 11, 6), 8)));

        var resultado = service.repetir(ACTOR, MODELO, DESTINO, true);

        verify(savePreferenciaPort).guardarFechasPropias(eq(ACTOR), anyCollection());
        verify(savePreferenciaPort).borrarParaFechas(ACTOR, List.of(new SavePreferenciaHorarioPort.FechaABorrar(gimnasio.id(), LocalDate.of(2026, 11, 6))));
        assertThat(resultado.diasCopiados()).isEqualTo(1);
        assertThat(resultado.diasQueVuelvenAlPlan()).isEqualTo(1);
    }

    @Test
    void repetirDosVecesNoVuelveAEscribirNada() {
        filas(List.of(sabadoModelo(despertar, 9)), List.of(sabadoDestino(despertar, 9)));

        var resultado = service.repetir(ACTOR, MODELO, DESTINO, false);

        assertThat(resultado.diasCopiados()).isZero();
        verify(savePreferenciaPort).guardarFechasPropias(eq(ACTOR), eq(List.of()));
    }

    @Test
    void lasMismasSemanasOUnaSemanaQueYaPasoSeRechazan() {
        assertThatThrownBy(() -> service.repetir(ACTOR, MODELO, MODELO, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.repetir(ACTOR, DESTINO, SemanaCalendaria.desde(LocalDate.of(2026, 10, 12)), false))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(savePreferenciaPort);
    }

    @Test
    void nuncaEscribeEnHabitosQueNoLeCorresponden() {
        var ajeno = habito("De otra formacion");
        filas(List.of(sabadoModelo(ajeno, 9)), List.of());
        when(loadHabitoPort.porIds(any())).thenReturn(List.of(ajeno));

        var resultado = service.repetir(ACTOR, MODELO, DESTINO, false);

        assertThat(resultado.excluidos()).hasSize(1);
        verify(savePreferenciaPort).guardarFechasPropias(eq(ACTOR), eq(List.of()));
    }

    // ---- deshacer ------------------------------------------------------------------------------

    @Test
    void deshacerBorraLaSemanaEnteraDeTodosLosHabitos() {
        when(savePreferenciaPort.borrarFechasPropias(any(), anyCollection(), any(), any())).thenReturn(3);

        int borradas = service.deshacerSemana(ACTOR, DESTINO);

        assertThat(borradas).isEqualTo(3);
        verify(savePreferenciaPort).borrarFechasPropias(ACTOR, Set.of(), DESTINO.lunes(), DESTINO.domingo());
    }

    @Test
    void deshacerLaSemanaEnCursoSoloBorraDesdeManana() {
        service.deshacerSemana(ACTOR, MODELO);

        verify(savePreferenciaPort).borrarFechasPropias(ACTOR, Set.of(), LocalDate.of(2026, 10, 29), MODELO.domingo());
    }

    @Test
    void deshacerUnaSemanaQueYaPasoNoHaceNada() {
        assertThat(service.deshacerSemana(ACTOR, SemanaCalendaria.desde(LocalDate.of(2026, 10, 12)))).isZero();
        verify(savePreferenciaPort, never()).borrarFechasPropias(any(), anyCollection(), any(), any());
    }

    @Test
    void elAvisoPropioViajaEnLaOferta() {
        var conAviso = new FechaPropia(despertar.id(), LocalDate.of(2026, 10, 31), true, null, null, RecordatorioDelDia.con(List.of(30, 0)), null);
        filas(List.of(conAviso), List.of());

        var dia = service.ofrecer(ACTOR).orElseThrow().habitos().getFirst().dias().getFirst();

        assertThat(dia.horaDisparo()).isNull();
        assertThat(dia.recordatorioActivo()).isTrue();
        assertThat(dia.antelaciones()).containsExactly(30, 0);
    }
}
