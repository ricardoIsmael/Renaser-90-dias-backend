package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.points.api.CumplimientoDeHabito;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-006: el hábito más flojo y el más fuerte de la ventana, sin adivinar. */
class HabitosDestacadosTest {

    private static CumplimientoDeHabito habito(String nombre, int programados, int cumplidos) {
        return new CumplimientoDeHabito(nombre, programados, cumplidos);
    }

    @Test
    @DisplayName("elige el de menor y el de mayor cumplimiento")
    void eligeLosExtremos() {
        HabitosDestacados destacados = HabitosDestacados.de(List.of(
                habito("Agua", 7, 7), habito("Lectura", 7, 2), habito("Caminar", 7, 5)));

        assertThat(destacados.flojo()).isEqualTo("Lectura");
        assertThat(destacados.fuerte()).isEqualTo("Agua");
    }

    @Test
    @DisplayName("solo cuentan los hábitos con al menos 2 días programados")
    void exigeDosDiasProgramados() {
        HabitosDestacados destacados = HabitosDestacados.de(List.of(
                habito("Agua", 7, 6), habito("Lectura", 7, 3), habito("Un solo día", 1, 0), habito("Ninguno", 0, 0)));

        assertThat(destacados.flojo()).isEqualTo("Lectura");
        assertThat(destacados.fuerte()).isEqualTo("Agua");
    }

    @Test
    @DisplayName("el fuerte necesita una tasa de 50 % o más")
    void elFuerteExigeLaMitad() {
        HabitosDestacados sinFuerte = HabitosDestacados.de(List.of(habito("Agua", 7, 3), habito("Lectura", 7, 1)));
        HabitosDestacados conFuerte = HabitosDestacados.de(List.of(habito("Agua", 4, 2), habito("Lectura", 4, 1)));

        assertThat(sinFuerte.fuerte()).isNull();
        assertThat(sinFuerte.flojo()).isEqualTo("Lectura");
        assertThat(conFuerte.fuerte()).isEqualTo("Agua");
    }

    @Test
    @DisplayName("un empate en el extremo deja ese dato vacío")
    void unEmpateNoSeAdivina() {
        HabitosDestacados destacados = HabitosDestacados.de(List.of(
                habito("Agua", 7, 7), habito("Caminar", 6, 6), habito("Lectura", 7, 2)));

        assertThat(destacados.fuerte()).as("Agua y Caminar empatan al 100 %").isNull();
        assertThat(destacados.flojo()).isEqualTo("Lectura");
    }

    @Test
    @DisplayName("2 de 3 y 4 de 6 son la misma tasa: empatan sin errores de redondeo")
    void lasTasasSeComparanSinDecimales() {
        HabitosDestacados destacados = HabitosDestacados.de(List.of(
                habito("Agua", 3, 2), habito("Caminar", 6, 4), habito("Lectura", 7, 1)));

        assertThat(destacados.fuerte()).isNull();
        assertThat(destacados.flojo()).isEqualTo("Lectura");
    }

    @Test
    @DisplayName("si todos rinden igual, el flojo y el fuerte serían los mismos: los dos quedan vacíos")
    void todosIgualesNoHayDatos() {
        HabitosDestacados destacados = HabitosDestacados.de(List.of(habito("Agua", 7, 5), habito("Lectura", 7, 5)));

        assertThat(destacados.flojo()).isNull();
        assertThat(destacados.fuerte()).isNull();
    }

    @Test
    @DisplayName("con un solo hábito que cuente, el flojo y el fuerte serían el mismo: los dos quedan vacíos")
    void unSoloHabitoNoHayDatos() {
        HabitosDestacados destacados = HabitosDestacados.de(List.of(habito("Agua", 7, 5), habito("Un día", 1, 1)));

        assertThat(destacados.flojo()).isNull();
        assertThat(destacados.fuerte()).isNull();
    }

    @Test
    @DisplayName("sin hábitos no hay datos")
    void sinHabitosNoHayDatos() {
        HabitosDestacados destacados = HabitosDestacados.de(List.of());

        assertThat(destacados.flojo()).isNull();
        assertThat(destacados.fuerte()).isNull();
    }
}
