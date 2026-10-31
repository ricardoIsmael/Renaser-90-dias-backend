package com.renaser.os;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.TestContext;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El reloj compartido de las pruebas de integración y el oyente que lo deja en hora real (E-649). */
class RelojDePruebaTest {

    private static final Instant MADRUGADA_UTC = Instant.parse("2026-10-06T01:25:00Z");

    @Test
    @DisplayName("sin fijar es el reloj del sistema, igual que SystemClock")
    void sinFijarEsLaHoraReal() {
        RelojDePrueba reloj = new RelojDePrueba();

        assertThat(reloj.estaFijo()).isFalse();
        assertThat(Duration.between(reloj.now(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("fijo, now() no se mueve y today() es la fecha de ese instante en UTC, como el reloj de producción")
    void fijoCongelaLaHoraYElDiaEsElDeUtc() {
        RelojDePrueba reloj = new RelojDePrueba();

        reloj.fijar(MADRUGADA_UTC);

        assertThat(reloj.now()).isEqualTo(MADRUGADA_UTC);
        assertThat(reloj.today()).isEqualTo(LocalDate.of(2026, 10, 6));
        reloj.fijar(Instant.parse("2026-10-06T23:59:59Z"));
        assertThat(reloj.today()).isEqualTo(LocalDate.of(2026, 10, 6));
        reloj.fijar(Instant.parse("2026-10-07T00:00:00Z"));
        assertThat(reloj.today()).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    @DisplayName("avanzar mueve un reloj fijado, hacia adelante o atrás; sin fijar no inventa una hora")
    void avanzarMueveElRelojFijado() {
        RelojDePrueba reloj = new RelojDePrueba();

        assertThatThrownBy(() -> reloj.avanzar(Duration.ofDays(1))).isInstanceOf(IllegalStateException.class);
        reloj.fijar(MADRUGADA_UTC);
        reloj.avanzar(Duration.ofDays(1));
        assertThat(reloj.now()).isEqualTo(MADRUGADA_UTC.plus(Duration.ofDays(1)));
        reloj.avanzar(Duration.ofHours(-2));
        assertThat(reloj.now()).isEqualTo(MADRUGADA_UTC.plus(Duration.ofHours(22)));
    }

    @Test
    @DisplayName("restablecer vuelve a la hora real")
    void restablecerVuelveALaHoraReal() {
        RelojDePrueba reloj = new RelojDePrueba();
        reloj.fijar(MADRUGADA_UTC);

        reloj.restablecer();

        assertThat(reloj.estaFijo()).isFalse();
        assertThat(Duration.between(reloj.now(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("el oyente restablece el reloj del contexto al terminar cada prueba, aunque la prueba lo dejara fijo")
    void elOyenteRestableceElRelojDelContexto() {
        RelojDePrueba reloj = new RelojDePrueba();
        reloj.fijar(MADRUGADA_UTC);
        GenericApplicationContext contexto = new GenericApplicationContext();
        contexto.registerBean(RelojDePrueba.class, () -> reloj);
        contexto.refresh();
        TestContext prueba = mock(TestContext.class);
        when(prueba.hasApplicationContext()).thenReturn(true);
        when(prueba.getApplicationContext()).thenReturn(contexto);

        new RestablecerRelojDePrueba().afterTestMethod(prueba);

        assertThat(reloj.estaFijo()).isFalse();
    }

    @Test
    @DisplayName("el oyente no carga un contexto que la prueba no usó")
    void elOyenteNoForzaLaCargaDelContexto() {
        TestContext prueba = mock(TestContext.class);
        when(prueba.hasApplicationContext()).thenReturn(false);

        new RestablecerRelojDePrueba().afterTestMethod(prueba);

        org.mockito.Mockito.verify(prueba, org.mockito.Mockito.never()).getApplicationContext();
    }

    @Test
    @DisplayName("un contexto sin RelojDePrueba (el oyente corre en toda prueba de Spring) no falla")
    void elOyenteToleraUnContextoSinElReloj() {
        GenericApplicationContext contexto = new GenericApplicationContext();
        contexto.refresh();
        TestContext prueba = mock(TestContext.class);
        when(prueba.hasApplicationContext()).thenReturn(true);
        when(prueba.getApplicationContext()).thenReturn(contexto);

        new RestablecerRelojDePrueba().afterTestMethod(prueba);
    }
}
