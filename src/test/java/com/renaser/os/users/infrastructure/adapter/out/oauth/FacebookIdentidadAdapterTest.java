package com.renaser.os.users.infrastructure.adapter.out.oauth;

import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacebookIdentidadAdapterTest {

    private static final String APP_ID = "fb-app-id-123";
    private static final String APP_SECRET = "fb-app-secret-456";
    private static final String TOKEN_URL_PREFIX = "https://graph.facebook.com/v21.0/oauth/access_token";
    private static final String ME_URL_PREFIX = "https://graph.facebook.com/v21.0/me";

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    private FacebookIdentidadAdapter adapter() {
        return adapter(APP_ID, APP_SECRET);
    }

    private FacebookIdentidadAdapter adapter(String appId, String appSecret) {
        return new FacebookIdentidadAdapter(restClientBuilder, appId, appSecret);
    }

    @Test
    void declaraElProveedorFacebook() {
        assertThat(adapter().proveedor()).isEqualTo(ProveedorIdentidad.FACEBOOK);
    }

    @Test
    void verificarCanjeaElCodeYLuegoLeeElPerfil() {
        mockServer.expect(requestTo(startsWith(TOKEN_URL_PREFIX)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("client_id", APP_ID))
                .andExpect(queryParam("client_secret", APP_SECRET))
                .andExpect(queryParam("code", "un-code"))
                .andExpect(queryParam("code_verifier", "un-verifier"))
                .andRespond(withSuccess("""
                        {"access_token":"un-access-token","token_type":"bearer","expires_in":5184000}
                        """, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(startsWith(ME_URL_PREFIX)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("access_token", "un-access-token"))
                .andRespond(withSuccess("""
                        {"id":"fb-sujeto-1","email":"persona@example.com","name":"Persona de Prueba"}
                        """, MediaType.APPLICATION_JSON));

        IdentidadVerificada identidad = adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback"));

        assertThat(identidad.sujeto()).isEqualTo("fb-sujeto-1");
        assertThat(identidad.email()).isEqualTo("persona@example.com");
        assertThat(identidad.emailVerificado()).isTrue();
        assertThat(identidad.nombre()).isEqualTo("Persona de Prueba");
        mockServer.verify();
    }

    @Test
    void verificarSinEmailDevuelveEmailVerificadoFalse() {
        mockServer.expect(requestTo(startsWith(TOKEN_URL_PREFIX)))
                .andRespond(withSuccess("{\"access_token\":\"un-access-token\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(startsWith(ME_URL_PREFIX)))
                .andRespond(withSuccess("{\"id\":\"fb-sujeto-2\",\"name\":\"Sin Email\"}", MediaType.APPLICATION_JSON));

        IdentidadVerificada identidad = adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback"));

        assertThat(identidad.email()).isNull();
        assertThat(identidad.emailVerificado()).isFalse();
    }

    @Test
    void verificarRechazaSiFacebookRespondeErrorEnElCanje() {
        mockServer.expect(requestTo(startsWith(TOKEN_URL_PREFIX))).andRespond(withServerError());

        assertThatThrownBy(() -> adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);
    }

    @Test
    void verificarRechazaSiFacebookRespondeErrorAlLeerElPerfil() {
        mockServer.expect(requestTo(startsWith(TOKEN_URL_PREFIX)))
                .andRespond(withSuccess("{\"access_token\":\"un-access-token\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(startsWith(ME_URL_PREFIX))).andRespond(withServerError());

        assertThatThrownBy(() -> adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);
    }

    @Test
    void verificarFallaSiFaltaConfiguracion() {
        FacebookIdentidadAdapter sinConfigurar = adapter("", APP_SECRET);

        assertThatThrownBy(() -> sinConfigurar.verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IllegalStateException.class);
    }
}
