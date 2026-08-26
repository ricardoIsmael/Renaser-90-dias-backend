package com.renaser.os.users.infrastructure.adapter.out.oauth;

import com.nimbusds.jwt.SignedJWT;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class AppleIdentidadAdapterTest {

    private static final String TEAM_ID = "TEAMID1234";
    private static final String KEY_ID = "KEYID56789";
    private static final String CLIENT_ID = "dev.renaser.app.services";
    private static final Instant AHORA = Instant.parse("2026-08-26T12:00:00Z");

    @Mock
    private JwtDecoder jwtDecoder;

    private Path clavePrivadaPem;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        clavePrivadaPem = tempDir.resolve("AuthKey_TEST.p8");
        Files.writeString(clavePrivadaPem, generarClavePrivadaEcComoPem());
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    private AppleIdentidadAdapter adapter() {
        return adapter(clavePrivadaPem.toString(), KEY_ID, TEAM_ID, CLIENT_ID);
    }

    private AppleIdentidadAdapter adapter(String authKeyPath, String keyId, String teamId, String clientId) {
        Clock clockFijo = new Clock() {
            @Override
            public Instant now() {
                return AHORA;
            }

            @Override
            public LocalDate today() {
                return AHORA.atZone(java.time.ZoneOffset.UTC).toLocalDate();
            }
        };
        return new AppleIdentidadAdapter(restClientBuilder, jwtDecoder, clockFijo, authKeyPath, keyId, teamId,
                clientId);
    }

    @Test
    void declaraElProveedorApple() {
        assertThat(adapter().proveedor()).isEqualTo(ProveedorIdentidad.APPLE);
    }

    @Test
    void elClientSecretEsUnJwtEs256FirmadoConLosClaimsDeApple() throws Exception {
        String clientSecret = adapter().generarClientSecret();

        SignedJWT jwt = SignedJWT.parse(clientSecret);
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(TEAM_ID);
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
    }

    @Test
    void elClientSecretNuncaExpiraMasAllaDeSeisMeses() throws Exception {
        String clientSecret = adapter().generarClientSecret();

        SignedJWT jwt = SignedJWT.parse(clientSecret);
        Instant expiracion = jwt.getJWTClaimsSet().getExpirationTime().toInstant();
        Instant emision = jwt.getJWTClaimsSet().getIssueTime().toInstant();

        assertThat(expiracion).isAfter(emision);
        assertThat(expiracion).isBefore(AHORA.plus(java.time.Duration.ofDays(180)));
    }

    @Test
    void generarClientSecretFallaSiFaltaConfiguracion() {
        AppleIdentidadAdapter sinConfigurar = adapter(clavePrivadaPem.toString(), "", TEAM_ID, CLIENT_ID);

        assertThatThrownBy(sinConfigurar::generarClientSecret).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generarClientSecretFallaSiLaClavePrivadaNoExiste() {
        AppleIdentidadAdapter clavePerdida = adapter("/ruta/que/no/existe.p8", KEY_ID, TEAM_ID, CLIENT_ID);

        assertThatThrownBy(clavePerdida::generarClientSecret).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void verificarCanjeaElCodeYDevuelveLaIdentidadDelIdToken() {
        mockServer.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"id_token":"un-id-token-cualquiera","access_token":"at","token_type":"bearer"}
                        """, MediaType.APPLICATION_JSON));
        when(jwtDecoder.decode(anyString())).thenReturn(jwtDeApple("apple-sub-1", "persona@icloud.com", true));

        IdentidadVerificada identidad = adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback"));

        assertThat(identidad.sujeto()).isEqualTo("apple-sub-1");
        assertThat(identidad.email()).isEqualTo("persona@icloud.com");
        assertThat(identidad.emailVerificado()).isTrue();
        // Apple no manda el nombre en el ID token (limitacion documentada en la clase).
        assertThat(identidad.nombre()).isNull();
        mockServer.verify();
    }

    @Test
    void verificarRechazaSiElIssuerDelIdTokenNoEsApple() {
        mockServer.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withSuccess("{\"id_token\":\"x\"}", MediaType.APPLICATION_JSON));
        Jwt jwtConIssuerAjeno = Jwt.withTokenValue("x")
                .header("alg", "RS256")
                .claim("iss", "https://otro-emisor.example")
                .claim("sub", "apple-sub-1")
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
        mockServer.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withSuccess("{\"id_token\":\"x\"}", MediaType.APPLICATION_JSON));
        Jwt jwtConAudienceAjeno = Jwt.withTokenValue("x")
                .header("alg", "RS256")
                .claim("iss", "https://appleid.apple.com")
                .claim("sub", "apple-sub-1")
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
        mockServer.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withSuccess("{\"id_token\":\"x\"}", MediaType.APPLICATION_JSON));
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("firma invalida"));

        assertThatThrownBy(() -> adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);
    }

    @Test
    void verificarRechazaSiAppleRespondeError() {
        mockServer.expect(requestTo("https://appleid.apple.com/auth/token"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter().verificar(
                new CanjeCodigoCommand("un-code", "un-verifier", "https://app.renaser.dev/callback")))
                .isInstanceOf(IdentidadProveedorInvalidaException.class);
    }

    private static Jwt jwtDeApple(String sujeto, String email, boolean emailVerificado) {
        return Jwt.withTokenValue("x")
                .header("alg", "RS256")
                .claim("iss", "https://appleid.apple.com")
                .claim("sub", sujeto)
                .audience(List.of(CLIENT_ID))
                .claim("email", email)
                .claim("email_verified", emailVerificado)
                .issuedAt(AHORA)
                .expiresAt(AHORA.plusSeconds(600))
                .build();
    }

    /** Genera una clave privada EC (P-256) de prueba en formato PEM/PKCS8, como el .p8 de Apple. */
    private static String generarClavePrivadaEcComoPem()
            throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IOException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        String base64 = Base64.getMimeEncoder(64, System.lineSeparator().getBytes())
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----" + System.lineSeparator() + base64 + System.lineSeparator()
                + "-----END PRIVATE KEY-----" + System.lineSeparator();
    }
}
