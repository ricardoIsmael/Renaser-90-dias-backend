package com.renaser.os.leadership.domain.model.habitos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstadoEnElDiaYVentanaTest {

    @Test
    @DisplayName("cumplido manda; opcional sin hacer es «sin datos» y nunca una falta; el resto depende de si el día cerró")
    void estados() {
        assertThat(EstadoEnElDia.de(true, false, false)).isEqualTo(EstadoEnElDia.CUMPLIDO);
        assertThat(EstadoEnElDia.de(true, false, true)).isEqualTo(EstadoEnElDia.CUMPLIDO);
        assertThat(EstadoEnElDia.de(false, false, false)).isEqualTo(EstadoEnElDia.PENDIENTE);
        assertThat(EstadoEnElDia.de(false, true, false)).isEqualTo(EstadoEnElDia.SIN_CUMPLIR);
        assertThat(EstadoEnElDia.de(false, true, true)).isEqualTo(EstadoEnElDia.SIN_DATOS);
        assertThat(EstadoEnElDia.de(false, false, true)).isEqualTo(EstadoEnElDia.SIN_DATOS);
        assertThat(EstadoEnElDia.SIN_DATOS.cuentaComoEsperado()).isFalse();
        assertThat(EstadoEnElDia.PENDIENTE.cuentaComoEsperado()).isTrue();
    }

    @Test
    @DisplayName("en la lista de personas, primero a quien hay que escribirle")
    void ordenDeLaLista() {
        assertThat(EstadoEnElDia.SIN_CUMPLIR.ordenEnLaLista()).isLessThan(EstadoEnElDia.PENDIENTE.ordenEnLaLista());
        assertThat(EstadoEnElDia.PENDIENTE.ordenEnLaLista()).isLessThan(EstadoEnElDia.CUMPLIDO.ordenEnLaLista());
        assertThat(EstadoEnElDia.CUMPLIDO.ordenEnLaLista()).isLessThan(EstadoEnElDia.SIN_DATOS.ordenEnLaLista());
    }

    @Test
    @DisplayName("la ventana es hoy y siete días atrás: ni el futuro ni más viejo")
    void ventana() {
        VentanaDeLosHabitos ventana = VentanaDeLosHabitos.de(LocalDate.of(2026, 10, 31));

        assertThat(ventana.fechaElegida(null)).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(ventana.fechaElegida(LocalDate.of(2026, 10, 24))).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThatThrownBy(() -> ventana.fechaElegida(LocalDate.of(2026, 11, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ventana.fechaElegida(LocalDate.of(2026, 10, 23))).isInstanceOf(IllegalArgumentException.class);
        assertThat(ventana.esHoy(LocalDate.of(2026, 10, 31))).isTrue();
        assertThat(ventana.esHoy(LocalDate.of(2026, 10, 30))).isFalse();
    }
}
