package com.renaser.os.plataforma.domain.model.despliegue;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Qué hace un cambio de escalón y cuándo es crítico (contrato fase 2 §3.4). Sin Spring. */
class CambioDeDespliegueTest {

    private static final DespliegueDeFuncionalidad AL_25 = DespliegueDeFuncionalidad.empezar("eventos", 25, 7,
            UserId.of(UUID.randomUUID()), Instant.parse("2026-10-14T04:30:00Z"));

    @Test
    @DisplayName("el paso sale de lo que había: empieza, sube, baja, termina o nada")
    void pasos() {
        assertThat(cambio(5).pasoDesde(null)).isEqualTo(PasoDelDespliegue.EMPIEZA);
        assertThat(cambio(0).pasoDesde(null)).isEqualTo(PasoDelDespliegue.EMPIEZA);
        assertThat(cambio(100).pasoDesde(null)).as("100 sin despliegue: ya llega a todos").isEqualTo(PasoDelDespliegue.NINGUNO);
        assertThat(cambio(50).pasoDesde(AL_25)).isEqualTo(PasoDelDespliegue.SUBE);
        assertThat(cambio(5).pasoDesde(AL_25)).isEqualTo(PasoDelDespliegue.BAJA);
        assertThat(cambio(100).pasoDesde(AL_25)).isEqualTo(PasoDelDespliegue.TERMINA);
        assertThat(cambio(25).pasoDesde(AL_25)).isEqualTo(PasoDelDespliegue.NINGUNO);
    }

    @Test
    @DisplayName("crítico, como dice el contrato: empezar o bajar una crítica, y empezar al 0 % cualquiera")
    void critico() {
        assertThat(cambio(5).esCritico(PasoDelDespliegue.EMPIEZA, true)).isTrue();
        assertThat(cambio(5).esCritico(PasoDelDespliegue.EMPIEZA, false)).isFalse();
        assertThat(cambio(0).esCritico(PasoDelDespliegue.EMPIEZA, false)).as("al 0 % la esconde a todos").isTrue();
        assertThat(cambio(5).esCritico(PasoDelDespliegue.BAJA, true)).isTrue();
        assertThat(cambio(5).esCritico(PasoDelDespliegue.BAJA, false)).isFalse();
        assertThat(cambio(50).esCritico(PasoDelDespliegue.SUBE, true)).as("subir solo suma personas").isFalse();
        assertThat(cambio(100).esCritico(PasoDelDespliegue.TERMINA, true)).isFalse();
    }

    @Test
    @DisplayName("porcentaje fuera de 0..100, motivo vacío o versión negativa: pedido inválido (400)")
    void pedidosInvalidos() {
        assertThatThrownBy(() -> cambio(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cambio(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CambioDeDespliegue("eventos", 5, "  ", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
        assertThatThrownBy(() -> new CambioDeDespliegue("eventos", 5, "x".repeat(501), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CambioDeDespliegue("eventos", 5, "Probar", -1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new CambioDeDespliegue("eventos", 5, "  Probar  ", null).motivo()).isEqualTo("Probar");
    }

    @Test
    @DisplayName("el resumen del push dice el escalón en palabras")
    void resumen() {
        assertThat(cambio(5).resumen(PasoDelDespliegue.EMPIEZA, null)).isEqualTo("En despliegue al 5 %");
        assertThat(cambio(0).resumen(PasoDelDespliegue.EMPIEZA, null)).contains("solo para probadores");
        assertThat(cambio(50).resumen(PasoDelDespliegue.SUBE, AL_25)).isEqualTo("En despliegue: 25 % → 50 %");
        assertThat(cambio(100).resumen(PasoDelDespliegue.TERMINA, AL_25)).isEqualTo("Terminó el despliegue: llega a todos");
    }

    private static CambioDeDespliegue cambio(int porcentaje) {
        return new CambioDeDespliegue("eventos", porcentaje, "Probar", null);
    }
}
