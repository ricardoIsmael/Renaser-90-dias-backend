package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las cuatro fases estiradas a la duración de cada persona (spec 022): con 90 días son las de siempre (1, 8, 35, 65). */
class CortesDeFaseTest {

    @Test
    @DisplayName("Con 90 días las fases empiezan en 1, 8, 35 y 65, como siempre")
    void conNoventaSonLasDeSiempre() {
        assertThat(inicios(90)).containsExactly(1, 8, 35, 65);
        assertThat(fines(90)).containsExactly(7, 34, 64, 90);
    }

    @Test
    @DisplayName("Con 365 días las fases se estiran en proporción y siguen cubriendo del día 1 al 365 sin huecos")
    void conUnAnioSeEstiran() {
        assertThat(inicios(365)).containsExactly(1, 29, 138, 260);
        assertThat(fines(365)).containsExactly(28, 137, 259, 365);
        for (int fase = 2; fase <= 4; fase++) {
            assertThat(CortesDeFase.inicioDeFase(fase, 365)).isEqualTo(CortesDeFase.finDeFase(fase - 1, 365) + 1);
        }
    }

    @Test
    @DisplayName("El número de fase de un día cambia exactamente en cada corte, con 90 y con 365")
    void elNumeroDeFaseCambiaEnElCorte() {
        for (int duracion : new int[]{90, 365}) {
            assertThat(CortesDeFase.numeroDeFase(1, duracion)).isEqualTo(1);
            for (int fase = 2; fase <= 4; fase++) {
                int corte = CortesDeFase.inicioDeFase(fase, duracion);
                assertThat(CortesDeFase.numeroDeFase(corte - 1, duracion)).isEqualTo(fase - 1);
                assertThat(CortesDeFase.numeroDeFase(corte, duracion)).isEqualTo(fase);
            }
            assertThat(CortesDeFase.numeroDeFase(duracion, duracion)).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("Una fase fuera de 1 a 4 o una duración menor a 1 se rechazan")
    void rechazaLoInvalido() {
        assertThatThrownBy(() -> CortesDeFase.inicioDeFase(0, 90)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CortesDeFase.finDeFase(5, 90)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CortesDeFase.numeroDeFase(3, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static int[] inicios(int duracion) {
        return java.util.stream.IntStream.rangeClosed(1, 4).map(f -> CortesDeFase.inicioDeFase(f, duracion)).toArray();
    }

    private static int[] fines(int duracion) {
        return java.util.stream.IntStream.rangeClosed(1, 4).map(f -> CortesDeFase.finDeFase(f, duracion)).toArray();
    }
}
