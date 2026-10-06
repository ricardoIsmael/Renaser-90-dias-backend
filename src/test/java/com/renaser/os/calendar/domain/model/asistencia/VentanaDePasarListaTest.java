package com.renaser.os.calendar.domain.model.asistencia;

import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La ventana de pasar lista (D-256, supuesto S-1). El evento de prueba es a las 20:00 de Lima del lunes 5,
 * que en UTC ya es el martes 6 a la 01:00: el reloj de cada caso cae en la madrugada UTC, cuando en Lima
 * todavía es el día anterior (regla 02 §3), justo la hora que esconde un cálculo hecho con la fecha UTC.
 */
class VentanaDePasarListaTest {

    /** Lunes 5 de octubre de 2026, 20:00 en Lima. */
    private static final Instant INICIO = Instant.parse("2026-10-06T01:00:00Z");
    private static final Ocurrencia DE_UNA_HORA = new Ocurrencia(INICIO, INICIO, 60, null);

    @Test
    @DisplayName("abre 30 min antes del inicio: 19:30 de Lima (00:30 UTC del día siguiente)")
    void abreTreintaMinutosAntes() {
        VentanaDePasarLista ventana = VentanaDePasarLista.de(DE_UNA_HORA);

        assertThat(ventana.abre()).isEqualTo(Instant.parse("2026-10-06T00:30:00Z"));
        assertThat(ventana.estaAbierta(Instant.parse("2026-10-06T00:29:59Z"))).as("19:29:59 en Lima").isFalse();
        assertThat(ventana.estaAbierta(Instant.parse("2026-10-06T00:30:00Z"))).as("19:30 en Lima").isTrue();
        assertThat(ventana.estaAbierta(Instant.parse("2026-10-06T00:45:00Z"))).as("19:45 en Lima").isTrue();
    }

    @Test
    @DisplayName("cierra 12 h después del fin: el evento termina a las 21:00 de Lima, cierra a las 09:00 del martes")
    void cierraDoceHorasDespuesDelFin() {
        VentanaDePasarLista ventana = VentanaDePasarLista.de(DE_UNA_HORA);

        assertThat(ventana.cierra()).isEqualTo(Instant.parse("2026-10-06T14:00:00Z"));
        assertThat(ventana.estaAbierta(Instant.parse("2026-10-06T14:00:00Z"))).isTrue();
        assertThat(ventana.estaAbierta(Instant.parse("2026-10-06T14:00:01Z"))).isFalse();
    }

    @Test
    @DisplayName("una ocurrencia reprogramada cuenta desde su inicio efectivo, no desde el slot de la serie")
    void cuentaDesdeElInicioEfectivo() {
        Instant movida = INICIO.plusSeconds(3 * 3600);
        VentanaDePasarLista ventana = VentanaDePasarLista.de(new Ocurrencia(INICIO, movida, 60, null));

        assertThat(ventana.abre()).isEqualTo(movida.minusSeconds(30 * 60));
        assertThat(ventana.estaAbierta(Instant.parse("2026-10-06T00:45:00Z"))).isFalse();
    }

    @Test
    @DisplayName("sin duración no se inventa una: el fin es el inicio")
    void sinDuracionElFinEsElInicio() {
        VentanaDePasarLista ventana = VentanaDePasarLista.de(new Ocurrencia(INICIO, INICIO, null, null));

        assertThat(ventana.cierra()).isEqualTo(INICIO.plusSeconds(12 * 3600));
    }

    @Test
    @DisplayName("yaAbrio sigue siendo verdadero cuando la ventana se venció (cerrar la lista se puede después)")
    void yaAbrioDespuesDelPlazo() {
        VentanaDePasarLista ventana = VentanaDePasarLista.de(DE_UNA_HORA);

        assertThat(ventana.yaAbrio(Instant.parse("2026-10-07T03:00:00Z"))).isTrue();
        assertThat(ventana.yaAbrio(Instant.parse("2026-10-06T00:00:00Z"))).isFalse();
    }
}
