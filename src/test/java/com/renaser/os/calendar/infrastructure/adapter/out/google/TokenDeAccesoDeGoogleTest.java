package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleNoConectadaException;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.infrastructure.adapter.out.google.TokenDeAccesoDeGoogle.Credencial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TokenDeAccesoDeGoogleTest {

    private GoogleDeMentira g;

    @BeforeEach
    void setUp() {
        g = new GoogleDeMentira();
    }

    private void respuestaDeToken(String token) {
        g.servidor.expect(requestTo("https://oauth2.googleapis.com/token")).andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("grant_type", "refresh_token", "refresh_token", GoogleDeMentira.REFRESH_TOKEN,
                        "client_id", GoogleDeMentira.CLIENT_ID, "client_secret", GoogleDeMentira.CLIENT_SECRET)))
                .andRespond(withSuccess("{\"access_token\":\"" + token + "\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
    }

    // spec 040 FR-011
    @Test
    @DisplayName("FR-011: el token de acceso se pide con el permiso permanente solo cuando hace falta y vive en memoria hasta que vence")
    void elTokenSePideSoloCuandoHaceFalta() {
        respuestaDeToken("token-1");

        Credencial uno = g.tokens.obtener();
        Credencial dos = g.tokens.obtener();

        assertThat(uno.tokenDeAcceso()).isEqualTo("token-1");
        assertThat(dos.tokenDeAcceso()).isEqualTo("token-1");
        assertThat(uno.calendarioId()).isEqualTo(GoogleDeMentira.CALENDARIO);
        g.servidor.verify();
    }

    // spec 040 FR-011
    @Test
    @DisplayName("FR-011: pasada la hora, el token se renueva")
    void elTokenSeRenuevaAlVencer() {
        respuestaDeToken("token-1");
        g.tokens.obtener();

        g.reloj.ahora = g.reloj.ahora.plus(Duration.ofMinutes(59));
        g.servidor.reset();
        respuestaDeToken("token-2");

        assertThat(g.tokens.obtener().tokenDeAcceso()).isEqualTo("token-2");
        g.servidor.verify();
    }

    // spec 040 FR-011
    @Test
    @DisplayName("FR-011: si la cuenta se reconecta con otro permiso, el token de la cuenta anterior ya no sirve")
    void unPermisoNuevoDescartaElToken() {
        respuestaDeToken("token-1");
        g.tokens.obtener();
        g.servidor.reset();

        var otra = com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle.conectar("renasercorporation@gmail.com", "otro-permiso-permanente",
                java.util.Set.of(), com.renaser.os.shared.domain.UserId.of(java.util.UUID.randomUUID()), g.reloj.now());
        g.cuenta.actual = otra;
        g.servidor.expect(requestTo("https://oauth2.googleapis.com/token")).andExpect(content().formDataContains(
                        java.util.Map.of("refresh_token", "otro-permiso-permanente")))
                .andRespond(withSuccess("{\"access_token\":\"token-nuevo\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));

        assertThat(g.tokens.obtener().tokenDeAcceso()).isEqualTo("token-nuevo");
        assertThat(g.tokens.obtener().calendarioId()).as("sin calendario propio: el principal").isEqualTo("primary");
    }

    // spec 040 FR-009
    @Test
    @DisplayName("FR-009: invalid_grant = el permiso se venció o se revocó: cuenta revocada, sin reintentos")
    void invalidGrant() {
        g.servidor.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}"));

        assertThatThrownBy(() -> g.tokens.obtener()).isInstanceOf(CuentaGoogleRevocadaException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(GoogleDeMentira.REFRESH_TOKEN));
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: Google caído al renovar el token es un fallo pasajero, no una cuenta revocada")
    void googleCaidoAlRenovar() {
        g.servidor.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> g.tokens.obtener()).isInstanceOf(GoogleNoRespondeException.class);
        assertThat(g.cuenta.actual.activa()).isTrue();
    }

    // spec 040 FR-009
    @Test
    @DisplayName("FR-009: sin cuenta no hay token")
    void sinCuenta() {
        g.cuenta.actual = null;

        assertThatThrownBy(() -> g.tokens.obtener()).isInstanceOf(CuentaGoogleNoConectadaException.class);
    }
}
