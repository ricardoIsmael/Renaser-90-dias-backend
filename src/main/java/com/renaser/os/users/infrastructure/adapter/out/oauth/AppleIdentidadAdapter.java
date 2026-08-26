package com.renaser.os.users.infrastructure.adapter.out.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Login con Apple (docs/MODULO_AUTH.md §6.3). El proveedor mas particular de los tres:
 *
 * <ul>
 *   <li>El {@code client_secret} NO es un string fijo: es un JWT firmado por nosotros con la
 *       clave privada {@code .p8} de Apple. Se regenera en CADA intercambio de codigo (nunca se
 *       cachea) con una vigencia de solo {@link #VIGENCIA_CLIENT_SECRET} — muy por debajo del
 *       tope de 6 meses que exige Apple. Elegido asi a proposito: cachear el JWT obliga a
 *       rastrear cuando rotarlo; regenerarlo siempre hace que "vencido" sea imposible por
 *       construccion.</li>
 *   <li>Apple manda el nombre del usuario UNA UNICA VEZ, en el primer login, dentro del body que
 *       el navegador le devuelve a la app — no en el ID token. Este adaptador solo ve el
 *       {@code code}/ID token, asi que {@code nombre} queda {@code null} siempre. Limitacion
 *       conocida: si el nombre hace falta, tiene que capturarse del lado de la app en el primer
 *       login y mandarse aparte — no se inventa de donde sacarlo.</li>
 * </ul>
 */
@Component
public class AppleIdentidadAdapter implements VerificadorIdentidadProveedor {

    private static final Logger log = LoggerFactory.getLogger(AppleIdentidadAdapter.class);

    private static final String TOKEN_ENDPOINT = "https://appleid.apple.com/auth/token";
    private static final String ISSUER_ESPERADO = "https://appleid.apple.com";

    /**
     * Muy por debajo del tope de 6 meses que exige Apple (§6.3) — alcanza con que el JWT siga
     * vivo durante el segundo, como mucho, que tarda el intercambio con {@link #TOKEN_ENDPOINT}.
     */
    private static final Duration VIGENCIA_CLIENT_SECRET = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;
    private final String authKeyPath;
    private final String keyId;
    private final String teamId;
    private final String clientId;

    public AppleIdentidadAdapter(RestClient.Builder restClientBuilder,
                                  @Qualifier("appleJwtDecoder") JwtDecoder jwtDecoder,
                                  Clock clock,
                                  @Value("${renaser.auth.apple.auth-key-path}") String authKeyPath,
                                  @Value("${renaser.auth.apple.key-id}") String keyId,
                                  @Value("${renaser.auth.apple.team-id}") String teamId,
                                  @Value("${renaser.auth.apple.client-id}") String clientId) {
        this.restClient = restClientBuilder.build();
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
        this.authKeyPath = authKeyPath;
        this.keyId = keyId;
        this.teamId = teamId;
        this.clientId = clientId;
    }

    @Override
    public ProveedorIdentidad proveedor() {
        return ProveedorIdentidad.APPLE;
    }

    @Override
    public IdentidadVerificada verificar(CanjeCodigoCommand command) {
        AppleTokenResponse token = canjearCodigo(command);
        Jwt idToken = decodificarIdToken(token.idToken());
        return identidadDesde(idToken);
    }

    private AppleTokenResponse canjearCodigo(CanjeCodigoCommand command) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", command.code());
        body.add("code_verifier", command.codeVerifier());
        body.add("redirect_uri", command.redirectUri());
        body.add("client_id", clientId);
        body.add("client_secret", generarClientSecret());
        try {
            AppleTokenResponse respuesta = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(AppleTokenResponse.class);
            if (respuesta == null || respuesta.idToken() == null || respuesta.idToken().isBlank()) {
                throw new IdentidadProveedorInvalidaException("APPLE");
            }
            return respuesta;
        } catch (RestClientException e) {
            log.warn("Apple rechazo el intercambio de codigo", e);
            throw new IdentidadProveedorInvalidaException("APPLE", e);
        }
    }

    private Jwt decodificarIdToken(String idTokenCrudo) {
        Jwt idToken;
        try {
            idToken = jwtDecoder.decode(idTokenCrudo);
        } catch (JwtException e) {
            log.warn("ID token de Apple invalido (firma o vigencia)", e);
            throw new IdentidadProveedorInvalidaException("APPLE", e);
        }
        if (!ISSUER_ESPERADO.equals(idToken.getClaimAsString("iss"))) {
            throw new IdentidadProveedorInvalidaException("APPLE");
        }
        if (idToken.getAudience() == null || !idToken.getAudience().contains(clientId)) {
            throw new IdentidadProveedorInvalidaException("APPLE");
        }
        return idToken;
    }

    private IdentidadVerificada identidadDesde(Jwt idToken) {
        String sujeto = idToken.getSubject();
        if (sujeto == null || sujeto.isBlank()) {
            throw new IdentidadProveedorInvalidaException("APPLE");
        }
        String email = idToken.getClaimAsString("email");
        boolean emailVerificado = Boolean.TRUE.equals(idToken.getClaimAsBoolean("email_verified"));
        // Apple no manda el nombre en el ID token: ver limitacion documentada en la clase.
        return new IdentidadVerificada(sujeto, email, emailVerificado, null);
    }

    /**
     * El {@code client_secret} de Apple: un JWT ES256 firmado con la clave privada del archivo
     * {@code .p8} (§6.3). Package-private para que el test pueda inspeccionar sus claims sin
     * pasar por una llamada HTTP real.
     */
    String generarClientSecret() {
        requireConfigurado();
        ECPrivateKey clavePrivada = leerClavePrivada();
        Instant ahora = clock.now();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(teamId)
                .subject(clientId)
                .audience(ISSUER_ESPERADO)
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plus(VIGENCIA_CLIENT_SECRET)))
                .build();
        SignedJWT signedJwt = new SignedJWT(header, claims);
        try {
            signedJwt.sign(new ECDSASigner(clavePrivada));
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el client_secret de Apple", e);
        }
        return signedJwt.serialize();
    }

    private void requireConfigurado() {
        if (isBlank(authKeyPath) || isBlank(keyId) || isBlank(teamId) || isBlank(clientId)) {
            throw new IllegalStateException("Login con Apple sin configurar: faltan "
                    + "APPLE_AUTH_KEY_PATH/APPLE_KEY_ID/APPLE_TEAM_ID/APPLE_CLIENT_ID");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ECPrivateKey leerClavePrivada() {
        try {
            String pem = Files.readString(Path.of(authKeyPath));
            String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] bytes = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo leer la clave privada de Apple (" + authKeyPath + ")", e);
        }
    }

    /** Solo los campos que este adaptador necesita del token endpoint de Apple. */
    private record AppleTokenResponse(@JsonProperty("id_token") String idToken,
                                       @JsonProperty("access_token") String accessToken,
                                       @JsonProperty("token_type") String tokenType,
                                       @JsonProperty("expires_in") Integer expiresIn) {
    }
}
