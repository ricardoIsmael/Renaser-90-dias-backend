package com.renaser.os.leadership.domain.model.cumplimiento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La ventana del líder es la del resumen «Semáforo por grupos», en las tres pantallas (decisión del dueño del
 * 2026-10-19): sin fecha, los 7 días que terminan ayer; con un viernes, la semana de sábado a viernes que termina ese
 * día; nunca una que llegue al futuro. El «hoy» es el de la persona (regla 02).
 */
class VentanaDeLaFichaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate LUNES_19 = LocalDate.of(2026, 10, 19);

    private static LocalDate hoyEn(String instante) {
        return Instant.parse(instante).atZone(LIMA).toLocalDate();
    }

    @Test
    @DisplayName("sin fecha es la ventana vigente del resumen: los 7 dias que terminan ayer, aunque ayer no sea viernes")
    void sinFechaEsLaVentanaVigente() {
        VentanaDeLaFicha ventana = VentanaDeLaFicha.de(null, LUNES_19);

        assertThat(ventana.vigente()).isTrue();
        assertThat(ventana.desde()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(ventana.hasta()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(ventana.hoyLocal()).isEqualTo(LUNES_19);
    }

    @Test
    @DisplayName("la vigente es la misma cuenta del resumen en cualquier dia: hasta = hoy - 1 y desde = hasta - 6")
    void laVigenteSigueAlDiaDeHoy() {
        for (int dia = 17; dia <= 23; dia++) {
            LocalDate hoy = LocalDate.of(2026, 10, dia);

            VentanaDeLaFicha ventana = VentanaDeLaFicha.de(null, hoy);

            assertThat(ventana.hasta()).isEqualTo(hoy.minusDays(1));
            assertThat(ventana.desde()).isEqualTo(hoy.minusDays(7));
        }
    }

    @Test
    @DisplayName("con un viernes es esa semana, de sabado a viernes, y deja de ser la vigente")
    void conUnViernesEsEsaSemana() {
        VentanaDeLaFicha ventana = VentanaDeLaFicha.de(LocalDate.of(2026, 10, 16), LUNES_19);

        assertThat(ventana.vigente()).isFalse();
        assertThat(ventana.desde()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(ventana.hasta()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(ventana.cerrada()).isTrue();
    }

    @Test
    @DisplayName("cerrada sale del calendario: la vigente de un lunes no esta cerrada; el sabado, la que acaba de cerrar si")
    void cerradaSegunElCalendario() {
        // Lunes 19: la vigente (12 al 18) llega a un domingo, de una semana que sigue abierta.
        assertThat(VentanaDeLaFicha.de(null, LUNES_19).cerrada()).isFalse();
        // Sabado 24: la vigente termina el viernes 23, que cerro hace unas horas.
        assertThat(VentanaDeLaFicha.de(null, LocalDate.of(2026, 10, 24)).cerrada()).isTrue();
        // Viernes 23: la vigente termina el jueves 22 y la semana de hoy todavia corre.
        assertThat(VentanaDeLaFicha.de(null, LocalDate.of(2026, 10, 23)).cerrada()).isFalse();
        // Una semana pasada pedida con su viernes esta cerrada.
        assertThat(VentanaDeLaFicha.de(LocalDate.of(2026, 10, 9), LUNES_19).cerrada()).isTrue();
    }

    @Test
    @DisplayName("regla 02: el «hoy» de la persona manda; con el reloj en la madrugada UTC la vigente es la de Lima")
    void madrugadaUtc() {
        // 03:00 UTC del domingo 18 = 22:00 del sabado 17 en Lima: para ella la vigente termina el viernes 16.
        LocalDate hoy = hoyEn("2026-10-18T03:00:00Z");

        VentanaDeLaFicha ventana = VentanaDeLaFicha.de(null, hoy);

        assertThat(hoy).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(ventana.desde()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(ventana.hasta()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(ventana.cerrada()).isTrue();
    }

    @Test
    @DisplayName("ninguna ventana llega al futuro: un viernes que todavia no llego es 400")
    void sinFuturo() {
        // El viernes 23 todavia no llego el lunes 19 (la semana en curso ya no se ofrece).
        assertThatThrownBy(() -> VentanaDeLaFicha.de(LocalDate.of(2026, 10, 23), LUNES_19))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("futuras");
        assertThatThrownBy(() -> VentanaDeLaFicha.de(LocalDate.of(2026, 10, 30), LUNES_19))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("futuras");
    }

    @Test
    @DisplayName("el viernes de hoy se puede pedir: llega a hoy y no mas alla; no esta cerrada")
    void elViernesDeHoy() {
        LocalDate viernes = LocalDate.of(2026, 10, 23);

        VentanaDeLaFicha ventana = VentanaDeLaFicha.de(viernes, viernes);

        assertThat(ventana.hasta()).isEqualTo(viernes);
        assertThat(ventana.cerrada()).isFalse();
        assertThat(ventana.esHoy(viernes)).isTrue();
    }

    @Test
    @DisplayName("regla 02: en la madrugada UTC un viernes que ya es hoy en UTC aun es futuro en Lima")
    void viernesFuturoEnLima() {
        // 03:00 UTC del viernes 23 = 22:00 del jueves 22 en Lima: en UTC el viernes 23 ya es hoy, en Lima todavia no.
        LocalDate hoy = hoyEn("2026-10-23T03:00:00Z");

        assertThat(hoy).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThatThrownBy(() -> VentanaDeLaFicha.de(LocalDate.of(2026, 10, 23), hoy))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("futuras");
        assertThat(VentanaDeLaFicha.de(LocalDate.of(2026, 10, 16), hoy).hasta()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    @DisplayName("un dia que no es viernes se rechaza, como en el resumen y en las otras vistas del semaforo")
    void soloViernes() {
        assertThatThrownBy(() -> VentanaDeLaFicha.de(LocalDate.of(2026, 10, 10), LUNES_19))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VentanaDeLaFicha.de(LocalDate.of(2026, 10, 18), LUNES_19))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hoy se distingue de los dias que ya pasaron; la vigente nunca incluye hoy")
    void hoy() {
        VentanaDeLaFicha vigente = VentanaDeLaFicha.de(null, LUNES_19);

        assertThat(vigente.esHoy(LUNES_19)).isTrue();
        assertThat(vigente.esHoy(LocalDate.of(2026, 10, 18))).isFalse();
        assertThat(vigente.hasta()).isBefore(LUNES_19);
    }

    @Test
    @DisplayName("el sabado de la semana de un dia: lo que se usa para saber cual fue la primera semana")
    void sabadoQueAbreLaSemanaDe() {
        // El Dia 1 un miercoles: su primera semana abre el sabado anterior.
        assertThat(VentanaDeLaFicha.sabadoQueAbreLaSemanaDe(LocalDate.of(2026, 10, 7)))
                .isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(VentanaDeLaFicha.sabadoQueAbreLaSemanaDe(LocalDate.of(2026, 10, 3)))
                .isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(VentanaDeLaFicha.sabadoQueAbreLaSemanaDe(LocalDate.of(2026, 10, 9)))
                .isEqualTo(LocalDate.of(2026, 10, 3));
    }
}
