package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.CuotaCambios;
import com.renaser.os.rag.application.ports.out.horarios.ConsultarHorariosPort.HorariosDelDia;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort.Panorama;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El acompañante lee los días de SU programa (spec 022): en el Diplomado de un año el día 200 es válido y se dice «de 365», no «de 90»; el
 * día 366 sigue siendo «fuera del programa». Con 90 días todo queda como estaba (lo prueban las pruebas de siempre de cada herramienta).
 */
class HerramientasConLaDuracionDelProgramaTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final LocalDate FECHA = LocalDate.of(2027, 3, 1);
    private static final CuotaCambios CUOTA = new CuotaCambios(0, 3, 3, true);

    private static HorariosDelDia dia(int diaPrograma, int duracion) {
        return new HorariosDelDia(FECHA, diaPrograma, List.of(), CUOTA, duracion);
    }

    private static InvocacionHerramienta conFecha(LocalDate fecha) {
        return new InvocacionHerramienta(ConsultarHorariosHerramienta.NOMBRE, java.util.Map.of(ConsultarHorariosHerramienta.ARGUMENTO_FECHA, fecha.toString()));
    }

    @Test
    @DisplayName("el resumen dice «día N de 365» y la fase de su programa, no «de 90»")
    void elResumenDiceLosDiasDeSuPrograma() {
        var panorama = new Panorama(ZoneId.of("America/Lima"), Optional.empty(), Optional.empty(), Optional.empty());
        ConsultarResumenDelProgramaHerramienta resumen = new ConsultarResumenDelProgramaHerramienta(
                id -> Optional.of(new SituacionDelAprendiz(200, 3).conDuracion(365)), (id, ahora) -> Optional.of(panorama),
                FixedClock.at(Instant.parse("2027-03-01T15:00:00Z")));

        String texto = ((ResultadoHerramienta.Exito) resumen.ejecutar(PERSONA,
                InvocacionHerramienta.sinArgumentos(ConsultarResumenDelProgramaHerramienta.NOMBRE))).contenido();

        assertThat(texto).contains("Dia del programa: 200 de 365 (fase 3 de 4).").doesNotContain("de 90");
    }

    @Test
    @DisplayName("el día 200 de un programa de 365 es válido para consultar horarios; el 366 queda fuera y dice «365 dias»")
    void consultarHorariosAcotaConLaDuracion() {
        ConsultarHorariosPort puerto = mock(ConsultarHorariosPort.class);
        ConsultarHorariosHerramienta herramienta = new ConsultarHorariosHerramienta(puerto);
        when(puerto.deFecha(PERSONA, FECHA)).thenReturn(dia(200, 365));
        assertThat(herramienta.ejecutar(PERSONA, conFecha(FECHA))).isInstanceOf(ResultadoHerramienta.Exito.class);

        LocalDate despues = FECHA.plusDays(166);
        when(puerto.deFecha(PERSONA, despues)).thenReturn(new HorariosDelDia(despues, 366, List.of(), CUOTA, 365));
        when(puerto.deFecha(PERSONA, null)).thenReturn(dia(200, 365));
        ResultadoHerramienta fuera = herramienta.ejecutar(PERSONA, conFecha(despues));
        assertThat(fuera).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) fuera).motivo()).contains("fuera de sus 365 dias");
    }

    @Test
    @DisplayName("para proponer un cambio de horario: el día 200 vale en un año y el 91 sigue fuera en la Escuela")
    void proponerAcotaConLaDuracion() {
        ConsultarHorariosPort puerto = mock(ConsultarHorariosPort.class);
        when(puerto.deFecha(PERSONA, FECHA)).thenReturn(dia(200, 365));
        assertThat(HorariosParaProponer.de(puerto, PERSONA, FECHA).diaPrograma()).isEqualTo(200);

        LocalDate escuela = LocalDate.of(2026, 12, 20);
        when(puerto.deFecha(PERSONA, escuela)).thenReturn(new HorariosDelDia(escuela, 91, List.of(), CUOTA));
        when(puerto.deFecha(PERSONA, null)).thenReturn(new HorariosDelDia(LocalDate.of(2026, 12, 1), 70, List.of(), CUOTA));
        assertThatThrownBy(() -> HorariosParaProponer.de(puerto, PERSONA, escuela))
                .isInstanceOf(PropuestaImposibleException.class).hasMessageContaining("fuera de sus 90 dias").hasMessageContaining("de 90");
    }
}
