package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.ports.out.mapa.ConsultarMapaDeRenacimientoPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.participante.ConsultarTratoDeLaPersonaPort;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.EstadoDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.HabitoDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.HabitoPausado;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.rag.domain.model.mapa.ProximoHito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-176: la situacion del turno lleva los habitos de hoy con su estado y los pausados, y si no se
 * pueden leer sale igual, sin ellos.
 *
 * <p>El reloj esta a las 03:00 UTC a proposito (regla 02): son las 22:00 del dia ANTERIOR en Lima.
 * Un plazo de las 23:30 de Lima (04:30 UTC del dia siguiente) todavia no vencio, aunque en UTC ya
 * sea otro dia.
 */
class SituacionDelTurnoServiceTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    /** Sabado 26/09 a las 22:00 en Lima. */
    private static final Instant TRES_AM_UTC = Instant.parse("2026-09-27T03:00:00Z");
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 9, 26);
    private static final SituacionDelAprendiz DIA_12 = new SituacionDelAprendiz(12, 2, HOY_EN_LIMA);

    private final ConsultarSituacionDelAprendizPort situacionPort = mock(ConsultarSituacionDelAprendizPort.class);
    private final ConsultarAgendaHabitosPort agendaPort = mock(ConsultarAgendaHabitosPort.class);
    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final ConsultarTratoDeLaPersonaPort tratoPort = mock(ConsultarTratoDeLaPersonaPort.class);
    private final ConsultarMapaDeRenacimientoPort mapaPort = mock(ConsultarMapaDeRenacimientoPort.class);
    private final SituacionDelTurnoService service = new SituacionDelTurnoService(situacionPort, agendaPort, planPort,
            FixedClock.at(TRES_AM_UTC), tratoPort, mapaPort);

    /** D-233: la prioridad y el proximo hito del Mapa viajan en la situacion; si falla, sin Mapa y el turno sigue. */
    @Test
    @DisplayName("D-233: la situacion lleva la prioridad y el proximo hito del Mapa, y nada si falla la lectura")
    void resumenDelMapa() {
        when(situacionPort.de(APRENDIZ)).thenReturn(Optional.of(DIA_12));
        when(mapaPort.de(APRENDIZ)).thenReturn(new MapaDeLaPersona(true, true, "salud", List.of(),
                List.of(new MapaDeLaPersona.Hito("salud", 30, "89 kg")), null, List.of(), List.of()));

        var mapa = service.de(APRENDIZ).orElseThrow().mapa();

        assertThat(mapa.prioridad()).isEqualTo("salud");
        assertThat(mapa.proximo()).isEqualTo(new ProximoHito(30, 18));
        assertThat(mapa.hito().texto()).isEqualTo("89 kg");

        when(mapaPort.de(APRENDIZ)).thenThrow(new IllegalStateException("caida"));
        assertThat(service.de(APRENDIZ).orElseThrow().mapa()).isNull();
    }

    /** E-457: el trato de su ficha viaja en la situacion; si no se puede leer, neutro y el turno sigue. */
    @Test
    @DisplayName("E-457: la situacion lleva el trato de la ficha, y neutro si falla la lectura")
    void tratoDeLaFicha() {
        when(situacionPort.de(APRENDIZ)).thenReturn(Optional.of(DIA_12));
        when(tratoPort.de(APRENDIZ)).thenReturn(ConsultarTratoDeLaPersonaPort.TratoDeLaPersona.MASCULINO);

        assertThat(service.de(APRENDIZ).orElseThrow().trato())
                .isEqualTo(ConsultarTratoDeLaPersonaPort.TratoDeLaPersona.MASCULINO);

        when(tratoPort.de(APRENDIZ)).thenThrow(new IllegalStateException("caida"));
        assertThat(service.de(APRENDIZ).orElseThrow().trato())
                .isEqualTo(ConsultarTratoDeLaPersonaPort.TratoDeLaPersona.NEUTRO);
    }

    private static HabitoDelDia habito(String titulo, String estado, Instant plazo, boolean evidencia, String clave) {
        return new HabitoDelDia(UUID.randomUUID(), titulo, estado, null, null, plazo, evidencia, List.of(), clave);
    }

    private static HabitoDelPlan delPlan(String titulo, boolean pausadoHoy, LocalDate hasta) {
        return new HabitoDelPlan(UUID.randomUUID(), titulo, false, pausadoHoy, hasta);
    }

    private void conPlan(HabitoDelPlan... habitos) {
        when(planPort.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(HOY_EN_LIMA, List.of(habitos), List.of()));
    }

    @Test
    @DisplayName("lleva cada habito de hoy con su estado en palabras, si pide foto, y los pausados con su fin")
    void habitosDeHoyYPausados() {
        when(situacionPort.de(APRENDIZ)).thenReturn(Optional.of(DIA_12));
        when(agendaPort.deHoyDe(APRENDIZ)).thenReturn(List.of(
                habito("JUGO VERDE", "COMPLETADO", null, true, null),
                habito("ULTIMA COMIDA", "COMPLETADO", null, false, null),
                habito("MEDITAR", "PENDIENTE", Instant.parse("2026-09-27T04:30:00Z"), true, null),
                habito("CAMINAR", "PENDIENTE", Instant.parse("2026-09-27T02:00:00Z"), false, null),
                habito("LECTURA", "EXPIRADO", null, false, null),
                habito("CLASE DIARIA", "EN_CURSO", null, true, HabitoDelDia.CLAVE_CLASE_DIARIA)));
        conPlan(delPlan("DUCHA FRIA", true, LocalDate.of(2026, 9, 27)), delPlan("YOGA", true, null),
                delPlan("MEDITAR", false, null));

        HabitosDeHoy habitos = service.de(APRENDIZ).orElseThrow().habitos();

        assertThat(habitos.deHoy()).containsExactly(
                new HabitoDeHoy("JUGO VERDE", EstadoDeHoy.HECHO, true),
                new HabitoDeHoy("ULTIMA COMIDA", EstadoDeHoy.HECHO, false),
                // 04:30 UTC = 23:30 del sabado en Lima: a las 22:00 de Lima sigue pendiente
                new HabitoDeHoy("MEDITAR", EstadoDeHoy.PENDIENTE, true),
                new HabitoDeHoy("CAMINAR", EstadoDeHoy.VENCIDO, false),
                new HabitoDeHoy("LECTURA", EstadoDeHoy.VENCIDO, false),
                // la Clase diaria pide evidencia en el catalogo, pero se cierra con su resumen (D-171)
                new HabitoDeHoy("CLASE DIARIA", EstadoDeHoy.EN_CURSO, false));
        assertThat(habitos.pausados()).containsExactly(
                new HabitoPausado("DUCHA FRIA", LocalDate.of(2026, 9, 27)), new HabitoPausado("YOGA", null));
    }

    @Test
    @DisplayName("E-290: un habito renombrado lleva tambien el titulo del programa")
    void renombradoConTituloDelPrograma() {
        when(situacionPort.de(APRENDIZ)).thenReturn(Optional.of(DIA_12));
        when(agendaPort.deHoyDe(APRENDIZ)).thenReturn(List.of(new HabitoDelDia(UUID.randomUUID(), "Batido de papaya",
                "COMPLETADO", null, null, null, true, List.of(), null, "JUGO VERDE")));
        conPlan();

        assertThat(service.de(APRENDIZ).orElseThrow().habitos().deHoy()).containsExactly(
                new HabitoDeHoy("Batido de papaya", EstadoDeHoy.HECHO, true, "JUGO VERDE"));
    }

    @Test
    @DisplayName("el dia, la fase y la fecha siguen siendo los del puerto de siempre")
    void conservaLaSituacion() {
        when(situacionPort.de(APRENDIZ)).thenReturn(Optional.of(DIA_12));
        when(agendaPort.deHoyDe(APRENDIZ)).thenReturn(List.of());
        conPlan();

        SituacionDelAprendiz situacion = service.de(APRENDIZ).orElseThrow();

        assertThat(situacion.diaPrograma()).isEqualTo(12);
        assertThat(situacion.fase()).isEqualTo(2);
        assertThat(situacion.hoy()).isEqualTo(HOY_EN_LIMA);
        assertThat(situacion.habitos()).isEqualTo(new HabitosDeHoy(List.of(), List.of()));
    }

    @Test
    @DisplayName("si la agenda falla, la situacion sale igual con dia y fase y sin habitos: el turno no se cae")
    void agendaQueFalla() {
        when(situacionPort.de(APRENDIZ)).thenReturn(Optional.of(DIA_12));
        when(agendaPort.deHoyDe(APRENDIZ)).thenThrow(new IllegalStateException("base caida"));

        assertThat(service.de(APRENDIZ)).contains(DIA_12);
    }

    @Test
    @DisplayName("si el plan falla, tampoco se dan los de hoy: una lista sin pausados diria que no los hay")
    void planQueFalla() {
        when(situacionPort.de(APRENDIZ)).thenReturn(Optional.of(DIA_12));
        when(agendaPort.deHoyDe(APRENDIZ)).thenReturn(List.of(habito("MEDITAR", "PENDIENTE", null, false, null)));
        when(planPort.planDe(APRENDIZ)).thenThrow(new java.util.NoSuchElementException("sin programa"));

        assertThat(service.de(APRENDIZ).orElseThrow().habitos()).isNull();
    }

    @Test
    @DisplayName("quien no cursa el programa no tiene situacion, y ni se consultan sus habitos")
    void sinPrograma() {
        when(situacionPort.de(APRENDIZ)).thenReturn(Optional.empty());

        assertThat(service.de(APRENDIZ)).isEmpty();
        verifyNoInteractions(agendaPort, planPort);
    }
}
