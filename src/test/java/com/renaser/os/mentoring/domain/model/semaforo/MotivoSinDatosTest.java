package com.renaser.os.mentoring.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** S-5: el «Sin datos» con su motivo, calculado de los días; nunca cambia el color ni el porcentaje. */
class MotivoSinDatosTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 9, 18);

    /** Siete días con los estados dados; ninguno medido. */
    private static VentanaDelSemaforo sinDatos(EstadoDiaSemaforo... estados) {
        List<DiaDelSemaforo> dias = new ArrayList<>();
        for (int i = 0; i < estados.length; i++) {
            dias.add(DiaDelSemaforo.sinPorcentaje(DESDE.plusDays(i), estados[i]));
        }
        return new VentanaDelSemaforo(DESDE, DESDE.plusDays(6), null, ColorSemaforo.SIN_DATOS, 0, false, dias);
    }

    private static EstadoDiaSemaforo[] siete(EstadoDiaSemaforo estado) {
        EstadoDiaSemaforo[] estados = new EstadoDiaSemaforo[7];
        java.util.Arrays.fill(estados, estado);
        return estados;
    }

    @Test
    @DisplayName("no activó su programa: NO_ACTIVADO (el semáforo no trae su ventana)")
    void noActivado() {
        assertThat(MedicionDelAprendiz.de(null).motivo()).isEqualTo(MotivoSinDatos.NO_ACTIVADO);
    }

    @Test
    @DisplayName("en el programa y sin nada programado: SIN_NADA_PLANIFICADO, aunque arrancara a mitad de la ventana")
    void sinNadaPlanificado() {
        var fuera = EstadoDiaSemaforo.FUERA_DEL_PROGRAMA;
        var sin = EstadoDiaSemaforo.SIN_DATOS;

        assertThat(MedicionDelAprendiz.de(sinDatos(siete(sin))).motivo()).isEqualTo(MotivoSinDatos.SIN_NADA_PLANIFICADO);
        assertThat(MedicionDelAprendiz.de(sinDatos(fuera, fuera, fuera, fuera, sin, sin, sin)).motivo())
                .isEqualTo(MotivoSinDatos.SIN_NADA_PLANIFICADO);
    }

    @Test
    @DisplayName("día 0 o graduado: FUERA_DEL_PROGRAMA; cálculo pendiente y pausa tienen su propio motivo")
    void fueraPendienteYPausado() {
        assertThat(MedicionDelAprendiz.de(sinDatos(siete(EstadoDiaSemaforo.FUERA_DEL_PROGRAMA))).motivo())
                .isEqualTo(MotivoSinDatos.FUERA_DEL_PROGRAMA);
        assertThat(MedicionDelAprendiz.de(sinDatos(siete(EstadoDiaSemaforo.PENDIENTE))).motivo())
                .isEqualTo(MotivoSinDatos.PENDIENTE_DE_CALCULO);
        assertThat(MedicionDelAprendiz.de(sinDatos(siete(EstadoDiaSemaforo.PAUSADO))).motivo())
                .isEqualTo(MotivoSinDatos.PAUSADO);
    }

    @Test
    @DisplayName("con color no hay motivo, y el motivo no toca color ni porcentaje")
    void conColorSinMotivo() {
        var medicion = new MedicionDelAprendiz(new BigDecimal("55.0"), ColorSemaforo.ROJO, 7, List.of());

        assertThat(medicion.motivo()).isNull();
        assertThat(medicion.color()).isEqualTo(ColorSemaforo.ROJO);
    }

    @Test
    @DisplayName("dentro de «Sin datos», el más desconectado primero; los colores no cambian de lugar")
    void ordenDentroDeSinDatos() {
        record Fila(String nombre, MedicionDelAprendiz medicion) {
        }
        var graduada = new Fila("Ángela", MedicionDelAprendiz.de(sinDatos(siete(EstadoDiaSemaforo.FUERA_DEL_PROGRAMA))));
        var noArranco = new Fila("Beto", MedicionDelAprendiz.SIN_MEDICION);
        var desconectada = new Fila("Zoila", MedicionDelAprendiz.de(sinDatos(siete(EstadoDiaSemaforo.SIN_DATOS))));
        var amarilla = new Fila("Yola", new MedicionDelAprendiz(new BigDecimal("70.0"), ColorSemaforo.AMARILLO, 7, List.of()));
        var verde = new Fila("Abel", new MedicionDelAprendiz(new BigDecimal("90.0"), ColorSemaforo.VERDE, 7, List.of()));
        List<Fila> filas = new ArrayList<>(List.of(verde, graduada, noArranco, desconectada, amarilla));

        filas.sort(OrdenDelSemaforo.porMedicionYNombre(Fila::medicion, Fila::nombre));

        // Con el orden anterior (color y nombre) Zoila, la que no planificó nada, quedaba última de su bloque.
        assertThat(filas).extracting(Fila::nombre).containsExactly("Yola", "Zoila", "Beto", "Ángela", "Abel");
    }
}
