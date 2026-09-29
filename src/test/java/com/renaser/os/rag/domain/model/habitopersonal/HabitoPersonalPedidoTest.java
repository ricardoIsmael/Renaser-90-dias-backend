package com.renaser.os.rag.domain.model.habitopersonal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-229: el habito propio que propone SER, sin Spring ni base. */
class HabitoPersonalPedidoTest {

    @Test
    @DisplayName("la dimension se lee como la dice la persona, con o sin tilde, y mapea a la clave de habits")
    void leeLaDimension() {
        assertThat(DimensionDelHabito.leer(" Espíritu ")).contains(DimensionDelHabito.ESPIRITU);
        assertThat(DimensionDelHabito.leer("emociones")).contains(DimensionDelHabito.EMOCIONES);
        assertThat(DimensionDelHabito.leer("CONSCIENCIA")).contains(DimensionDelHabito.EMOCIONES);
        assertThat(DimensionDelHabito.EMOCIONES.claveCategoria()).isEqualTo("CONSCIENCIA");
        assertThat(DimensionDelHabito.leer("vida y negocio")).isEmpty();
        assertThat(DimensionDelHabito.leer(null)).isEmpty();
        assertThat(DimensionDelHabito.leer("  ")).isEmpty();
    }

    @Test
    @DisplayName("sin hora usa la de Training (06:00) y lo marca; sin dias son los siete")
    void valoresPorDefecto() {
        HabitoPersonalPedido pedido = HabitoPersonalPedido.de("  Leer   20 minutos ", DimensionDelHabito.MENTE, null,
                null, "  ");

        assertThat(pedido.nombre()).isEqualTo("Leer 20 minutos");
        assertThat(pedido.hora()).isEqualTo(LocalTime.of(6, 0));
        assertThat(pedido.horaElegida()).isFalse();
        assertThat(pedido.todosLosDias()).isTrue();
        assertThat(pedido.meta()).isNull();
        assertThat(pedido.resumen()).isEqualTo("Nuevo hábito: Leer 20 minutos · Mente · 06:00 · todos los días");
    }

    @Test
    @DisplayName("el resumen de la tarjeta lleva dimension, hora, dias en orden y meta")
    void resumenCompleto() {
        HabitoPersonalPedido pedido = HabitoPersonalPedido.de("Correr", DimensionDelHabito.CUERPO,
                LocalTime.of(7, 30), Set.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), "5 km");

        assertThat(pedido.resumen())
                .isEqualTo("Nuevo hábito: Correr · Cuerpo · 07:30 · lunes, miércoles y viernes · meta: 5 km");
        assertThat(pedido.diasParaGuardar()).isEqualTo("MONDAY,WEDNESDAY,FRIDAY");
    }

    @Test
    @DisplayName("mismo nombre sin importar mayusculas, tildes ni espacios")
    void mismoNombre() {
        HabitoPersonalPedido pedido = HabitoPersonalPedido.de("Meditación", DimensionDelHabito.ESPIRITU, null, null,
                null);

        assertThat(pedido.seLlamaIgualQue("  meditacion ")).isTrue();
        assertThat(pedido.seLlamaIgualQue("Meditación guiada")).isFalse();
        assertThat(pedido.seLlamaIgualQue(null)).isFalse();
    }

    @Test
    @DisplayName("sin nombre, o con nombre o meta mas largos que los del alta de la app, no hay pedido")
    void limites() {
        assertThatThrownBy(() -> HabitoPersonalPedido.de(" ", DimensionDelHabito.MENTE, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nombre");
        assertThatThrownBy(() -> HabitoPersonalPedido.de("x".repeat(121), DimensionDelHabito.MENTE, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("120");
        assertThatThrownBy(() -> HabitoPersonalPedido.de("Leer", DimensionDelHabito.MENTE, null, null, "m".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("200");
        assertThat(HabitoPersonalPedido.de("x".repeat(120), DimensionDelHabito.MENTE, null, null, null)).isNotNull();
    }

    @Test
    @DisplayName("sin dimension no hay pedido: la categoria nunca se adivina")
    void sinDimension() {
        assertThatThrownBy(() -> HabitoPersonalPedido.de("Leer", null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
