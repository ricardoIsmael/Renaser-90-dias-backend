package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort.CambioDeHorario;
import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort.HorarioCambiado;
import com.renaser.os.rag.application.ports.out.horarios.AjustarHorariosPort.HorarioSemanal;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.CuotaCambios;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las escrituras de horario que corren al tocar "Confirmar" (D-153): delegan en {@code habits} por
 * el puerto, y un rechazo del negocio vuelve como {@code Fallo} legible, sin el mensaje crudo.
 */
class ConfirmablesDeHorarioTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID MEDITAR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 11);

    private final AjustarHorariosPort ajustar = mock(AjustarHorariosPort.class);
    private final ConsultarHorariosPort horarios = mock(ConsultarHorariosPort.class);

    private static String motivo(ResultadoHerramienta resultado) {
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        return ((ResultadoHerramienta.Fallo) resultado).motivo();
    }

    @Test
    @DisplayName("cambio general: delega con los argumentos guardados y responde con lo que devolvio habits")
    void cambioDeHorario() {
        CambioDeHorarioConfirmable confirmable = new CambioDeHorarioConfirmable(ajustar);
        when(ajustar.cambiarHorario(APRENDIZ, new CambioDeHorario(MEDITAR, LocalTime.of(6, 30), null, null)))
                .thenReturn(new HorarioCambiado(LocalTime.of(6, 30), null, LocalDate.of(2026, 9, 10), 1, 3, false));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, new InvocacionHerramienta(
                PropuestaDeCambioDeHorario.NOMBRE, Map.of("habito_id", MEDITAR.toString(), "hora_inicio", "06:30")));

        assertThat(confirmable.herramienta()).isEqualTo(PropuestaDeCambioDeHorario.NOMBRE);
        assertThat(resultado).isEqualTo(ResultadoHerramienta.exito("Horario cambiado: 06:30 desde el jueves "
                + "2026-09-10. Le quedan 1 de 3 cambios de horario esa semana."));
    }

    @Test
    @DisplayName("si entre proponer y confirmar se gasto el cupo, Fallo legible y sin el mensaje interno")
    void cambioSinCupoAlConfirmar() {
        CambioDeHorarioConfirmable confirmable = new CambioDeHorarioConfirmable(ajustar);
        when(ajustar.cambiarHorario(any(), any())).thenThrow(new IllegalStateException("Esta semana ya reacomodaste 3"));

        String motivo = motivo(confirmable.aplicar(APRENDIZ, new InvocacionHerramienta(
                PropuestaDeCambioDeHorario.NOMBRE, Map.of("habito_id", MEDITAR.toString(), "hora_inicio", "06:30",
                "fecha", VIERNES.toString()))));

        assertThat(motivo).contains("no le quedan cambios").doesNotContain("reacomodaste 3");
    }

    @Test
    @DisplayName("una invocacion guardada rota no llega a habits")
    void invocacionRota() {
        motivo(new CambioDeHorarioConfirmable(ajustar).aplicar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(PropuestaDeCambioDeHorario.NOMBRE)));

        verify(ajustar, never()).cambiarHorario(any(), any());
    }

    @Test
    @DisplayName("apagar un dia: delega; si habits dice obligatorio, Fallo legible")
    void apagarDia() {
        ApagarDiaConfirmable confirmable = new ApagarDiaConfirmable(ajustar, horarios);
        InvocacionHerramienta apagar = new InvocacionHerramienta(PropuestaDeApagarDia.NOMBRE,
                Map.of("habito_id", MEDITAR.toString(), "fecha", VIERNES.toString(), "accion", "apagar"));

        assertThat(confirmable.aplicar(APRENDIZ, apagar))
                .isEqualTo(ResultadoHerramienta.exito("Habito apagado el viernes 2026-09-11."));
        verify(ajustar).cambiarEstadoDelDia(APRENDIZ, MEDITAR, VIERNES, false);

        doThrow(new IllegalStateException("Este habito es obligatorio")).when(ajustar)
                .cambiarEstadoDelDia(APRENDIZ, MEDITAR, VIERNES, false);
        assertThat(motivo(confirmable.aplicar(APRENDIZ, apagar))).contains("obligatorio del programa");
    }

    @Test
    @DisplayName("encender un dia que sigue apagado por dia de semana: se dice, no se afirma que quedo activo")
    void encenderPeroSigueApagadoPorDiaDeSemana() {
        ApagarDiaConfirmable confirmable = new ApagarDiaConfirmable(ajustar, horarios);
        when(horarios.deFecha(APRENDIZ, VIERNES)).thenReturn(new HorariosDelDia(VIERNES, 14,
                List.of(new HorarioDeHabito(MEDITAR, "Meditar", LocalTime.of(6, 0), null, false, true, false, false,
                        null)), new CuotaCambios(0, 3, 3, false)));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, new InvocacionHerramienta(
                PropuestaDeApagarDia.NOMBRE, Map.of("habito_id", MEDITAR.toString(), "fecha", VIERNES.toString(),
                "accion", "encender")));

        verify(ajustar).cambiarEstadoDelDia(APRENDIZ, MEDITAR, VIERNES, true);
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("sigue apagado")
                .contains("todos los viernes");
    }

    @Test
    @DisplayName("dia de semana: fijar, apagar y quitar delegan; el rechazo se traduce segun la accion")
    void diaDeSemana() {
        HorarioPorDiaDeSemanaConfirmable confirmable = new HorarioPorDiaDeSemanaConfirmable(ajustar);
        InvocacionHerramienta fijar = new InvocacionHerramienta(PropuestaDeHorarioPorDiaDeSemana.NOMBRE,
                Map.of("habito_id", MEDITAR.toString(), "dia_semana", "MONDAY", "accion", "fijar",
                        "hora_inicio", "05:00", "hora_limite", "06:00"));

        assertThat(confirmable.aplicar(APRENDIZ, fijar))
                .isEqualTo(ResultadoHerramienta.exito("Horario fijado para los lunes: 05:00-06:00."));
        verify(ajustar).fijarDiaDeLaSemana(APRENDIZ, new HorarioSemanal(MEDITAR, DayOfWeek.MONDAY,
                LocalTime.of(5, 0), LocalTime.of(6, 0)));

        confirmable.aplicar(APRENDIZ, new InvocacionHerramienta(PropuestaDeHorarioPorDiaDeSemana.NOMBRE,
                Map.of("habito_id", MEDITAR.toString(), "dia_semana", "MONDAY", "accion", "quitar")));
        verify(ajustar).quitarDiaDeLaSemana(APRENDIZ, MEDITAR, DayOfWeek.MONDAY);

        doThrow(new IllegalStateException("x")).when(ajustar).fijarDiaDeLaSemana(any(), any());
        doThrow(new IllegalStateException("x")).when(ajustar).apagarDiaDeLaSemana(any(), any(), any());
        assertThat(motivo(confirmable.aplicar(APRENDIZ, fijar))).contains("no le quedan cambios");
        assertThat(motivo(confirmable.aplicar(APRENDIZ, new InvocacionHerramienta(
                PropuestaDeHorarioPorDiaDeSemana.NOMBRE, Map.of("habito_id", MEDITAR.toString(),
                "dia_semana", "MONDAY", "accion", "apagar"))))).contains("obligatorio");
    }

    @Test
    @DisplayName("cuenta suspendida o habito ajeno al confirmar: Fallo legible, no 500")
    void noAutorizado() {
        doThrow(new NotAuthorizedException("Cuenta suspendida")).when(ajustar)
                .quitarDiaDeLaSemana(any(), any(), any());

        String motivo = motivo(new HorarioPorDiaDeSemanaConfirmable(ajustar).aplicar(APRENDIZ,
                new InvocacionHerramienta(PropuestaDeHorarioPorDiaDeSemana.NOMBRE, Map.of("habito_id",
                        MEDITAR.toString(), "dia_semana", "MONDAY", "accion", "quitar"))));

        assertThat(motivo).contains("no esta activa");
    }
}
