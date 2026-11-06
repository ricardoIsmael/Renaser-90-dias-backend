package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.renaser.os.calendar.application.ports.out.meet.AutorizacionGooglePort.Consentimiento;
import com.renaser.os.calendar.application.ports.out.meet.AutorizacionGooglePort.PermisoConcedido;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** El baile de OAuth con Google (spec 040, FR-010) contra un servidor de mentira. */
class GoogleOAuthAdapterTest {

    private GoogleDeMentira g;
    private MockRestServiceServer servidor;
    private GoogleOAuthAdapter adapter;

    @BeforeEach
    void setUp() {
        g = new GoogleDeMentira();
        servidor = g.servidor;
        adapter = new GoogleOAuthAdapter(g.configuracion, g.llamadas, g.tokens);
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: la dirección de consentimiento pide el permiso permanente (offline + consent), usa PKCE y lleva un state; nunca el secreto del cliente")
    void direccionDeConsentimiento() {
        Consentimiento consentimiento = adapter.pedirConsentimiento();

        var parametros = UriComponentsBuilder.fromUriString(consentimiento.url()).build().getQueryParams();
        assertThat(consentimiento.url()).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(parametros.getFirst("access_type")).isEqualTo("offline");
        assertThat(parametros.getFirst("prompt")).isEqualTo("consent");
        assertThat(parametros.getFirst("response_type")).isEqualTo("code");
        assertThat(parametros.getFirst("client_id")).isEqualTo(GoogleDeMentira.CLIENT_ID);
        assertThat(parametros.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(parametros.getFirst("state")).isEqualTo(consentimiento.state());
        assertThat(java.net.URLDecoder.decode(parametros.getFirst("scope"), StandardCharsets.UTF_8))
                .contains("calendar.app.created", "meetings.space.settings", "openid", "email");
        assertThat(java.net.URLDecoder.decode(parametros.getFirst("redirect_uri"), StandardCharsets.UTF_8)).isEqualTo(GoogleDeMentira.REDIRECT_URI);
        assertThat(consentimiento.url()).doesNotContain(GoogleDeMentira.CLIENT_SECRET).doesNotContain(consentimiento.codeVerifier());
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: PKCE: el desafío es el SHA-256 del verificador en base64url, y cada conexión trae un state y un verificador nuevos")
    void pkceYAzar() throws Exception {
        Consentimiento uno = adapter.pedirConsentimiento();
        Consentimiento otro = adapter.pedirConsentimiento();

        String desafio = UriComponentsBuilder.fromUriString(uno.url()).build().getQueryParams().getFirst("code_challenge");
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(uno.codeVerifier().getBytes(StandardCharsets.US_ASCII));
        assertThat(desafio).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(hash));
        assertThat(uno.codeVerifier()).hasSizeBetween(43, 128).matches("[A-Za-z0-9_-]+");
        assertThat(uno.state()).isNotEqualTo(otro.state()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(uno.codeVerifier()).isNotEqualTo(otro.codeVerifier());
    }

    // spec 040 FR-010; spec 047 FR-019 (suma leer las reuniones)
    @Test
    @DisplayName("FR-010: los permisos que tienen que estar todos son los pedidos sin los de identidad")
    void permisosObligatorios() {
        assertThat(adapter.permisosObligatorios()).containsExactlyInAnyOrder(
                "https://www.googleapis.com/auth/calendar.app.created", "https://www.googleapis.com/auth/meetings.space.settings",
                "https://www.googleapis.com/auth/meetings.space.readonly");
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: canjear el código manda el verificador de PKCE y el secreto del cliente, y devuelve el permiso permanente, los permisos concedidos y el correo")
    void canjearElCodigo() {
        String idToken = "eyJhbGciOiJSUzI1NiJ9." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"email\":\"renasercorporation@gmail.com\",\"email_verified\":true}".getBytes(StandardCharsets.UTF_8)) + ".firma";
        servidor.expect(requestTo("https://oauth2.googleapis.com/token")).andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("grant_type", "authorization_code", "code", "el-codigo",
                        "code_verifier", "el-verificador", "redirect_uri", GoogleDeMentira.REDIRECT_URI, "client_id", GoogleDeMentira.CLIENT_ID,
                        "client_secret", GoogleDeMentira.CLIENT_SECRET)))
                .andRespond(withSuccess("""
                        {"access_token":"ya29.x","refresh_token":"%s","expires_in":3599,
                         "scope":"openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/calendar.app.created https://www.googleapis.com/auth/meetings.space.settings",
                         "id_token":"%s"}""".formatted(GoogleDeMentira.REFRESH_TOKEN, idToken), MediaType.APPLICATION_JSON));

        PermisoConcedido permiso = adapter.canjear("el-codigo", "el-verificador");

        assertThat(permiso.refreshToken()).isEqualTo(GoogleDeMentira.REFRESH_TOKEN);
        assertThat(permiso.correo()).isEqualTo("renasercorporation@gmail.com");
        assertThat(permiso.permisosConcedidos()).contains("https://www.googleapis.com/auth/calendar.app.created",
                "https://www.googleapis.com/auth/meetings.space.settings");
        servidor.verify();
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: sin permiso permanente (Google no lo devolvió) o sin saber de qué cuenta es, la conexión se rechaza")
    void sinPermisoPermanenteOSinCuenta() {
        servidor.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"ya29.x\",\"scope\":\"openid\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"ya29.x\",\"refresh_token\":\"r\",\"scope\":\"openid\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.canjear("c", "v")).isInstanceOf(GoogleRechazoException.class).hasMessage("Google no dio el permiso permanente.");
        assertThatThrownBy(() -> adapter.canjear("c", "v")).isInstanceOf(GoogleRechazoException.class).hasMessageContaining("de qué cuenta");
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: revocar anula el permiso en Google; que ya estuviera anulado (400 invalid_token) no es un error, un 5xx sí se reintenta")
    void revocar() {
        servidor.expect(requestTo("https://oauth2.googleapis.com/revoke")).andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("token", GoogleDeMentira.REFRESH_TOKEN)))
                .andRespond(withSuccess());
        servidor.expect(requestTo("https://oauth2.googleapis.com/revoke")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_token\"}"));
        servidor.expect(requestTo("https://oauth2.googleapis.com/revoke")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        adapter.revocar(GoogleDeMentira.REFRESH_TOKEN);
        adapter.revocar(GoogleDeMentira.REFRESH_TOKEN);
        assertThatThrownBy(() -> adapter.revocar(GoogleDeMentira.REFRESH_TOKEN)).isInstanceOf(GoogleNoRespondeException.class);
    }

    // spec 040 FR-034
    @Test
    @DisplayName("FR-034: crear el calendario propio «Renaser 90 días» pide un token con el permiso nuevo y devuelve el id del calendario")
    void crearElCalendarioPropio() {
        servidor.expect(requestTo("https://oauth2.googleapis.com/token")).andExpect(content().formDataContains(
                        java.util.Map.of("grant_type", "refresh_token", "refresh_token", "permiso-nuevo")))
                .andRespond(withSuccess("{\"access_token\":\"ya29.nuevo\",\"expires_in\":3599}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://www.googleapis.com/calendar/v3/calendars")).andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization", "Bearer ya29.nuevo"))
                .andExpect(jsonPath("$.summary").value("Renaser 90 días"))
                .andRespond(withSuccess("{\"id\":\"abc@group.calendar.google.com\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.crearCalendarioPropio("permiso-nuevo", "Renaser 90 días")).isEqualTo("abc@group.calendar.google.com");
        servidor.verify();
    }
}
