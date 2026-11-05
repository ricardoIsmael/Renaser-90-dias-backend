package com.renaser.os.calendar.domain.model.recordatorio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los cuatro avisos de MENSAJE de una ocurrencia (spec 041, FR-014): 1 dia, 1 hora, 30 y 10 minutos antes, y solo los que todavia llegan a tiempo.
 */
class AvisosDeMensajeDeLaOcurrenciaTest {

    private static final List<Integer> CUATRO = List.of(1440, 60, 30, 10);
    /** 19:00 en Lima = 00:00 UTC del dia siguiente: el instante UTC cae en otro dia calendario que el local (regla 02). */
    private static final Instant INICIO = Instant.parse("2026-11-07T00:00:00Z");

    // spec 041 FR-014, SC-008
    @Test
    @DisplayName("un evento creado con tiempo genera los cuatro avisos, en orden: 1 dia, 1 hora, 30 y 10 minutos antes")
    void generaLosCuatroAvisos() {
        Instant ahora = INICIO.minus(java.time.Duration.ofDays(3));

        var instantes = CalculadoraRecordatorios.instantesDeMensaje(INICIO, CUATRO, ahora);

        assertThat(instantes).extracting(InstanteDeMensaje::minutosAntes).containsExactly(1440, 60, 30, 10);
        assertThat(instantes).extracting(InstanteDeMensaje::enviarEn).containsExactly(INICIO.minusSeconds(86_400),
                INICIO.minusSeconds(3_600), INICIO.minusSeconds(1_800), INICIO.minusSeconds(600));
        assertThat(instantes).allSatisfy(i -> assertThat(i.inicioOcurrencia()).isEqualTo(INICIO));
    }

    // spec 041 FR-014, SC-008
    @Test
    @DisplayName("«1 dia antes» es exactamente 24 horas antes de la hora de inicio, no «a las 9 de la manana de la vispera»")
    void unDiaAntesSonVeinticuatroHoras() {
        var instantes = CalculadoraRecordatorios.instantesDeMensaje(INICIO, CUATRO, INICIO.minus(java.time.Duration.ofDays(3)));

        assertThat(instantes.get(0).enviarEn()).isEqualTo(INICIO.minus(java.time.Duration.ofHours(24)));
    }

    // spec 041 FR-014, SC-008
    @Test
    @DisplayName("un evento creado 20 minutos antes de empezar solo genera el de 10 minutos: el momento de los otros ya habia pasado")
    void unEventoCreadoVeinteMinutosAntesSoloTieneElDeDiez() {
        Instant ahora = INICIO.minus(java.time.Duration.ofMinutes(20));

        var instantes = CalculadoraRecordatorios.instantesDeMensaje(INICIO, CUATRO, ahora);

        assertThat(instantes).extracting(InstanteDeMensaje::minutosAntes).containsExactly(10);
    }

    // spec 041 FR-014
    @Test
    @DisplayName("un aviso cuyo momento es exactamente ahora ya paso: no se genera")
    void elMomentoExactoDeAhoraNoSeGenera() {
        Instant ahora = INICIO.minus(java.time.Duration.ofMinutes(30));

        assertThat(CalculadoraRecordatorios.instantesDeMensaje(INICIO, CUATRO, ahora)).extracting(InstanteDeMensaje::minutosAntes)
                .containsExactly(10);
    }

    // spec 041 FR-014
    @Test
    @DisplayName("la lista es configuracion: una vacia apaga los avisos de mensaje; una repetida o no positiva se ignora")
    void laListaEsConfiguracion() {
        Instant ahora = INICIO.minus(java.time.Duration.ofDays(3));

        assertThat(CalculadoraRecordatorios.instantesDeMensaje(INICIO, List.of(), ahora)).isEmpty();
        assertThat(CalculadoraRecordatorios.instantesDeMensaje(INICIO, java.util.Arrays.asList(30, 30, 0, -5, null), ahora))
                .extracting(InstanteDeMensaje::minutosAntes).containsExactly(30);
    }

    // spec 041 FR-014
    @Test
    @DisplayName("la ventana de generacion alcanza para el aviso mas lejano, con el mismo margen de un dia que las reglas")
    void laVentanaAlcanza() {
        assertThat(CalculadoraRecordatorios.diasDeVentanaDeMensaje(CUATRO, 3)).isEqualTo(3);
        assertThat(CalculadoraRecordatorios.diasDeVentanaDeMensaje(List.of(1440 * 5), 3)).isEqualTo(6);
        assertThat(CalculadoraRecordatorios.diasDeVentanaDeMensaje(List.of(), 3)).isEqualTo(3);
    }
}
