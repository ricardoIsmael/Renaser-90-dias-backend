package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.CuotaCambios;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.domain.model.agenda.AgendaSemanal;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code buscar_huecos_para_habitos}: "hoy" y los horarios los resuelve {@code habits} (probado con
 * reloj entre 00:00 y 05:00 UTC en {@code HorarioDelDiaFinderServiceTest}). Aca se prueba lo que la
 * herramienta SI calcula: el cruce de la agenda con la franja de cada habito.
 */
class BuscarHuecosParaHabitosHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);
    private static final UUID MEDITAR = UUID.randomUUID();
    private static final UUID LEER = UUID.randomUUID();
    private static final UUID GYM = UUID.randomUUID();
    private static final UUID AGUA = UUID.randomUUID();
    private static final UUID PAUSADO = UUID.randomUUID();

    private final ConsultarHorariosPort puerto = mock(ConsultarHorariosPort.class);
    private final AgendaEnMemoria agendas = new AgendaEnMemoria();
    private final BuscarHuecosParaHabitosHerramienta herramienta = new BuscarHuecosParaHabitosHerramienta(puerto, agendas);

    private static HorarioDeHabito habito(UUID id, String titulo, LocalTime inicio, LocalTime limite) {
        return new HorarioDeHabito(id, titulo, inicio, limite, false, false, false, false, null);
    }

    private static InvocacionHerramienta invocacion(String ocupado, String fecha) {
        Map<String, String> argumentos = new HashMap<>();
        if (ocupado != null) {
            argumentos.put(BuscarHuecosParaHabitosHerramienta.ARGUMENTO_OCUPADO, ocupado);
        }
        if (fecha != null) {
            argumentos.put(BuscarHuecosParaHabitosHerramienta.ARGUMENTO_FECHA, fecha);
        }
        return new InvocacionHerramienta(BuscarHuecosParaHabitosHerramienta.NOMBRE, argumentos);
    }

    private static String texto(ResultadoHerramienta resultado) {
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }

    @Test
    @DisplayName("dice que habitos chocan, el hueco dentro de su franja y una hora sugerida ya calculada")
    void cruzaAgendaConFranjas() {
        when(puerto.deFecha(APRENDIZ, JUEVES)).thenReturn(new HorariosDelDia(JUEVES, 12, List.of(
                habito(MEDITAR, "Meditar", LocalTime.of(7, 0), LocalTime.of(8, 0)),
                habito(LEER, "Leer", LocalTime.of(12, 0), LocalTime.of(20, 0)),
                habito(GYM, "Gym", LocalTime.of(10, 0), LocalTime.of(12, 0)),
                habito(AGUA, "Tomar agua", null, null),
                new HorarioDeHabito(PAUSADO, "Correr", LocalTime.of(6, 0), null, false, false, true, false, null)),
                new CuotaCambios(0, 3, 3, false)));

        String texto = texto(herramienta.ejecutar(APRENDIZ, invocacion("09:00-13:00, 14:00-18:00", "2026-09-24")));

        assertThat(texto)
                .contains("Huecos del 2026-09-24 (dia 12 del programa). Ocupada: 09:00-13:00, 14:00-18:00.")
                .contains("Horas libres del dia: 00:00-09:00, 13:00-14:00, 18:00-24:00.")
                .contains("Meditar | franja 07:00-08:00 | sin choque: le queda libre toda su franja, sugerida=07:00")
                .contains("Leer | franja 12:00-20:00 | choca en parte | libre dentro de su franja: 13:00-14:00, "
                        + "18:00-20:00 | sugerida=13:00 (60 min libres)")
                .contains("Gym | franja 10:00-12:00 | sin hueco en su franja")
                .contains("Tomar agua | sin hora fija: le sirve cualquiera de las horas libres del dia")
                .contains("Correr | no cuenta ese dia (pausado)");
    }

    @Test
    @DisplayName("sin 'ocupado' usa la agenda guardada de ese dia de la semana (el 2026-09-24 es jueves)")
    void usaLaAgendaGuardada() {
        agendas.guardar(APRENDIZ, AgendaSemanal.vacia().conDias(Set.of(DayOfWeek.THURSDAY), "09:00-18:00")
                .conDias(Set.of(DayOfWeek.FRIDAY), "06:00-22:00"));
        when(puerto.deFecha(APRENDIZ, JUEVES)).thenReturn(new HorariosDelDia(JUEVES, 12, List.of(
                habito(MEDITAR, "Meditar", LocalTime.of(7, 0), LocalTime.of(8, 0))), new CuotaCambios(0, 3, 3, false)));

        assertThat(texto(herramienta.ejecutar(APRENDIZ, invocacion(null, "2026-09-24"))))
                .contains("Ocupada: 09:00-18:00.")
                .contains("Meditar | franja 07:00-08:00 | sin choque");
    }

    @Test
    @DisplayName("sin 'ocupado' y sin agenda guardada para ese dia, pide preguntarla")
    void sinAgendaGuardadaPidePreguntar() {
        when(puerto.deFecha(APRENDIZ, null)).thenReturn(new HorariosDelDia(JUEVES, 12, List.of(),
                new CuotaCambios(0, 3, 3, false)));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, invocacion(null, null));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("preguntale a que hora esta ocupada");
    }

    @Test
    @DisplayName("una agenda que no se entiende es un fallo legible y ni consulta los horarios")
    void agendaInvalidaFalla() {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, invocacion("de 9 a 6", null));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(puerto);
    }

    @Test
    @DisplayName("sin fecha pide 'hoy' a habits con null: el modelo no calcula la fecha")
    void sinFechaEsHoy() {
        when(puerto.deFecha(APRENDIZ, null)).thenReturn(new HorariosDelDia(JUEVES, 12, List.of(),
                new CuotaCambios(0, 3, 3, false)));

        assertThat(herramienta.ejecutar(APRENDIZ, invocacion("09:00-18:00", null)))
                .isInstanceOf(ResultadoHerramienta.Exito.class);
    }

    @Test
    @DisplayName("fuera de los 90 dias no hay habitos que encajar")
    void fueraDelPrograma() {
        when(puerto.deFecha(any(), any())).thenReturn(new HorariosDelDia(JUEVES, 91, List.of(),
                new CuotaCambios(0, 3, 3, false)));

        assertThat(texto(herramienta.ejecutar(APRENDIZ, invocacion("09:00-18:00", null))))
                .contains("fuera de sus 90 dias");
    }

    @Test
    @DisplayName("una cuenta suspendida recibe un fallo, no sus horarios")
    void suspendidaFalla() {
        when(puerto.deFecha(any(), any())).thenThrow(new NotAuthorizedException("suspendida"));

        assertThat(herramienta.ejecutar(APRENDIZ, invocacion("09:00-18:00", null)))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
    }
}
