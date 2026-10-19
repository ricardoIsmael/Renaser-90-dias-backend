package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.domain.model.semaforo.ReglaDelSemaforo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code {faltan}} (FR-006, research R5): cuántos pendientes de hoy faltan para que la ventana de mañana suba de
 * color, con la aritmética del semáforo (porcentaje del día a entero, promedio a un decimal, cortes 80 y 60).
 */
class HabitosQueFaltanTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 19);

    private static DiaDelSemaforo medido(int diasAtras, int porcentaje) {
        return new DiaDelSemaforo(HOY.minusDays(diasAtras), EstadoDiaSemaforo.MEDIDO, porcentaje,
                ReglaDelSemaforo.colorDelDia(porcentaje), 1, 1, 0, 0);
    }

    private static DiaDelSemaforo sinMedir(int diasAtras, EstadoDiaSemaforo estado) {
        return DiaDelSemaforo.sinPorcentaje(HOY.minusDays(diasAtras), estado);
    }

    /** La ventana vigente: siete días del más viejo (hace 7) al más nuevo (ayer). */
    private static List<DiaDelSemaforo> ventana(int primero, int... ultimosSeis) {
        List<DiaDelSemaforo> dias = new ArrayList<>();
        dias.add(medido(7, primero));
        for (int i = 0; i < ultimosSeis.length; i++) {
            dias.add(medido(6 - i, ultimosSeis[i]));
        }
        return dias;
    }

    /** Hoy: {@code programados} entre hábitos y objetivos, {@code cumplidos} de ellos. */
    private static DiaDelSemaforo hoy(int programados, int cumplidos) {
        int porcentaje = ReglaDelSemaforo.porcentajeDelDia(cumplidos, programados);
        return new DiaDelSemaforo(HOY, EstadoDiaSemaforo.MEDIDO, porcentaje, ReglaDelSemaforo.colorDelDia(porcentaje),
                programados, cumplidos, 0, 0);
    }

    @Test
    @DisplayName("en amarillo: el mínimo de pendientes de hoy para que mañana sea verde")
    void deAmarilloAVerde() {
        // Los seis últimos suman 460; con hoy al 100 % la ventana de mañana da 560 / 7 = 80,0.
        List<DiaDelSemaforo> ventana = ventana(50, 100, 80, 70, 70, 70, 70);

        Optional<Integer> faltan = HabitosQueFaltan.de(ColorSemaforo.AMARILLO, ventana, hoy(3, 1));

        assertThat(faltan).contains(2);
    }

    @Test
    @DisplayName("en rojo: el mínimo de pendientes para que mañana sea amarillo")
    void deRojoAAmarillo() {
        // Los seis últimos suman 340; hoy necesita 80 % para llegar a 420 / 7 = 60,0. Con 3 de 5 (60 %) no alcanza.
        List<DiaDelSemaforo> ventana = ventana(40, 60, 60, 60, 60, 50, 50);

        Optional<Integer> faltan = HabitosQueFaltan.de(ColorSemaforo.ROJO, ventana, hoy(5, 2));

        assertThat(faltan).contains(2);
    }

    @Test
    @DisplayName("baja al cumplir un pendiente")
    void bajaAlCumplir() {
        List<DiaDelSemaforo> ventana = ventana(40, 60, 60, 60, 60, 50, 50);

        assertThat(HabitosQueFaltan.de(ColorSemaforo.ROJO, ventana, hoy(5, 2))).contains(2);
        assertThat(HabitosQueFaltan.de(ColorSemaforo.ROJO, ventana, hoy(5, 3))).contains(1);
    }

    @Test
    @DisplayName("si lo cumplido hoy ya alcanza para el color siguiente, no hay dato (k ≤ 0)")
    void yaAlcanza() {
        List<DiaDelSemaforo> ventana = ventana(40, 60, 60, 60, 60, 50, 50);

        assertThat(HabitosQueFaltan.de(ColorSemaforo.ROJO, ventana, hoy(5, 4))).isEmpty();
        assertThat(HabitosQueFaltan.de(ColorSemaforo.ROJO, ventana, hoy(5, 5))).isEmpty();
    }

    @Test
    @DisplayName("si ni cumpliendo todo lo de hoy sube de color, no hay dato (k > P − C)")
    void ningunaCantidadAlcanza() {
        // Seis días en 70 suman 420: con hoy al 100 % da 520 / 7 = 74,3, sigue amarillo.
        List<DiaDelSemaforo> ventana = ventana(70, 70, 70, 70, 70, 70, 70);

        assertThat(HabitosQueFaltan.de(ColorSemaforo.AMARILLO, ventana, hoy(4, 0))).isEmpty();
    }

    @Test
    @DisplayName("caso borde: 559 / 7 = 79,857 se muestra 79,9, no es verde ni cumpliendo todo")
    void ochentaMenosUnaCentesima() {
        List<DiaDelSemaforo> ventana = ventana(50, 100, 80, 70, 70, 70, 69);

        assertThat(HabitosQueFaltan.de(ColorSemaforo.AMARILLO, ventana, hoy(3, 1))).isEmpty();
    }

    @Test
    @DisplayName("solo promedia los días medidos: los pausados, sin datos o pendientes no cuentan")
    void ignoraLosDiasSinMedir() {
        List<DiaDelSemaforo> ventana = new ArrayList<>(List.of(medido(7, 40), medido(6, 100), medido(5, 80),
                sinMedir(4, EstadoDiaSemaforo.SIN_DATOS), sinMedir(3, EstadoDiaSemaforo.PAUSADO),
                sinMedir(2, EstadoDiaSemaforo.PENDIENTE), sinMedir(1, EstadoDiaSemaforo.SIN_DATOS)));

        // (100 + 80 + hoy) / 3 ≥ 80 pide hoy ≥ 60 %: con 5 programados son 3.
        assertThat(HabitosQueFaltan.de(ColorSemaforo.AMARILLO, ventana, hoy(5, 1))).contains(2);
    }

    @Test
    @DisplayName("en verde no hay color al que subir")
    void enVerdeNoHayDato() {
        assertThat(HabitosQueFaltan.de(ColorSemaforo.VERDE, ventana(90, 90, 90, 90, 90, 90, 90), hoy(3, 0)))
                .isEmpty();
    }

    @Test
    @DisplayName("sin color no hay dato")
    void sinDatosNoHayDato() {
        assertThat(HabitosQueFaltan.de(ColorSemaforo.SIN_DATOS, ventana(0, 0, 0, 0, 0, 0, 0), hoy(3, 0))).isEmpty();
    }

    @Test
    @DisplayName("si hoy no es un día medido (nada programado, pausa, fuera del programa) no hay dato")
    void hoyQueNoSeMide() {
        List<DiaDelSemaforo> ventana = ventana(40, 60, 60, 60, 60, 50, 50);

        for (EstadoDiaSemaforo estado : List.of(EstadoDiaSemaforo.SIN_DATOS, EstadoDiaSemaforo.PAUSADO,
                EstadoDiaSemaforo.FUERA_DEL_PROGRAMA, EstadoDiaSemaforo.CUENTA_SUSPENDIDA)) {
            assertThat(HabitosQueFaltan.de(ColorSemaforo.ROJO, ventana, sinMedir(0, estado))).as("%s", estado).isEmpty();
        }
    }

    @Test
    @DisplayName("cuenta hábitos y objetivos juntos, como el semáforo")
    void sumaHabitosYObjetivos() {
        List<DiaDelSemaforo> ventana = ventana(40, 60, 60, 60, 60, 50, 50);
        // 3 hábitos (1 cumplido) + 2 objetivos (1 cumplido) = 5 programados, 2 cumplidos.
        DiaDelSemaforo hoy = new DiaDelSemaforo(HOY, EstadoDiaSemaforo.MEDIDO, 40, ColorSemaforo.ROJO, 3, 1, 2, 1);

        assertThat(HabitosQueFaltan.de(ColorSemaforo.ROJO, ventana, hoy)).contains(2);
    }
}
