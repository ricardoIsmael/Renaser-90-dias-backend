package com.renaser.os.mentoring.domain.model.resumen;

import com.renaser.os.mentoring.domain.model.semaforo.ConteoPorColor;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El conteo del cierre del sábado (solo semanas cerradas), sin Spring. */
class ConteoDelCierreTest {

    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 25);

    private final Map<UserId, VentanaDelSemaforo> semanas = new HashMap<>();

    private UserId aprendizCon(VentanaDelSemaforo semana) {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        semanas.put(aprendiz, semana);
        return aprendiz;
    }

    @Test
    @DisplayName("cada semana cerrada cuenta en su color")
    void cuentaLasSemanasCerradasPorColor() {
        List<UserId> aprendices = List.of(aprendizCon(cerrada(ColorSemaforo.VERDE)),
                aprendizCon(cerrada(ColorSemaforo.VERDE)), aprendizCon(cerrada(ColorSemaforo.AMARILLO)),
                aprendizCon(cerrada(ColorSemaforo.ROJO)), aprendizCon(cerrada(ColorSemaforo.SIN_DATOS)));

        assertThat(ReglasDelResumenSemanal.conteoDelCierre(aprendices, semanas)).isEqualTo(new ConteoPorColor(2, 1, 1, 1));
    }

    @Test
    @DisplayName("un aprendiz sin semana cerrada o sin semana cuenta como sin datos, nunca con su color vigente")
    void sinSemanaCerradaCuentaComoSinDatos() {
        UserId verdeTodaviaAbierta = aprendizCon(abierta(ColorSemaforo.VERDE));
        UserId rojoTodaviaAbierta = aprendizCon(abierta(ColorSemaforo.ROJO));
        UserId sinPrograma = UserId.of(UUID.randomUUID());
        UserId amarillo = aprendizCon(cerrada(ColorSemaforo.AMARILLO));

        ConteoPorColor conteo = ReglasDelResumenSemanal.conteoDelCierre(
                List.of(verdeTodaviaAbierta, rojoTodaviaAbierta, sinPrograma, amarillo), semanas);

        assertThat(conteo).isEqualTo(new ConteoPorColor(0, 1, 0, 3));
        assertThat(conteo.total()).isEqualTo(4);
    }

    @Test
    @DisplayName("solo cuenta a los aprendices pedidos: una semana de otra persona no suma")
    void soloCuentaALosAprendicesPedidos() {
        aprendizCon(cerrada(ColorSemaforo.VERDE));

        assertThat(ReglasDelResumenSemanal.conteoDelCierre(List.of(), semanas)).isEqualTo(ConteoPorColor.NINGUNO);
    }

    @Test
    @DisplayName("el resumen general es la suma de los grupos")
    void sumaDeGrupos() {
        ConteoPorColor suma = new ConteoPorColor(5, 2, 1, 0).mas(new ConteoPorColor(3, 0, 2, 1));

        assertThat(suma).isEqualTo(new ConteoPorColor(8, 2, 3, 1));
        assertThat(suma.total()).isEqualTo(14);
    }

    @Test
    @DisplayName("un conteo negativo no existe")
    void noAdmiteNegativos() {
        assertThatThrownBy(() -> new ConteoPorColor(0, -1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static VentanaDelSemaforo cerrada(ColorSemaforo color) {
        return semana(color, true);
    }

    private static VentanaDelSemaforo abierta(ColorSemaforo color) {
        return semana(color, false);
    }

    /** Coherente: sin datos no tiene porcentaje ni días medidos; los demás, un porcentaje de su color. */
    private static VentanaDelSemaforo semana(ColorSemaforo color, boolean cerrada) {
        BigDecimal porcentaje = switch (color) {
            case VERDE -> new BigDecimal("86.0");
            case AMARILLO -> new BigDecimal("72.5");
            case ROJO -> new BigDecimal("41.0");
            case SIN_DATOS -> null;
        };
        int diasConDatos = porcentaje == null ? 0 : 6;
        return new VentanaDelSemaforo(VIERNES.minusDays(6), VIERNES, porcentaje, color, diasConDatos, cerrada,
                List.of());
    }
}
