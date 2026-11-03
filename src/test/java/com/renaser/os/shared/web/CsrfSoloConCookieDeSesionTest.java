package com.renaser.os.shared.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** CSRF se exige solo cuando la sesion llega en la cookie `SESSION`; con la cabecera X-Auth-Token (lo de hoy) no. */
class CsrfSoloConCookieDeSesionTest {

    @Test
    void sinCookiesNoSeExige() {
        var pedido = new MockHttpServletRequest("POST", "/api/v1/habit-tracks/x/complete");
        pedido.addHeader("X-Auth-Token", "abc");
        assertThat(SecurityConfig.traeCookieDeSesion(pedido)).isFalse();
    }

    @Test
    void conOtraCookieNoSeExige() {
        var pedido = new MockHttpServletRequest("POST", "/api/v1/wall");
        pedido.setCookies(new Cookie("AWSALB", "x"));
        assertThat(SecurityConfig.traeCookieDeSesion(pedido)).isFalse();
    }

    @Test
    void conLaCookieDeSesionSeExige() {
        var pedido = new MockHttpServletRequest("POST", "/api/v1/wall");
        pedido.setCookies(new Cookie("AWSALB", "x"), new Cookie("SESSION", "abc"));
        assertThat(SecurityConfig.traeCookieDeSesion(pedido)).isTrue();
    }
}
