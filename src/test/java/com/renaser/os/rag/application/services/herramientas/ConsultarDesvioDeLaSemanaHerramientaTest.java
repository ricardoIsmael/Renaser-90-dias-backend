package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarHabitosVencidosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarHabitosVencidosPort.HabitoVencido;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.BalanceDelEje;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.DiaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.PlanDeManana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.ProgresoDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort.SemaforoDelAprendiz;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort.Tramo;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code consultar_desvio_de_la_semana} (D-177): hechos, sin juicio, y el resto si una fuente falla.
 * "Hoy" lo da {@code rocks} en la zona de la persona (probado en {@code RocasDelAprendizServiceTest} con
 * el reloj a las 03:00 UTC); aca se prueba que los dias terminados son de {@code desde} a AYER.
 */
class ConsultarDesvioDeLaSemanaHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 21);
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);

    private final ConsultarRocasDelAprendizPort rocas = mock(ConsultarRocasDelAprendizPort.class);
    private final ConsultarHabitosVencidosPort vencidos = mock(ConsultarHabitosVencidosPort.class);
    private final GestionarPlanDeHabitosPort plan = mock(GestionarPlanDeHabitosPort.class);
    private final ConsultarSemaforoDelAprendizPort semaforo = mock(ConsultarSemaforoDelAprendizPort.class);
    private final ConsultarDesvioDeLaSemanaHerramienta herramienta =
            new ConsultarDesvioDeLaSemanaHerramienta(rocas, vencidos, plan, semaforo);

    @Test
    @DisplayName("junta rocas por eje, objetivos, habitos vencidos, pausas y semaforo; hoy no cuenta como incumplido")
    void juntaLosHechosDeLaSemana() {
        when(rocas.progresoDeLaSemana(APRENDIZ)).thenReturn(progreso());
        when(rocas.deLaSemana(APRENDIZ)).thenReturn(new RocasDeLaSemana(4, LUNES, LUNES.plusDays(6), List.of(
                new RocaDeLaSemana("CUERPO", "Correr 3 veces", null, null, false, false, 5))));
        when(plan.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(JUEVES, List.of(
                new HabitoDelPlan(UUID.randomUUID(), "Caminar", false, true, LocalDate.of(2026, 9, 30)),
                new HabitoDelPlan(UUID.randomUUID(), "Leer", false, false, null)), List.of()));
        when(vencidos.vencidosEntre(APRENDIZ, LUNES, JUEVES.minusDays(1))).thenReturn(List.of(
                new HabitoVencido(LUNES, "Ducha fria"), new HabitoVencido(LUNES.plusDays(1), "Ducha fria"),
                new HabitoVencido(LUNES.plusDays(2), "Leer 20 minutos")));
        when(semaforo.de(APRENDIZ)).thenReturn(Optional.of(new SemaforoDelAprendiz(
                new Tramo(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 23), new BigDecimal("72.50"),
                        "Requiere atención", 7),
                new Tramo(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18), new BigDecimal("81.0"), "Al día", 7))));

        String texto = texto(herramienta.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(
                ConsultarDesvioDeLaSemanaHerramienta.NOMBRE)));

        assertThat(texto).contains("Semana 4 del programa (2026-09-21 al 2026-09-27), hoy es jueves 2026-09-24. "
                        + "Dias ya terminados: del 2026-09-21 al 2026-09-23.")
                .contains("- CUERPO (objetivo de la semana: Correr 3 veces): 1 completada(s) y 3 sin completar, de 4 "
                        + "planificada(s)")
                .contains("- TRABAJO (sin objetivo esta semana): sin acciones planificadas")
                .contains("Dias terminados sin rocas planificadas: martes 2026-09-22.")
                .contains("Hoy (todavia en curso): 0 de 2 completadas.")
                .contains("Avance de la semana: 25%. Ritmo de los ultimos 7 dias: CRITICO.")
                .contains("Habitos vencidos sin cumplir en los dias terminados: 3")
                .contains("- Ducha fria: 2 vez/veces (2026-09-21, 2026-09-22)")
                .contains("Habitos en pausa hoy (no se le piden, no son incumplimiento): Caminar (hasta el 2026-09-30).")
                .doesNotContain("Leer (")
                .contains("ultimos 7 dias cerrados (2026-09-17 al 2026-09-23): 72.5%, Requiere atención")
                .contains("Ultima semana cerrada del semaforo (de sabado a viernes) (2026-09-12 al 2026-09-18): 81%")
                .contains(TextoDelDesvio.SOLO_HECHOS)
                .doesNotContain("No pude leer");
        // Los dias terminados van hasta AYER: lo de hoy todavia se puede hacer.
        verify(vencidos).vencidosEntre(APRENDIZ, LUNES, LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("sin el programa de rocas: sale lo de habitos, desde el lunes, y dice que falto")
    void sinRocasSaleElResto() {
        when(rocas.progresoDeLaSemana(APRENDIZ)).thenThrow(new NotAuthorizedException("sin programa de rocas"));
        when(rocas.deLaSemana(APRENDIZ)).thenThrow(new NotAuthorizedException("sin programa de rocas"));
        when(plan.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(JUEVES, List.of(), List.of()));
        when(vencidos.vencidosEntre(any(), any(), any())).thenReturn(List.of());
        when(semaforo.de(APRENDIZ)).thenReturn(Optional.empty());

        String texto = texto(herramienta.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(
                ConsultarDesvioDeLaSemanaHerramienta.NOMBRE)));

        assertThat(texto).contains("Semana en curso (desde el 2026-09-21)")
                .contains("Habitos vencidos sin cumplir en los dias terminados: ninguno.")
                .contains("Habitos en pausa hoy: ninguno.")
                .contains("Semaforo: no se le mide.")
                .contains("No pude leer: el avance de sus rocas, sus objetivos de la semana.");
        verify(vencidos).vencidosEntre(APRENDIZ, LUNES, LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("D-203: sin Dia 1 elegido la semana 1 va sin fechas, no hay dias terminados ni se consultan vencidos")
    void sinDiaUnoNoHayFechas() {
        when(rocas.progresoDeLaSemana(APRENDIZ)).thenReturn(new ProgresoDeLaSemana(1, null, null, JUEVES, 0, List.of(),
                "OK", 0, List.of(), plan(), false));
        when(rocas.deLaSemana(APRENDIZ)).thenReturn(new RocasDeLaSemana(1, null, null, List.of()));
        when(plan.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(JUEVES, List.of(), List.of()));
        when(semaforo.de(APRENDIZ)).thenReturn(Optional.empty());

        String texto = texto(herramienta.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(
                ConsultarDesvioDeLaSemanaHerramienta.NOMBRE)));

        assertThat(texto).contains("Semana 1 del programa (todavia sin fechas: no eligio su Dia 1), hoy es jueves "
                        + "2026-09-24. Dias ya terminados: ninguno todavia.")
                .doesNotContain("null");
        verifyNoInteractions(vencidos);
    }

    @Test
    @DisplayName("el lunes todavia no hay dias terminados: no se consultan vencidos, y un semaforo caido no tumba el resto")
    void lunesSinDiasTerminados() {
        when(rocas.progresoDeLaSemana(APRENDIZ)).thenReturn(new ProgresoDeLaSemana(4, LUNES, LUNES.plusDays(6), LUNES,
                0, List.of(new DiaDeLaSemana(LUNES, 0, null, true)), "OK", 5, List.of(), plan(), false));
        when(rocas.deLaSemana(APRENDIZ)).thenReturn(new RocasDeLaSemana(4, LUNES, LUNES.plusDays(6), List.of()));
        when(plan.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(LUNES, List.of(), List.of()));
        when(semaforo.de(APRENDIZ)).thenThrow(new IllegalStateException("base caida"));

        String texto = texto(herramienta.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(
                ConsultarDesvioDeLaSemanaHerramienta.NOMBRE)));

        assertThat(texto).contains("Dias ya terminados: ninguno todavia.")
                .contains("Habitos vencidos sin cumplir: todavia no hay dias terminados esta semana.")
                .contains("Hoy: sin rocas planificadas.")
                .contains("No pude leer: su semaforo.");
        verifyNoInteractions(vencidos);
    }

    @Test
    @DisplayName("si no se puede saber que dia es para la persona, es un fallo y no se consulta nada mas")
    void sinNingunaFuenteEsFallo() {
        when(rocas.progresoDeLaSemana(APRENDIZ)).thenThrow(new IllegalStateException("x"));
        when(rocas.deLaSemana(APRENDIZ)).thenThrow(new IllegalStateException("x"));
        when(plan.planDe(APRENDIZ)).thenThrow(new IllegalStateException("x"));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(
                ConsultarDesvioDeLaSemanaHerramienta.NOMBRE));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(vencidos, semaforo);
    }

    private static ProgresoDeLaSemana progreso() {
        return new ProgresoDeLaSemana(4, LUNES, LUNES.plusDays(6), JUEVES, 25, List.of(
                new DiaDeLaSemana(LUNES, 1, 2, false),
                new DiaDeLaSemana(LUNES.plusDays(1), 0, null, false),
                new DiaDeLaSemana(LUNES.plusDays(2), 0, 2, false),
                new DiaDeLaSemana(JUEVES, 0, 2, true),
                new DiaDeLaSemana(JUEVES.plusDays(1), null, null, false)),
                "CRITICO", 1, List.of(new BalanceDelEje("CUERPO", 4, 1), new BalanceDelEje("TRABAJO", 0, 0)),
                plan(), false);
    }

    private static PlanDeManana plan() {
        return new PlanDeManana(false, 0, false, LocalTime.of(18, 0), false);
    }

    private static String texto(ResultadoHerramienta resultado) {
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }
}
