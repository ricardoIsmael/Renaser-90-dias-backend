package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.CuotaCambios;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.FichaDeHabito;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoSemanal;
import com.renaser.os.rag.application.services.herramientas.HorarioSemanalPedido.Accion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #71: el texto de cada tarjeta de propuesta del panel de SER («Marcar 'X' como hecho…», «Pausar 'X'…») nombra el hábito con el título del catálogo,
 * que llega en MAYÚSCULAS. La persona lo lee: va en tipo oración, como en la app ({@code TituloEnTipoOracion}). Solo cambia el texto de la tarjeta: la
 * invocación que se guarda y lo que ve el modelo no se tocan. Un título que ya trae minúsculas (o el nombre que la persona le puso) se deja igual.
 */
class ResumenesDePropuestaEnTipoOracionTest {

    private static final UUID ID = UUID.fromString("66507383-7219-43ab-aa42-2fbc76152b82");
    private static final String RITUAL = "RITUAL TIERRA - AGUA - FUEGO (noche)";
    private static final String RITUAL_LEIDO = "Ritual tierra - agua - fuego (noche)";
    private static final String ULTIMA_COMIDA = "ÚLTIMA COMIDA DEL DÍA";
    private static final String ULTIMA_COMIDA_LEIDA = "Última comida del día";
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 14);

    private static HorarioDeHabito horario(String titulo) {
        return new HorarioDeHabito(ID, titulo, LocalTime.of(6, 0), LocalTime.of(7, 0), false, false, false, false, null);
    }

    @Test
    @DisplayName("marcar como hecho: «Marcar 'Ritual tierra - agua - fuego (noche)' como hecho (+8 puntos…)»")
    void marcarComoHecho() {
        HabitoDelDia habito = new HabitoDelDia(ID, RITUAL, "PENDIENTE", 8, 10, null, false);

        assertThat(PropuestaDeMarcarHabito.resumenDe(habito))
                .isEqualTo("Marcar '" + RITUAL_LEIDO + "' como hecho (+8 puntos si lo confirmas ahora)");
    }

    @Test
    @DisplayName("pausar, cambiar la pausa y reactivar")
    void pausar() {
        HabitoDelPlan activo = new HabitoDelPlan(ID, ULTIMA_COMIDA, false, false, null);
        HabitoDelPlan pausado = new HabitoDelPlan(ID, ULTIMA_COMIDA, false, true, null);

        assertThat(PropuestaDePausarHabito.resumenDe(activo, new PropuestaDePausarHabito.Pedido(ID, true, null)))
                .startsWith("Pausar '" + ULTIMA_COMIDA_LEIDA + "' desde hoy sin fecha de fin");
        assertThat(PropuestaDePausarHabito.resumenDe(pausado, new PropuestaDePausarHabito.Pedido(ID, true, null)))
                .isEqualTo("Cambiar la pausa de '" + ULTIMA_COMIDA_LEIDA + "': ahora sin fecha de fin");
        assertThat(PropuestaDePausarHabito.resumenDe(pausado, new PropuestaDePausarHabito.Pedido(ID, false, null)))
                .isEqualTo("Reactivar '" + ULTIMA_COMIDA_LEIDA + "' en tu plan");
    }

    @Test
    @DisplayName("apagar un día y volver a activarlo")
    void apagarUnDia() {
        HorarioDeHabito habito = horario(RITUAL);

        assertThat(PropuestaDeApagarDia.resumenDe(new PropuestaDeApagarDia.DiaPedido(ID, LUNES, true), habito, true))
                .startsWith("Apagar '" + RITUAL_LEIDO + "' solo hoy, ");
        assertThat(PropuestaDeApagarDia.resumenDe(new PropuestaDeApagarDia.DiaPedido(ID, LUNES, false), habito, false))
                .startsWith("Volver a activar '" + RITUAL_LEIDO + "' el ");
    }

    @Test
    @DisplayName("cambiar el horario, general o de un solo día")
    void cambiarElHorario() {
        HorarioDeHabito actual = horario(ULTIMA_COMIDA);
        var general = new PropuestaDeCambioDeHorario.CambioPedido(ID, LocalTime.of(6, 30), LocalTime.of(7, 30), null);
        var unDia = new PropuestaDeCambioDeHorario.CambioPedido(ID, LocalTime.of(6, 30), LocalTime.of(7, 30), LUNES);

        assertThat(PropuestaDeCambioDeHorario.resumenDe(general, actual, LUNES))
                .startsWith("Cambiar '" + ULTIMA_COMIDA_LEIDA + "' de 06:00-07:00 a 06:30-07:30 como horario general");
        assertThat(PropuestaDeCambioDeHorario.resumenDe(unDia, actual, LUNES))
                .startsWith("Cambiar '" + ULTIMA_COMIDA_LEIDA + "' solo el ");
    }

    @Test
    @DisplayName("el horario de un día de la semana: fijar, apagar y quitar lo propio")
    void horarioPorDiaDeSemana() {
        HorarioDeHabito habito = horario(RITUAL);
        HorariosDelDia proxima = new HorariosDelDia(LUNES, 17, List.of(habito), new CuotaCambios(1, 2, 3, false));

        assertThat(PropuestaDeHorarioPorDiaDeSemana.resumenValidado(
                new HorarioSemanalPedido(ID, DayOfWeek.MONDAY, Accion.FIJAR, LocalTime.of(5, 0), null), habito, proxima))
                .startsWith("Fijar '" + RITUAL_LEIDO + "' los lunes a 05:00");
        assertThat(PropuestaDeHorarioPorDiaDeSemana.resumenValidado(
                new HorarioSemanalPedido(ID, DayOfWeek.MONDAY, Accion.APAGAR, null, null), habito, proxima))
                .isEqualTo("Apagar '" + RITUAL_LEIDO + "' los lunes, todas las semanas, hasta que lo vuelva a activar.");
        assertThat(PropuestaDeHorarioPorDiaDeSemana.resumenValidado(
                new HorarioSemanalPedido(ID, DayOfWeek.MONDAY, Accion.QUITAR, null, null), habito, proxima))
                .startsWith("Quitar lo propio de los lunes en '" + RITUAL_LEIDO + "'");
    }

    @Test
    @DisplayName("anotar el día de un hábito semanal")
    void diaDeUnHabitoSemanal() {
        HabitoSemanal habito = new HabitoSemanal(ID, "CORRER POR LA MAÑANA", null, List.of());

        assertThat(PropuestaDeDiaDeHabitoSemanal.resumenDe(habito, LocalDate.of(2026, 9, 24)))
                .isEqualTo("Anotar el jueves 24/09 como tu dia de 'Correr por la mañana' esta semana");
    }

    @Test
    @DisplayName("renombrar: el nombre del programa se lee en tipo oración; el que puso la persona, tal cual")
    void renombrar() {
        FichaDeHabito sinRenombre = new FichaDeHabito(ID, "AGUA TIBIA CON LIMÓN", null, null, true);
        FichaDeHabito renombrada = new FichaDeHabito(ID, "AGUA TIBIA CON LIMÓN", "MI AGUA", null, true);

        assertThat(sinRenombre.tituloParaLeer()).isEqualTo("Agua tibia con limón");
        // Su nombre propio no se toca, aunque lo haya escrito todo en mayúsculas; el del programa sí se lee en tipo oración.
        assertThat(renombrada.tituloParaLeer()).isEqualTo("MI AGUA");
        // Para comparar y para el modelo, el valor de siempre.
        assertThat(sinRenombre.tituloVisible()).isEqualTo("AGUA TIBIA CON LIMÓN");
    }

    @Test
    @DisplayName("un título que ya trae minúsculas se deja igual (el renombre de la persona, «Clase diaria»)")
    void unTituloConMinusculasNoCambia() {
        assertThat(PropuestaDeMarcarHabito.resumenDe(new HabitoDelDia(ID, "Clase diaria", "PENDIENTE", 8, 10, null, false)))
                .isEqualTo("Marcar 'Clase diaria' como hecho (+8 puntos si lo confirmas ahora)");
        assertThat(PropuestaDeMarcarHabito.resumenDe(new HabitoDelDia(ID, "Mi jugo verde", "PENDIENTE", 8, 10, null, false)))
                .contains("'Mi jugo verde'");
    }
}
