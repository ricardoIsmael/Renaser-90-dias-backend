package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.CuotaCambios;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorarioDeHabito;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code proponer_apagar_dia}: hoy SI se puede apagar (a diferencia de cambiar la hora), el pasado
 * no, y un obligatorio nunca. "Hoy" es el que devuelve {@code habits} para el participante.
 */
class PropuestaDeApagarDiaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 9, 9);
    private static final UUID MEDITAR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final CuotaCambios CUOTA = new CuotaCambios(3, 0, 3, false);

    private final ConsultarHorariosPort horarios = mock(ConsultarHorariosPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final PropuestaDeApagarDia herramienta = new PropuestaDeApagarDia(horarios, proponer);

    private static HorariosDelDia dia(LocalDate fecha, boolean apagado, boolean obligatorio) {
        return new HorariosDelDia(fecha, 12, List.of(new HorarioDeHabito(MEDITAR, "Meditar", LocalTime.of(6, 0),
                null, false, apagado, false, obligatorio, null)), CUOTA);
    }

    private static InvocacionHerramienta pedido(LocalDate fecha, String accion) {
        return new InvocacionHerramienta(PropuestaDeApagarDia.NOMBRE, accion == null
                ? Map.of("habito_id", MEDITAR.toString(), "fecha", fecha.toString())
                : Map.of("habito_id", MEDITAR.toString(), "fecha", fecha.toString(), "accion", accion));
    }

    @Test
    @DisplayName("apagar hoy: propone con el resumen exacto, sin mirar el cupo (apagar no lo gasta)")
    void apagarHoy() {
        when(horarios.deFecha(APRENDIZ, null)).thenReturn(dia(HOY, false, false));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, pedido(HOY, null));

        verify(proponer).proponer(APRENDIZ, new InvocacionHerramienta(PropuestaDeApagarDia.NOMBRE,
                        Map.of("habito_id", MEDITAR.toString(), "fecha", "2026-09-09", "accion", "apagar")),
                "Apagar 'Meditar' solo hoy, miércoles 2026-09-09: ese dia no se le va a pedir. No gasta cambios de "
                        + "horario.");
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("TODAVIA NO");
    }

    @Test
    @DisplayName("un obligatorio no se propone apagar")
    void obligatorio() {
        LocalDate viernes = HOY.plusDays(2);
        when(horarios.deFecha(APRENDIZ, null)).thenReturn(dia(HOY, false, false));
        when(horarios.deFecha(APRENDIZ, viernes)).thenReturn(dia(viernes, false, true));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, pedido(viernes, "apagar"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        // E-245: el motivo y lo que si se puede, nunca un "no se puede" a secas.
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("obligatorio del programa")
                .contains("cambiarle la hora");
        verify(proponer, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("un dia que ya paso en su zona no se propone, y ni siquiera se consulta")
    void diaPasado() {
        when(horarios.deFecha(APRENDIZ, null)).thenReturn(dia(HOY, false, false));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, pedido(HOY.minusDays(1), "apagar"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(horarios, never()).deFecha(eq(APRENDIZ), eq(HOY.minusDays(1)));
        verify(proponer, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("encender: solo si ese dia esta apagado")
    void encender() {
        LocalDate viernes = HOY.plusDays(2);
        when(horarios.deFecha(APRENDIZ, null)).thenReturn(dia(HOY, false, false));
        when(horarios.deFecha(APRENDIZ, viernes)).thenReturn(dia(viernes, true, false));

        herramienta.ejecutar(APRENDIZ, pedido(viernes, "encender"));
        ResultadoHerramienta yaEncendido = herramienta.ejecutar(APRENDIZ, pedido(HOY, "encender"));

        verify(proponer).proponer(eq(APRENDIZ), any(),
                eq("Volver a activar 'Meditar' el viernes 2026-09-11, con su horario de ese dia."));
        assertThat(yaEncendido).isInstanceOf(ResultadoHerramienta.Fallo.class);
    }

    @Test
    @DisplayName("una accion desconocida no se adivina")
    void accionDesconocida() {
        assertThat(herramienta.ejecutar(APRENDIZ, pedido(HOY, "borrar")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(proponer, never()).proponer(any(), any(), any());
    }
}
