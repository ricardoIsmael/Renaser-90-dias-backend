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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code proponer_horario_por_dia_de_semana}: fijar mira el cupo de la PROXIMA ocurrencia de ese
 * dia (la fecha efectiva con que {@code habits} lo cobra); apagar y quitar no gastan cupo.
 */
class PropuestaDeHorarioPorDiaDeSemanaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    /** Miercoles, dia 12 (en la zona del participante, segun habits). */
    private static final LocalDate HOY = LocalDate.of(2026, 9, 9);
    private static final LocalDate PROXIMO_LUNES = LocalDate.of(2026, 9, 14);
    private static final UUID MEDITAR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ConsultarHorariosPort horarios = mock(ConsultarHorariosPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final PropuestaDeHorarioPorDiaDeSemana herramienta = new PropuestaDeHorarioPorDiaDeSemana(horarios,
            proponer);

    private static HorariosDelDia dia(LocalDate fecha, int diaPrograma, CuotaCambios cuota, boolean obligatorio) {
        return new HorariosDelDia(fecha, diaPrograma, List.of(new HorarioDeHabito(MEDITAR, "Meditar",
                LocalTime.of(6, 0), LocalTime.of(7, 0), false, false, false, obligatorio, null)), cuota);
    }

    private static InvocacionHerramienta pedido(String... claveValor) {
        Map<String, String> argumentos = new HashMap<>(Map.of("habito_id", MEDITAR.toString()));
        for (int i = 0; i < claveValor.length; i += 2) {
            argumentos.put(claveValor[i], claveValor[i + 1]);
        }
        return new InvocacionHerramienta(PropuestaDeHorarioPorDiaDeSemana.NOMBRE, argumentos);
    }

    private void hoyYProximoLunes(CuotaCambios cuotaDelLunes, boolean obligatorio) {
        when(horarios.deFecha(APRENDIZ, null)).thenReturn(dia(HOY, 12, new CuotaCambios(0, 3, 3, false), false));
        when(horarios.deFecha(APRENDIZ, PROXIMO_LUNES)).thenReturn(dia(PROXIMO_LUNES, 17, cuotaDelLunes,
                obligatorio));
    }

    @Test
    @DisplayName("fijar: resumen exacto con la hora de antes, desde cuando rige y los cambios que quedarian")
    void fijar() {
        hoyYProximoLunes(new CuotaCambios(1, 2, 3, false), false);

        herramienta.ejecutar(APRENDIZ, pedido("dia_semana", "Lunes", "accion", "fijar", "hora_inicio", "05:00"));

        verify(proponer).proponer(APRENDIZ, new InvocacionHerramienta(PropuestaDeHorarioPorDiaDeSemana.NOMBRE,
                        Map.of("habito_id", MEDITAR.toString(), "dia_semana", "MONDAY", "accion", "fijar",
                                "hora_inicio", "05:00")),
                "Fijar 'Meditar' los lunes a 05:00 (sin hora limite propia) (hasta ahora ese dia: 06:00-07:00), desde "
                        + "el lunes 2026-09-14. Los demas dias no cambian. Usa 1 de sus 3 cambios de esa semana: le "
                        + "quedarian 1.");
    }

    @Test
    @DisplayName("el mismo dia de semana que hoy mira la semana siguiente: hoy no se reacomoda")
    void mismoDiaQueHoy() {
        LocalDate proximoMiercoles = HOY.plusDays(7);
        when(horarios.deFecha(APRENDIZ, null)).thenReturn(dia(HOY, 12, new CuotaCambios(0, 3, 3, false), false));
        when(horarios.deFecha(APRENDIZ, proximoMiercoles)).thenReturn(dia(proximoMiercoles, 19,
                new CuotaCambios(0, 3, 3, false), false));

        herramienta.ejecutar(APRENDIZ, pedido("dia_semana", "miércoles", "accion", "fijar", "hora_inicio", "05:00"));

        verify(horarios).deFecha(APRENDIZ, proximoMiercoles);
        verify(proponer).proponer(eq(APRENDIZ), any(), any());
    }

    @Test
    @DisplayName("fijar sin cupo no se propone; quitar con el mismo cupo agotado si, porque no gasta")
    void cupoAgotado() {
        hoyYProximoLunes(new CuotaCambios(3, 0, 3, false), false);

        ResultadoHerramienta fijar = herramienta.ejecutar(APRENDIZ, pedido("dia_semana", "lunes", "accion", "fijar",
                "hora_inicio", "05:00"));
        herramienta.ejecutar(APRENDIZ, pedido("dia_semana", "lunes", "accion", "quitar"));

        assertThat(fijar).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(proponer).proponer(eq(APRENDIZ), any(), eq("Quitar lo propio de los lunes en 'Meditar' (hora propia "
                + "o apagado): esos dias vuelve a su horario general. No gasta cambios de horario."));
    }

    @Test
    @DisplayName("apagar un obligatorio no se propone, aunque el resto de la semana se pueda")
    void apagar() {
        hoyYProximoLunes(new CuotaCambios(3, 0, 3, false), true);

        ResultadoHerramienta obligatorio = herramienta.ejecutar(APRENDIZ, pedido("dia_semana", "lunes",
                "accion", "apagar"));

        assertThat(obligatorio).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(proponer, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("fijar sin hora, dia inventado o accion desconocida no se proponen")
    void argumentosQueNoSirven() {
        hoyYProximoLunes(new CuotaCambios(0, 3, 3, false), false);

        List.of(pedido("dia_semana", "lunes", "accion", "fijar"),
                        pedido("dia_semana", "feriado", "accion", "apagar"),
                        pedido("dia_semana", "lunes", "accion", "borrar"))
                .forEach(invocacion -> assertThat(herramienta.ejecutar(APRENDIZ, invocacion))
                        .isInstanceOf(ResultadoHerramienta.Fallo.class));
        verify(proponer, never()).proponer(any(), any(), any());
    }
}
