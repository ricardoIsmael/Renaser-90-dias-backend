package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.CambioProgramado;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.CuotaCambios;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code consultar_horarios}: la herramienta no calcula "hoy" ni la cuota — eso es de
 * {@code habits}, y esta probado en {@code HorarioDelDiaFinderServiceTest} con un reloj entre
 * 00:00 y 05:00 UTC. Aca se prueba lo que SI decide: el argumento, el rango del programa, la
 * traduccion de fallos y el texto que ve el modelo.
 */
class ConsultarHorariosHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);
    private static final UUID MEDITAR = UUID.randomUUID();
    private static final UUID DORMIR = UUID.randomUUID();

    private final ConsultarHorariosPort puerto = mock(ConsultarHorariosPort.class);
    private final ConsultarHorariosHerramienta herramienta = new ConsultarHorariosHerramienta(puerto);

    @Test
    @DisplayName("la fecha es opcional: sin argumento no falta nada y se pide 'hoy' a habits con null")
    void sinFechaPideHoyAHabits() {
        when(puerto.deFecha(APRENDIZ, null)).thenReturn(dia(20, List.of(), new CuotaCambios(0, 3, 3, true)));

        var invocacion = InvocacionHerramienta.sinArgumentos(ConsultarHorariosHerramienta.NOMBRE);
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, invocacion);

        assertThat(herramienta.definicion().obligatoriosFaltantesEn(invocacion)).isEmpty();
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(texto(resultado)).contains("No tiene habitos activos").contains("semana de acomodo libre");
        verify(puerto).deFecha(APRENDIZ, null);
    }

    @Test
    @DisplayName("devuelve horario, marcas ciertas y los cambios que quedan en la semana")
    void armaElTextoConHorarioMarcasYCuota() {
        when(puerto.deFecha(APRENDIZ, JUEVES)).thenReturn(dia(26, List.of(
                new HorarioDeHabito(MEDITAR, "Meditar", LocalTime.of(6, 0), LocalTime.of(8, 0), true, true, false,
                        false, new CambioProgramado(LocalTime.of(7, 0), LocalTime.of(9, 0), JUEVES.plusDays(2))),
                new HorarioDeHabito(DORMIR, "Dormir", LocalTime.of(22, 0), null, false, false, false, true, null)),
                new CuotaCambios(1, 2, 3, false)));

        String texto = texto(herramienta.ejecutar(APRENDIZ, conFecha("2026-09-24")));

        assertThat(texto).contains("Horarios del 2026-09-24 (dia 26 del programa)")
                .contains("habito_id=" + MEDITAR + " | Meditar | inicio=06:00 limite=08:00 | horario_propio=si"
                        + " | apagado_ese_dia=si | cambio_programado=07:00 desde 2026-09-26")
                .contains("habito_id=" + DORMIR + " | Dormir | inicio=22:00 | obligatorio=si")
                .contains("1 usados, 2 restantes de 3");
        assertThat(texto).doesNotContain("pausado=si");
    }

    @Test
    @DisplayName("una fecha mal escrita es un fallo legible y no llega a habits")
    void fechaMalEscritaFalla() {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, conFecha("24/09/2026"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("yyyy-MM-dd");
        verify(puerto, never()).deFecha(any(), any());
    }

    @Test
    @DisplayName("una fecha que no existe en el calendario tambien se rechaza")
    void fechaInexistenteFalla() {
        assertThat(herramienta.ejecutar(APRENDIZ, conFecha("2026-02-30")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(puerto, never()).deFecha(any(), any());
    }

    @Test
    @DisplayName("una fecha fuera de los 90 dias es un fallo claro, por arriba y por abajo")
    void fechaFueraDelProgramaFalla() {
        LocalDate lejos = LocalDate.of(2027, 3, 1);
        LocalDate antes = LocalDate.of(2026, 1, 1);
        when(puerto.deFecha(APRENDIZ, lejos)).thenReturn(conDia(lejos, 91));
        when(puerto.deFecha(APRENDIZ, antes)).thenReturn(conDia(antes, -1));

        ResultadoHerramienta despues = herramienta.ejecutar(APRENDIZ, conFecha(lejos.toString()));
        ResultadoHerramienta previo = herramienta.ejecutar(APRENDIZ, conFecha(antes.toString()));

        assertThat(((ResultadoHerramienta.Fallo) despues).motivo()).contains("fuera de sus 90 dias");
        assertThat(previo).isInstanceOf(ResultadoHerramienta.Fallo.class);
    }

    /** E-276: "el 5 de noviembre" llego como 2025-11-05 y la respuesta fue "queda fuera de tus 90 dias". */
    @Test
    @DisplayName("una fecha armada con un año viejo se corrige al año en que cae dentro del programa")
    void fechaConAnioViejoSeCorrige() {
        LocalDate hoy = LocalDate.of(2026, 9, 25);
        LocalDate conAnioViejo = LocalDate.of(2025, 11, 5);
        LocalDate corregida = LocalDate.of(2026, 11, 5);
        when(puerto.deFecha(APRENDIZ, conAnioViejo)).thenReturn(conDia(conAnioViejo, -306));
        when(puerto.deFecha(APRENDIZ, null)).thenReturn(conDia(hoy, 18));
        when(puerto.deFecha(APRENDIZ, corregida)).thenReturn(conDia(corregida, 59));

        String texto = texto(herramienta.ejecutar(APRENDIZ, conFecha("2025-11-05")));

        assertThat(texto).contains("Horarios del 2026-11-05 (dia 59 del programa)");
    }

    @Test
    @DisplayName("el dia 0 (programa aun sin activar) y el dia 90 si se consultan")
    void bordesDelProgramaSeConsultan() {
        when(puerto.deFecha(APRENDIZ, JUEVES)).thenReturn(conDia(JUEVES, 90));
        when(puerto.deFecha(APRENDIZ, null)).thenReturn(dia(0, List.of(), new CuotaCambios(0, 3, 3, true)));

        var sinFecha = InvocacionHerramienta.sinArgumentos(ConsultarHorariosHerramienta.NOMBRE);

        assertThat(herramienta.ejecutar(APRENDIZ, conFecha("2026-09-24")))
                .isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(herramienta.ejecutar(APRENDIZ, sinFecha)).isInstanceOf(ResultadoHerramienta.Exito.class);
    }

    @Test
    @DisplayName("cuenta suspendida o sin programa: fallo legible, nunca excepcion")
    void fallosDeHabitsSeTraducen() {
        UserId suspendido = UserId.of(UUID.randomUUID());
        UserId sinPrograma = UserId.of(UUID.randomUUID());
        when(puerto.deFecha(suspendido, null)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        when(puerto.deFecha(sinPrograma, null)).thenThrow(new NoSuchElementException("Participante no encontrado"));

        var sinFecha = InvocacionHerramienta.sinArgumentos(ConsultarHorariosHerramienta.NOMBRE);
        ResultadoHerramienta rechazado = herramienta.ejecutar(suspendido, sinFecha);
        ResultadoHerramienta ausente = herramienta.ejecutar(sinPrograma, sinFecha);

        assertThat(((ResultadoHerramienta.Fallo) rechazado).motivo()).contains("suspendida")
                .doesNotContain("Cuenta suspendida");
        assertThat(((ResultadoHerramienta.Fallo) ausente).motivo()).doesNotContain(sinPrograma.toString());
    }

    private static InvocacionHerramienta conFecha(String fecha) {
        return new InvocacionHerramienta(ConsultarHorariosHerramienta.NOMBRE,
                Map.of(ConsultarHorariosHerramienta.ARGUMENTO_FECHA, fecha));
    }

    private static HorariosDelDia dia(int diaPrograma, List<HorarioDeHabito> habitos, CuotaCambios cuota) {
        return new HorariosDelDia(JUEVES, diaPrograma, habitos, cuota);
    }

    private static HorariosDelDia conDia(LocalDate fecha, int diaPrograma) {
        return new HorariosDelDia(fecha, diaPrograma, List.of(), new CuotaCambios(0, 3, 3, false));
    }

    private static String texto(ResultadoHerramienta resultado) {
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }
}
