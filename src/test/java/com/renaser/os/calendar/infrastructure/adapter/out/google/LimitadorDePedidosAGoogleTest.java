package com.renaser.os.calendar.infrastructure.adapter.out.google;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorDePedidosAGoogleTest {

    // spec 040 FR-013
    @Test
    @DisplayName("FR-013: el límite propio corta los pedidos por encima del tope por minuto, y vuelve a dejar pasar al terminar la ventana")
    void cortaPorEncimaDelTopeYSeRecupera() {
        GoogleDeMentira.Reloj reloj = new GoogleDeMentira.Reloj();
        LimitadorDePedidosAGoogle limitador = new LimitadorDePedidosAGoogle(reloj, 30);

        for (int i = 0; i < 30; i++) {
            assertThat(limitador.intentar()).as("pedido %d", i + 1).isTrue();
        }
        assertThat(limitador.intentar()).as("el 31 no sale").isFalse();

        reloj.ahora = reloj.ahora.plus(Duration.ofSeconds(59));
        assertThat(limitador.intentar()).isFalse();

        reloj.ahora = reloj.ahora.plus(Duration.ofSeconds(2));
        assertThat(limitador.intentar()).isTrue();
    }

    // spec 040 FR-013
    @Test
    @DisplayName("FR-013: el tope de 30 por minuto es mucho más estricto que el de Google (600 por minuto)")
    void masEstrictoQueGoogle() {
        GoogleDeMentira.Reloj reloj = new GoogleDeMentira.Reloj();
        LimitadorDePedidosAGoogle limitador = new LimitadorDePedidosAGoogle(reloj, 30);

        long salieron = java.util.stream.IntStream.range(0, 600).filter(i -> limitador.intentar()).count();

        assertThat(salieron).isEqualTo(30).isLessThan(600);
    }
}
