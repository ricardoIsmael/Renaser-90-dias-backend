package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Research R3: a qué grupo del catálogo pertenece el mensaje de hoy. */
class GrupoDelDiaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 19);

    private static DiaDelSemaforo dia(int diasAtras, EstadoDiaSemaforo estado) {
        return estado == EstadoDiaSemaforo.MEDIDO
                ? new DiaDelSemaforo(HOY.minusDays(diasAtras), estado, 80, ColorSemaforo.VERDE, 1, 1, 0, 0)
                : DiaDelSemaforo.sinPorcentaje(HOY.minusDays(diasAtras), estado);
    }

    private static List<DiaDelSemaforo> ventanaDe(EstadoDiaSemaforo... estados) {
        List<DiaDelSemaforo> dias = new ArrayList<>();
        for (int i = 0; i < estados.length; i++) {
            dias.add(dia(estados.length - i, estados[i]));
        }
        return dias;
    }

    private static List<DiaDelSemaforo> ventanaMedida() {
        EstadoDiaSemaforo[] siete = new EstadoDiaSemaforo[7];
        java.util.Arrays.fill(siete, EstadoDiaSemaforo.MEDIDO);
        return ventanaDe(siete);
    }

    private static SituacionDelSemaforo situacion(ColorSemaforo color, boolean pausado, boolean terminado, int dia,
                                                  List<DiaDelSemaforo> ventana) {
        return new SituacionDelSemaforo(color, pausado, terminado, dia, ventana);
    }

    @ParameterizedTest(name = "color {0} es el grupo {1}")
    @CsvSource({"VERDE,VERDE", "AMARILLO,AMARILLO", "ROJO,ROJO"})
    @DisplayName("los tres colores van a su grupo")
    void losColoresVanASuGrupo(ColorSemaforo color, GrupoDelMensaje esperado) {
        assertThat(GrupoDelDia.de(situacion(color, false, false, 30, ventanaMedida()))).isEqualTo(esperado);
    }

    @Test
    @DisplayName("sin datos antes del Día 1 y en la primera semana es INICIO")
    void inicio() {
        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.SIN_DATOS, false, false, 0, List.of())))
                .isEqualTo(GrupoDelMensaje.INICIO);
        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.SIN_DATOS, false, false, 7, List.of())))
                .isEqualTo(GrupoDelMensaje.INICIO);
    }

    @Test
    @DisplayName("sin datos pasada la primera semana es SIN_PROGRAMA")
    void sinPrograma() {
        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.SIN_DATOS, false, false, 8, List.of())))
                .isEqualTo(GrupoDelMensaje.SIN_PROGRAMA);
    }

    @Test
    @DisplayName("con una pausa que pidió la persona es PAUSA, aunque la ventana todavía tenga color")
    void pausa() {
        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.VERDE, true, false, 30, ventanaMedida())))
                .isEqualTo(GrupoDelMensaje.PAUSA);
        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.SIN_DATOS, true, false, 3, List.of())))
                .isEqualTo(GrupoDelMensaje.PAUSA);
    }

    @Test
    @DisplayName("después del día 90 es GRADUADO, con color o sin él")
    void graduado() {
        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.VERDE, false, true, 90, ventanaMedida())))
                .isEqualTo(GrupoDelMensaje.GRADUADO);
        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.SIN_DATOS, false, true, 90, List.of())))
                .isEqualTo(GrupoDelMensaje.GRADUADO);
    }

    @Test
    @DisplayName("el día 90 todavía no es graduado: el programa termina después")
    void elDiaNoventaSigueEnElPrograma() {
        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.AMARILLO, false, false, 90, ventanaMedida())))
                .isEqualTo(GrupoDelMensaje.AMARILLO);
    }

    @Test
    @DisplayName("la ventana con días de cuenta suspendida y ninguno medido es CUENTA_PAUSADA")
    void cuentaPausada() {
        List<DiaDelSemaforo> ventana = ventanaDe(EstadoDiaSemaforo.CUENTA_SUSPENDIDA,
                EstadoDiaSemaforo.CUENTA_SUSPENDIDA, EstadoDiaSemaforo.CUENTA_SUSPENDIDA,
                EstadoDiaSemaforo.CUENTA_SUSPENDIDA, EstadoDiaSemaforo.CUENTA_SUSPENDIDA,
                EstadoDiaSemaforo.PENDIENTE, EstadoDiaSemaforo.PENDIENTE);

        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.SIN_DATOS, false, false, 40, ventana)))
                .isEqualTo(GrupoDelMensaje.CUENTA_PAUSADA);
    }

    @Test
    @DisplayName("en cuanto hay un día medido ya no es CUENTA_PAUSADA")
    void conUnDiaMedidoDejaDeSerCuentaPausada() {
        List<DiaDelSemaforo> ventana = ventanaDe(EstadoDiaSemaforo.CUENTA_SUSPENDIDA,
                EstadoDiaSemaforo.CUENTA_SUSPENDIDA, EstadoDiaSemaforo.CUENTA_SUSPENDIDA,
                EstadoDiaSemaforo.CUENTA_SUSPENDIDA, EstadoDiaSemaforo.CUENTA_SUSPENDIDA,
                EstadoDiaSemaforo.CUENTA_SUSPENDIDA, EstadoDiaSemaforo.MEDIDO);

        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.VERDE, false, false, 40, ventana)))
                .isEqualTo(GrupoDelMensaje.VERDE);
    }

    @Test
    @DisplayName("el orden: cuenta pausada, graduado, pausa, y recién después el color")
    void elOrdenDeLasReglas() {
        List<DiaDelSemaforo> suspendidos = ventanaDe(EstadoDiaSemaforo.CUENTA_SUSPENDIDA,
                EstadoDiaSemaforo.CUENTA_SUSPENDIDA, EstadoDiaSemaforo.CUENTA_SUSPENDIDA,
                EstadoDiaSemaforo.CUENTA_SUSPENDIDA, EstadoDiaSemaforo.CUENTA_SUSPENDIDA,
                EstadoDiaSemaforo.CUENTA_SUSPENDIDA, EstadoDiaSemaforo.CUENTA_SUSPENDIDA);

        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.SIN_DATOS, true, true, 90, suspendidos)))
                .as("cuenta pausada gana a graduado y a pausa").isEqualTo(GrupoDelMensaje.CUENTA_PAUSADA);
        assertThat(GrupoDelDia.de(situacion(ColorSemaforo.VERDE, true, true, 90, ventanaMedida())))
                .as("graduado gana a pausa").isEqualTo(GrupoDelMensaje.GRADUADO);
    }

    @Test
    @DisplayName("D-281: el color del día de la tarjeta del chat da su grupo; SIN_DATOS no tiene grupo de color")
    void elGrupoDeUnColor() {
        assertThat(GrupoDelDia.delColor(ColorSemaforo.VERDE)).contains(GrupoDelMensaje.VERDE);
        assertThat(GrupoDelDia.delColor(ColorSemaforo.AMARILLO)).contains(GrupoDelMensaje.AMARILLO);
        assertThat(GrupoDelDia.delColor(ColorSemaforo.ROJO)).contains(GrupoDelMensaje.ROJO);
        assertThat(GrupoDelDia.delColor(ColorSemaforo.SIN_DATOS)).isEmpty();
    }
}
