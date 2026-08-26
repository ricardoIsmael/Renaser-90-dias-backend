package com.renaser.os.users.infrastructure.adapter.out.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Login con Facebook (docs/MODULO_AUTH.md §6.3). A diferencia de Google/Apple, Facebook usa
 * OAuth 2.0 estandar, NO OIDC: no hay ID token firmado que verificar. El flujo es dos llamadas
 * REST directas contra la Graph API:
 *
 * <ol>
 *   <li>canjear el {@code code} por un {@code access_token}</li>
 *   <li>usar ese token para pedir {@code id,email,name} en {@code /me}</li>
 * </ol>
 *
 * <p><b>Bloqueante de negocio, no de codigo:</b> Meta exige revision de app y verificacion de
 * negocio antes de aceptar llamadas de produccion contra estas rutas. Este adaptador puede estar
 * listo y compilar sin que el proveedor real acepte todavia las llamadas — el tramite hay que
 * iniciarlo aparte (ver docs/MODULO_AUTH.md §6.3 y la pregunta abierta en la fase 6).
 */
@Component
public class FacebookIdentidadAdapter implements VerificadorIdentidadProveedor {

    private static final Logger log = LoggerFactory.getLogger(FacebookIdentidadAdapter.class);

    /**
     * Version de la Graph API. Meta deprecha versiones periodicamente (aprox. cada 2 años) —
     * si el login empieza a fallar con "Unsupported get request", la primera hipotesis es que
     * esta version ya vencio, no un bug de este adaptador.
     */
    private static final String GRAPH_API_VERSION = "v21.0";
    private static final String GRAPH_BASE_URL = "https://graph.facebook.com/" + GRAPH_API_VERSION;

    private final RestClient restClient;
    private final String appId;
    private final String appSecret;

    public FacebookIdentidadAdapter(RestClient.Builder restClientBuilder,
                                     @Value("${renaser.auth.facebook.app-id}") String appId,
                                     @Value("${renaser.auth.facebook.app-secret}") String appSecret) {
        this.restClient = restClientBuilder.build();
        this.appId = appId;
        this.appSecret = appSecret;
    }

    @Override
    public ProveedorIdentidad proveedor() {
        return ProveedorIdentidad.FACEBOOK;
    }

    @Override
    public IdentidadVerificada verificar(CanjeCodigoCommand command) {
        requireConfigurado();
        String accessToken = canjearCodigo(command);
        FacebookUserInfo perfil = obtenerPerfil(accessToken);
        return identidadDesde(perfil);
    }

    private String canjearCodigo(CanjeCodigoCommand command) {
        URI uri = UriComponentsBuilder.fromUriString(GRAPH_BASE_URL + "/oauth/access_token")
                .queryParam("client_id", appId)
                .queryParam("client_secret", appSecret)
                .queryParam("redirect_uri", command.redirectUri())
                .queryParam("code", command.code())
                .queryParam("code_verifier", command.codeVerifier())
                .encode()
                .build()
                .toUri();
        try {
            FacebookTokenResponse respuesta = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(FacebookTokenResponse.class);
            if (respuesta == null || respuesta.accessToken() == null || respuesta.accessToken().isBlank()) {
                throw new IdentidadProveedorInvalidaException("FACEBOOK");
            }
            return respuesta.accessToken();
        } catch (RestClientException e) {
            log.warn("Facebook rechazo el intercambio de codigo", e);
            throw new IdentidadProveedorInvalidaException("FACEBOOK", e);
        }
    }

    private FacebookUserInfo obtenerPerfil(String accessToken) {
        URI uri = UriComponentsBuilder.fromUriString(GRAPH_BASE_URL + "/me")
                .queryParam("fields", "id,email,name")
                .queryParam("access_token", accessToken)
                .encode()
                .build()
                .toUri();
        try {
            FacebookUserInfo perfil = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(FacebookUserInfo.class);
            if (perfil == null || perfil.id() == null || perfil.id().isBlank()) {
                throw new IdentidadProveedorInvalidaException("FACEBOOK");
            }
            return perfil;
        } catch (RestClientException e) {
            log.warn("Facebook rechazo la lectura de perfil", e);
            throw new IdentidadProveedorInvalidaException("FACEBOOK", e);
        }
    }

    private IdentidadVerificada identidadDesde(FacebookUserInfo perfil) {
        // La Graph API no expone un flag "verificado" en /me: se asume que un email que Facebook
        // devuelve ya pertenece a una cuenta verificada por Meta (documentado, no confirmado
        // contra la API real — sin credenciales para probarlo, ver reporte final).
        boolean emailVerificado = perfil.email() != null && !perfil.email().isBlank();
        return new IdentidadVerificada(perfil.id(), perfil.email(), emailVerificado, perfil.name());
    }

    private void requireConfigurado() {
        if (isBlank(appId) || isBlank(appSecret)) {
            throw new IllegalStateException("Login con Facebook sin configurar: faltan "
                    + "FACEBOOK_APP_ID/FACEBOOK_APP_SECRET");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record FacebookTokenResponse(@JsonProperty("access_token") String accessToken,
                                          @JsonProperty("token_type") String tokenType,
                                          @JsonProperty("expires_in") Integer expiresIn) {
    }

    private record FacebookUserInfo(String id, String email, String name) {
    }
}
