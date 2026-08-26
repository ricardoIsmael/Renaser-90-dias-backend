package com.renaser.os.users.infrastructure.adapter.out.oauth;

import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class GoogleIdentidadAdapterTest {

    private static final String CLIENT_ID = "dev.renaser.app.googleusercontent.com";
    private static final String CLIENT_SECRET = "un-client-secret-cualquiera";
    private static final Instant AHORA = Instant.parse("2026-08-26T12:00:00Z");

    @Mock
    private JwtDecoder jwtDecoder;

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    private GoogleIdentidadAdapter adapter() {
        return adapter(CLIENT_ID, CLIENT_SECRET);
    }

    private GoogleIdentidadAdapter adapter(String clientId, String clientSecret) {
        return new GoogleIdentidadAdapter(restClientBuilder, jwtDecoder, clientId, clientSecret);
    }

    @Test
    void declaraElProveedorGoogle() {
        assertThat(adapter().proveedor()).isEqualTo(ProveedorIdentidad.GOOGLE);
    }

    @Test
    void verificarFallaSiFaltaConfiguracion() {
        GoogleIdentidadAdapter sinConfigurar = adapter("", CLIENT_SECRET);

        assertThatThrownBy(() -> sinConfigurar.verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void verificarCanjeaElCodeYDevuelveLaIdentidadDelIdToken() {
        mockServer.expect(requestTo(GoogleIdentidadAdapter.TOKEN_ENDPOINT))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"id_token":"un-id-token-cualquiera","access_token":"at","token_type":"bearer"}
                        """, MediaType.APPLICATION_JSON));
        when(jwtDecoder.decode(anyString()))
                .thenReturn(jwtDeGoogle("google-sub-1", "persona@gmail.com", true, "Persona de Prueba"));

        IdentidadVerificada identidad = adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback"));

        assertThat(identidad.sujeto()).isEqualTo("google-sub-1");
        assertThat(identidad.email()).isEqualTo("persona@gmail.com");
        assertThat(identidad.emailVerificado()).isTrue();
        assertThat(identidad.nombre()).isEqualTo("Persona de Prueba");
        mockServer.verify();
    }

    @Test
    void verificarRechazaSiElIssuerDelIdTokenNoEsGoogle() {
        mockServer.expect(requestTo(GoogleIdentidadAdapter.TOKEN_ENDPOINT))
                .andRespond(withSuccess("{\"id_token\":\"x\"}", MediaType.APPLICATION_JSON));
        Jwt jwtConIssuerAjeno = Jwt.withTokenValue("x")
                .header("alg", "RS256")
                .claim("iss", "https://otro-emisor.example")
                .claim("sub", "google-sub-1")
                .audience(List.of(CLIENT_ID))
                .issuedAt(AHORA)
                .expiresAt(AHORA.plusSeconds(600))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwtConIssuerAjeno);

        assertThatThrownBy(() -> adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);
    }

    @Test
    void verificarRechazaSiElAudienceDelIdTokenNoCoincideConElClientId() {
        mockServer.expect(requestTo(GoogleIdentidadAdapter.TOKEN_ENDPOINT))
                .andRespond(withSuccess("{\"id_token\":\"x\"}", MediaType.APPLICATION_JSON));
        Jwt jwtConAudienceAjeno = Jwt.withTokenValue("x")
                .header("alg", "RS256")
                .claim("iss", "https://accounts.google.com")
                .claim("sub", "google-sub-1")
                .audience(List.of("otra-app-cualquiera"))
                .issuedAt(AHORA)
                .expiresAt(AHORA.plusSeconds(600))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwtConAudienceAjeno);

        assertThatThrownBy(() -> adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);
    }

    @Test
    void verificarRechazaSiElIdTokenNoVerifica() {
        mockServer.expect(requestTo(GoogleIdentidadAdapter.TOKEN_ENDPOINT))
                .andRespond(withSuccess("{\"id_token\":\"x\"}", MediaType.APPLICATION_JSON));
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("firma invalida"));

        assertThatThrownBy(() -> adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);
    }

    @Test
    void verificarRechazaSiGoogleRespondeError() {
        mockServer.expect(requestTo(GoogleIdentidadAdapter.TOKEN_ENDPOINT))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);
    }

    @Test
    void verificarRechazaSiElSubjectDelIdTokenEstaVacio() {
        mockServer.expect(requestTo(GoogleIdentidadAdapter.TOKEN_ENDPOINT))
                .andRespond(withSuccess("{\"id_token\":\"x\"}", MediaType.APPLICATION_JSON));
        Jwt jwtSinSujeto = Jwt.withTokenValue("x")
                .header("alg", "RS256")
                .claim("iss", "https://accounts.google.com")
                .claim("sub", " ")
                .audience(List.of(CLIENT_ID))
                .issuedAt(AHORA)
                .expiresAt(AHORA.plusSeconds(600))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwtSinSujeto);

        assertThatThrownBy(() -> adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);
    }

    private static Jwt jwtDeGoogle(String sujeto, String email, boolean emailVerificado, String nombre) {
        return Jwt.withTokenValue("x")
                .header("alg", "RS256")
                .claim("iss", "https://accounts.google.com")
                .claim("sub", sujeto)
                .audience(List.of(CLIENT_ID))
                .claim("email", email)
                .claim("email_verified", emailVerificado)
                .claim("name", nombre)
                .issuedAt(AHORA)
                .expiresAt(AHORA.plusSeconds(600))
                .build();
    }
}
