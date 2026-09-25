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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code proponer_cambio_de_horario}: propone con el resumen exacto, nunca escribe, y no ofrece un
 * boton que {@code habits} va a rechazar (cupo agotado, dia no futuro, habito ajeno). "Hoy" lo da
 * {@code habits} en la zona del participante: aca el puerto devuelve el 09/09 de Lima.
 */
class PropuestaDeCambioDeHorarioTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    /** Miercoles en Lima; el reloj real puede estar ya en el 10/09 UTC: la herramienta no lo mira. */
    private static final LocalDate HOY = LocalDate.of(2026, 9, 9);
    private static final LocalDate MANANA = LocalDate.of(2026, 9, 10);
    private static final UUID MEDITAR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ConsultarHorariosPort horarios = mock(ConsultarHorariosPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final PropuestaDeCambioDeHorario herramienta = new PropuestaDeCambioDeHorario(horarios, proponer);

    private static HorariosDelDia dia(LocalDate fecha, int diaPrograma, CuotaCambios cuota) {
        return new HorariosDelDia(fecha, diaPrograma, List.of(new HorarioDeHabito(MEDITAR, "Meditar",
                LocalTime.of(6, 0), LocalTime.of(7, 0), false, false, false, false, null)), cuota);
    }

    private static InvocacionHerramienta pedido(String... claveValor) {
        Map<String, String> argumentos = new HashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            argumentos.put(claveValor[i], claveValor[i + 1]);
        }
        return new InvocacionHerramienta(PropuestaDeCambioDeHorario.NOMBRE, argumentos);
    }

    private void hoyEsDia12() {
        when(horarios.deFecha(APRENDIZ, null)).thenReturn(dia(HOY, 12, new CuotaCambios(2, 1, 3, false)));
    }

    @Test
    @DisplayName("cambio general: propone desde manana en su zona, con el cambio exacto y los cambios que quedarian")
    void cambioGeneral() {
        hoyEsDia12();
        when(horarios.deFecha(APRENDIZ, MANANA)).thenReturn(dia(MANANA, 13, new CuotaCambios(2, 1, 3, false)));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, pedido("habito_id", MEDITAR.toString(),
                "hora_inicio", "6:30", "hora_limite", "07:30"));

        String resumen = "Cambiar 'Meditar' de 06:00-07:00 a 06:30-07:30 como horario general, desde el jueves "
                + "2026-09-10 (hoy sigue igual). Usa 1 de sus 3 cambios de esa semana: le quedarian 0.";
        verify(proponer).proponer(APRENDIZ, new InvocacionHerramienta(PropuestaDeCambioDeHorario.NOMBRE,
                Map.of("habito_id", MEDITAR.toString(), "hora_inicio", "06:30", "hora_limite", "07:30")), resumen);
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains(resumen).contains("TODAVIA NO")
                .contains("Confirmar");
    }

    @Test
    @DisplayName("cambio de un solo dia en la semana libre: dice que es solo ese dia y que no gasta cupo")
    void cambioDeUnDia() {
        LocalDate sabado = LocalDate.of(2026, 9, 12);
        hoyEsDia12();
        when(horarios.deFecha(APRENDIZ, sabado)).thenReturn(dia(sabado, 15, new CuotaCambios(0, 3, 3, true)));

        herramienta.ejecutar(APRENDIZ, pedido("habito_id", MEDITAR.toString(), "hora_inicio", "06:30",
                "fecha", "2026-09-12"));

        verify(proponer).proponer(APRENDIZ, new InvocacionHerramienta(PropuestaDeCambioDeHorario.NOMBRE,
                        Map.of("habito_id", MEDITAR.toString(), "hora_inicio", "06:30", "fecha", "2026-09-12")),
                "Cambiar 'Meditar' solo el sábado 2026-09-12, de 06:00-07:00 a 06:30 (sin hora limite propia) (los "
                        + "demas dias no cambian). No gasta cambios: es su semana de acomodo libre.");
    }

    @Test
    @DisplayName("cupo agotado: no se propone nada y el modelo recibe un motivo para explicar")
    void cupoAgotado() {
        hoyEsDia12();
        when(horarios.deFecha(APRENDIZ, MANANA)).thenReturn(dia(MANANA, 13, new CuotaCambios(3, 0, 3, false)));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, pedido("habito_id", MEDITAR.toString(),
                "hora_inicio", "06:30"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("ya uso sus 3 cambios");
        verify(proponer, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("hoy (en su zona) no se reacomoda: no se propone")
    void hoyNoSeReacomoda() {
        hoyEsDia12();

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, pedido("habito_id", MEDITAR.toString(),
                "hora_inicio", "06:30", "fecha", HOY.toString()));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(proponer, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("un habito que no esta entre los suyos, una hora sin formato o un limite antes del inicio no se proponen")
    void argumentosQueNoSirven() {
        hoyEsDia12();
        when(horarios.deFecha(APRENDIZ, MANANA)).thenReturn(dia(MANANA, 13, new CuotaCambios(0, 3, 3, false)));

        List<InvocacionHerramienta> invalidos = List.of(
                pedido("habito_id", UUID.randomUUID().toString(), "hora_inicio", "06:30"),
                pedido("habito_id", MEDITAR.toString(), "hora_inicio", "7am"),
                pedido("habito_id", MEDITAR.toString(), "hora_inicio", "08:00", "hora_limite", "07:00"),
                pedido("habito_id", "Meditar", "hora_inicio", "06:30"));

        invalidos.forEach(invocacion -> assertThat(herramienta.ejecutar(APRENDIZ, invocacion))
                .isInstanceOf(ResultadoHerramienta.Fallo.class));
        verify(proponer, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("si la propuesta no se puede guardar, Fallo legible: nunca 'hecho'")
    void propuestaQueNoSeGuarda() {
        hoyEsDia12();
        when(horarios.deFecha(APRENDIZ, MANANA)).thenReturn(dia(MANANA, 13, new CuotaCambios(0, 3, 3, false)));
        when(proponer.proponer(any(), any(), any())).thenThrow(new IllegalStateException("base caida"));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, pedido("habito_id", MEDITAR.toString(),
                "hora_inicio", "06:30"));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo("No pude preparar la confirmacion en este momento."));
    }

    @Test
    @DisplayName("la descripcion manda a consultar_horarios antes y prohibe decir que ya esta hecho")
    void descripcion() {
        assertThat(herramienta.definicion().descripcion()).contains("consultar_horarios")
                .contains("Nunca digas que el cambio ya esta hecho");
        assertThat(herramienta.definicion().obligatoriosFaltantesEn(pedido()))
                .containsExactly("habito_id", "hora_inicio");
    }

    @Test
    @DisplayName("bateria 2026-09-25: un cambio a la misma franja no se propone ni gasta cupo, y lo dice")
    void mismaFranjaNoSePropone() {
        hoyEsDia12();
        when(horarios.deFecha(APRENDIZ, MANANA)).thenReturn(dia(MANANA, 13, new CuotaCambios(2, 1, 3, false)));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, pedido("habito_id", MEDITAR.toString(),
                "hora_inicio", "06:00", "hora_limite", "07:00"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("ya esta a las 06:00-07:00")
                .contains("no se gasta un cambio");
        verify(proponer, never()).proponer(any(), any(), any());
    }
}
