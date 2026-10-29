package com.renaser.os;

import com.renaser.os.shared.domain.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El reloj compartido de las pruebas de integración dentro de un contexto real de Spring (E-649): es
 * el {@code Clock} que usa la aplicación, y la hora que una prueba fija no llega a la siguiente.
 *
 * <p>Las dos pruebas dependen de su orden A PROPÓSITO: la segunda comprueba que lo que dejó la primera
 * fue restablecido por {@link RestablecerRelojDePrueba}, que es lo único que evita que un reloj
 * movido contamine a otra clase en un contexto compartido.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RelojDePruebaCompartidoIT {

    private static final Instant UN_DIA_DEL_PASADO = Instant.parse("2020-02-29T03:00:00Z");

    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private Clock relojDeLaAplicacion;

    @Test
    @Order(1)
    @DisplayName("el Clock de la aplicación es el reloj de prueba: lo que se fija se ve en todo el contexto")
    void elClockDeLaAplicacionEsElDePrueba() {
        reloj.fijar(UN_DIA_DEL_PASADO);

        assertThat(relojDeLaAplicacion).isSameAs(reloj);
        assertThat(relojDeLaAplicacion.now()).isEqualTo(UN_DIA_DEL_PASADO);
    }

    @Test
    @Order(2)
    @DisplayName("la prueba siguiente ve la hora real: el oyente restableció el reloj que dejó la anterior")
    void laPruebaSiguienteVeLaHoraReal() {
        assertThat(reloj.estaFijo()).isFalse();
        assertThat(Duration.between(relojDeLaAplicacion.now(), Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }
}
