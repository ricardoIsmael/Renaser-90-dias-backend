package com.renaser.os.rocks.domain.model.rocadiaria;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Portado 1:1 desde {@code longestRocksStreakDays} del backend viejo
 * (RenaserBack/src/features/profile/service.ts:248-270): un día solo cuenta
 * para la racha si tiene las 3 Rocas Diarias completas ese día.
 */
class RachaRocasTest {

    private static final LocalDate D1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 8, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 8, 3);
    private static final LocalDate D4 = LocalDate.of(2026, 8, 4);
    private static final LocalDate D6 = LocalDate.of(2026, 8, 6);

    @Test
    void sinRocasDevuelveCero() {
        assertThat(RachaRocas.calcular(null)).isZero();
        assertThat(RachaRocas.calcular(List.of())).isZero();
    }

    @Test
    void ningunDiaLlegaATresRocas_devuelveCero() {
        List<LocalDate> fechas = tresVeces(D1, 2); // 2/3, no cuenta
        assertThat(RachaRocas.calcular(fechas)).isZero();
    }

    @Test
    void unSoloDiaCompleto_rachaDeUnDia() {
        List<LocalDate> fechas = tresVeces(D1, 3);
        assertThat(RachaRocas.calcular(fechas)).isEqualTo(1);
    }

    @Test
    void variosDiasConsecutivosCompletos_cuentaLaRachaEntera() {
        List<LocalDate> fechas = new ArrayList<>();
        fechas.addAll(tresVeces(D1, 3));
        fechas.addAll(tresVeces(D2, 3));
        fechas.addAll(tresVeces(D3, 3));
        assertThat(RachaRocas.calcular(fechas)).isEqualTo(3);
    }

    @Test
    void rachaInterrumpidaPorUnDiaIncompleto_seCortaAhi() {
        List<LocalDate> fechas = new ArrayList<>();
        fechas.addAll(tresVeces(D1, 3));
        fechas.addAll(tresVeces(D2, 3));
        fechas.addAll(tresVeces(D3, 2)); // interrumpe: solo 2/3
        fechas.addAll(tresVeces(D4, 3));
        assertThat(RachaRocas.calcular(fechas)).isEqualTo(2);
    }

    @Test
    void diasCompletosNoConsecutivos_devuelveLaMasLarga() {
        List<LocalDate> fechas = new ArrayList<>();
        fechas.addAll(tresVeces(D1, 3));
        fechas.addAll(tresVeces(D2, 3));
        // hueco: D3, D5 sin Rocas completas
        fechas.addAll(tresVeces(D6, 3));
        assertThat(RachaRocas.calcular(fechas)).isEqualTo(2);
    }

    @Test
    void rachaQueTerminaHoy_seCuentaIgual() {
        LocalDate hoy = LocalDate.now();
        LocalDate ayer = hoy.minusDays(1);
        List<LocalDate> fechas = new ArrayList<>();
        fechas.addAll(tresVeces(ayer, 3));
        fechas.addAll(tresVeces(hoy, 3));
        assertThat(RachaRocas.calcular(fechas)).isEqualTo(2);
    }

    @Test
    void masDeTresRocasElMismoDia_siguenContandoComoUnDiaCompleto() {
        // no debería pasar en producción (máximo 3 ejes), pero el cálculo no debe romperse
        List<LocalDate> fechas = tresVeces(D1, 4);
        assertThat(RachaRocas.calcular(fechas)).isEqualTo(1);
    }

    private static List<LocalDate> tresVeces(LocalDate fecha, int repeticiones) {
        return java.util.stream.Stream.generate(() -> fecha).limit(repeticiones).toList();
    }
}
