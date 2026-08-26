package com.renaser.os.users.infrastructure.adapter.out.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
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

/**
 * Login con Google (docs/MODULO_AUTH.md §6.3). El mas simple de los tres proveedores: OIDC
 * estandar, {@code client_secret} fijo (a diferencia de Apple, que lo firma en cada intercambio),
 * y sin las particularidades de Facebook (que no es OIDC y no tiene ID token).
 *
 * <p>Flujo Authorization Code + PKCE: el {@code code} y el {@code code_verifier} que manda la app
 * se canjean contra {@value #TOKEN_ENDPOINT} junto con el {@code client_secret} — que vive SOLO
 * aca, nunca en el cliente movil (docs/MODULO_AUTH.md §6.1). El ID token que devuelve Google se
 * verifica contra el JWKS de Google ({@code iss}/{@code aud}/firma/vigencia) antes de confiar en
 * cualquiera de sus claims.
 */
@Component
public class GoogleIdentidadAdapter implements VerificadorIdentidadProveedor {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdentidadAdapter.class);

    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String ISSUER_ESPERADO = "https://accounts.google.com";

    private final RestClient restClient;
    private final JwtDecoder jwtDecoder;
    private final String clientId;
    private final String clientSecret;

    public GoogleIdentidadAdapter(RestClient.Builder restClientBuilder,
                                   @Qualifier("googleJwtDecoder") JwtDecoder jwtDecoder,
                                   @Value("${renaser.auth.google.client-id}") String clientId,
                                   @Value("${renaser.auth.google.client-secret}") String clientSecret) {
        this.restClient = restClientBuilder.build();
        this.jwtDecoder = jwtDecoder;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public ProveedorIdentidad proveedor() {
        return ProveedorIdentidad.GOOGLE;
    }

    @Override
    public IdentidadVerificada verificar(CanjeCodigoCommand command) {
        requireConfigurado();
        GoogleTokenResponse token = canjearCodigo(command);
        Jwt idToken = decodificarIdToken(token.idToken());
        return identidadDesde(idToken);
    }

    private GoogleTokenResponse canjearCodigo(CanjeCodigoCommand command) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", command.code());
        body.add("code_verifier", command.codeVerifier());
        body.add("redirect_uri", command.redirectUri());
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        try {
            GoogleTokenResponse respuesta = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
            if (respuesta == null || respuesta.idToken() == null || respuesta.idToken().isBlank()) {
                throw new IdentidadProveedorInvalidaException("GOOGLE");
            }
            return respuesta;
        } catch (RestClientException e) {
            // Nunca se loguea el `code` ni el `client_secret`: regla dura del modulo (§0). El
            // mensaje de la excepcion de Google (invalid_grant, code expirado, etc.) puede
            // logearse — no contiene ninguno de los dos.
            log.warn("Google rechazo el intercambio de codigo", e);
            throw new IdentidadProveedorInvalidaException("GOOGLE", e);
        }
    }

    private Jwt decodificarIdToken(String idTokenCrudo) {
        Jwt idToken;
        try {
            idToken = jwtDecoder.decode(idTokenCrudo);
        } catch (JwtException e) {
            log.warn("ID token de Google invalido (firma o vigencia)", e);
            throw new IdentidadProveedorInvalidaException("GOOGLE", e);
        }
        if (!ISSUER_ESPERADO.equals(idToken.getClaimAsString("iss"))) {
            throw new IdentidadProveedorInvalidaException("GOOGLE");
        }
        if (idToken.getAudience() == null || !idToken.getAudience().contains(clientId)) {
            throw new IdentidadProveedorInvalidaException("GOOGLE");
        }
        return idToken;
    }

    private static IdentidadVerificada identidadDesde(Jwt idToken) {
        String sujeto = idToken.getSubject();
        if (sujeto == null || sujeto.isBlank()) {
            throw new IdentidadProveedorInvalidaException("GOOGLE");
        }
        String email = idToken.getClaimAsString("email");
        boolean emailVerificado = Boolean.TRUE.equals(idToken.getClaimAsBoolean("email_verified"));
        String nombre = idToken.getClaimAsString("name");
        return new IdentidadVerificada(sujeto, email, emailVerificado, nombre);
    }

    private void requireConfigurado() {
        if (isBlank(clientId) || isBlank(clientSecret)) {
            throw new IllegalStateException(
                    "Login con Google sin configurar: faltan GOOGLE_OAUTH_CLIENT_ID/GOOGLE_OAUTH_CLIENT_SECRET");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Solo los campos que este adaptador necesita del token endpoint de Google. */
    private record GoogleTokenResponse(@JsonProperty("id_token") String idToken,
                                        @JsonProperty("access_token") String accessToken,
                                        @JsonProperty("token_type") String tokenType,
                                        @JsonProperty("expires_in") Integer expiresIn) {
    }
}
