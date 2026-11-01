package com.renaser.os.onboarding.domain.model.pagos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagoDeLaPersonaTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:00Z");

    @Test
    @DisplayName("tres situaciones: sin anotar (no hay nada que mostrar), sin cronograma y con cronograma")
    void tresSituaciones() {
        assertThat(PagoDeLaPersona.sinAnotar().anotado()).isFalse();
        assertThat(PagoDeLaPersona.sinCronograma().anotado()).isTrue();
        assertThat(PagoDeLaPersona.sinCronograma().esConCronograma()).isFalse();
        assertThat(PagoDeLaPersona.conCronograma(Moneda.PEN).anotado()).isTrue();
        assertThat(PagoDeLaPersona.conCronograma(Moneda.PEN).esConCronograma()).isTrue();
        assertThat(PagoDeLaPersona.conCronograma(Moneda.USD).moneda()).isEqualTo(Moneda.USD);
    }

    @Test
    @DisplayName("con cronograma lleva su moneda y sin cronograma no la lleva")
    void laMonedaVaConElCronograma() {
        assertThatThrownBy(() -> new PagoDeLaPersona(ModalidadDePago.CON_CRONOGRAMA, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagoDeLaPersona(ModalidadDePago.SIN_CRONOGRAMA, Moneda.PEN, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagoDeLaPersona(null, Moneda.PEN, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirmar guarda el nombre sin espacios sobrantes, la hora y la huella; solo un cronograma se confirma")
    void confirmar() {
        PagoDeLaPersona confirmado = PagoDeLaPersona.conCronograma(Moneda.PEN).confirmado("  Ana Quispe  ", "abc", AHORA);

        assertThat(confirmado.confirmadoNombre()).isEqualTo("Ana Quispe");
        assertThat(confirmado.confirmadoEn()).isEqualTo(AHORA);
        assertThat(confirmado.confirmadoHuella()).isEqualTo("abc");
        assertThat(confirmado.moneda()).isEqualTo(Moneda.PEN);

        assertThatThrownBy(() -> PagoDeLaPersona.sinCronograma().confirmado("Ana", "abc", AHORA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PagoDeLaPersona.sinAnotar().confirmado("Ana", "abc", AHORA)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("el nombre es obligatorio y llega a 120 letras")
    void elNombre() {
        PagoDeLaPersona con = PagoDeLaPersona.conCronograma(Moneda.PEN);

        assertThatThrownBy(() -> con.confirmado(null, "abc", AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> con.confirmado("   ", "abc", AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> con.confirmado("a".repeat(121), "abc", AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThat(con.confirmado("a".repeat(120), "abc", AHORA).confirmadoNombre()).hasSize(120);
    }

    @Test
    @DisplayName("la confirmación vale mientras la huella sea la misma; con otra huella queda desactualizada; sin confirmar, sin confirmar")
    void estadoDeLaConfirmacion() {
        PagoDeLaPersona sin = PagoDeLaPersona.conCronograma(Moneda.PEN);
        PagoDeLaPersona confirmado = sin.confirmado("Ana", "abc", AHORA);

        assertThat(sin.confirmacion("abc")).isEqualTo(EstadoDeConfirmacion.SIN_CONFIRMAR);
        assertThat(confirmado.confirmacion("abc")).isEqualTo(EstadoDeConfirmacion.CONFIRMADA);
        assertThat(confirmado.confirmacion("xyz")).isEqualTo(EstadoDeConfirmacion.DESACTUALIZADA);
    }

    @Test
    @DisplayName("la confirmación va completa o no va, y solo existe con cronograma")
    void laConfirmacionVaCompleta() {
        assertThatThrownBy(() -> new PagoDeLaPersona(ModalidadDePago.CON_CRONOGRAMA, Moneda.PEN, AHORA, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagoDeLaPersona(ModalidadDePago.CON_CRONOGRAMA, Moneda.PEN, AHORA, "Ana", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagoDeLaPersona(ModalidadDePago.SIN_CRONOGRAMA, null, AHORA, "Ana", "abc")).isInstanceOf(IllegalArgumentException.class);
    }
}
