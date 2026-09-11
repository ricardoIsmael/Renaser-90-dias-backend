package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El cupo cuenta aprendices, no personas. Mentor, guía y soporte están en el grupo pero no
 * ocupan un lugar (plan.md §3): si contaran, un grupo de 10 con mentor y soporte aceptaría
 * solo 8 alumnos y el número comercial dejaría de significar lo que dice.
 */
class CupoCelulaTest {

    private static UserId usuario() {
        return UserId.of(UUID.randomUUID());
    }

    private static List<FuncionAcompanamiento> ocupantes(int aprendices, FuncionAcompanamiento... otros) {
        List<FuncionAcompanamiento> lista = new java.util.ArrayList<>();
        for (int i = 0; i < aprendices; i++) {
            lista.add(FuncionAcompanamiento.APRENDIZ);
        }
        lista.addAll(List.of(otros));
        return lista;
    }

    @Test
    @DisplayName("solo el aprendiz consume cupo")
    void soloAprendizConsume() {
        assertThat(FuncionAcompanamiento.APRENDIZ.consumeCupo()).isTrue();
        assertThat(FuncionAcompanamiento.MENTOR.consumeCupo()).isFalse();
        assertThat(FuncionAcompanamiento.GUIA.consumeCupo()).isFalse();
        assertThat(FuncionAcompanamiento.SOPORTE.consumeCupo()).isFalse();
    }

    @Test
    @DisplayName("un grupo de 10 con mentor y soporte todavia admite al decimo aprendiz")
    void mentorYSoporteNoRestanLugares() {
        CupoCelula cupo = CupoCelula.regular(10);

        assertThat(cupo.admiteOtroAprendiz(
                ocupantes(9, FuncionAcompanamiento.MENTOR, FuncionAcompanamiento.SOPORTE))).isTrue();
    }

    @Test
    @DisplayName("lleno con 10 aprendices, sin importar cuanto acompanamiento haya encima")
    void llenoSeMideEnAprendices() {
        CupoCelula cupo = CupoCelula.regular(10);

        assertThat(cupo.admiteOtroAprendiz(
                ocupantes(10, FuncionAcompanamiento.MENTOR, FuncionAcompanamiento.GUIA))).isFalse();
    }

    @Test
    @DisplayName("recepcion no tiene tope comercial (D-05)")
    void recepcionNoTieneTope() {
        CupoCelula recepcion = CupoCelula.recepcion();

        assertThat(recepcion.admiteOtroAprendiz(ocupantes(200))).isTrue();
        assertThat(recepcion.maximo()).isEmpty();
    }

    @Test
    @DisplayName("la capacidad configurable vive entre 10 y 15 (D-01)")
    void capacidadFueraDeRango() {
        assertThat(CupoCelula.regular(10).maximo()).contains(10);
        assertThat(CupoCelula.regular(15).maximo()).contains(15);

        assertThatThrownBy(() -> CupoCelula.regular(9)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CupoCelula.regular(16)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bajar la capacidad por debajo de la ocupacion no expulsa: bloquea altas (RF-28)")
    void reducirCapacidadNoExpulsa() {
        CupoCelula reducido = CupoCelula.regular(10);
        List<FuncionAcompanamiento> doceAprendices = ocupantes(12);

        assertThat(reducido.admiteOtroAprendiz(doceAprendices)).isFalse();
        assertThat(reducido.excedente(doceAprendices)).isEqualTo(2);
        assertThat(reducido.excedente(ocupantes(8))).isZero();
    }
}
