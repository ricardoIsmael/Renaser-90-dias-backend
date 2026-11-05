package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.calendar.application.ports.out.meet.AutorizacionGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.infrastructure.adapter.out.google.LlamadasAGoogle.Respuesta;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * El OAuth 2.0 con Google para conectar la cuenta que organiza las salas (spec 040, FR-010): flujo de código con {@code access_type=offline} y
 * {@code prompt=consent} —sin los dos Google no devuelve el permiso permanente—, PKCE y {@code state} propio. El {@code client_secret} vive solo
 * acá. Es lo que hace RenaserOs con su proyecto de Google Cloud, con el nuestro.
 *
 * <p>Nunca se escribe en un registro el código, el {@code state}, el verificador, ni ningún token.
 */
@Component
class GoogleOAuthAdapter implements AutorizacionGooglePort {

    static final String EXTREMO_DE_CONSENTIMIENTO = "https://accounts.google.com/o/oauth2/v2/auth";
    static final URI EXTREMO_DE_REVOCACION = URI.create("https://oauth2.googleapis.com/revoke");
    static final String CALENDAR = "https://www.googleapis.com/calendar/v3";

    private final ConfiguracionDeGoogle configuracion;
    private final LlamadasAGoogle llamadas;
    private final TokenDeAccesoDeGoogle tokens;
    private final SecureRandom azar = new SecureRandom();
    private final ObjectMapper json = new ObjectMapper();

    GoogleOAuthAdapter(ConfiguracionDeGoogle configuracion, LlamadasAGoogle llamadas, TokenDeAccesoDeGoogle tokens) {
        this.configuracion = configuracion;
        this.llamadas = llamadas;
        this.tokens = tokens;
    }

    @Override
    public Set<String> permisosObligatorios() {
        return configuracion.permisosObligatorios();
    }

    @Override
    public Consentimiento pedirConsentimiento() {
        String state = aleatorio();
        String verificador = aleatorio();
        String url = UriComponentsBuilder.fromUriString(EXTREMO_DE_CONSENTIMIENTO)
                .queryParam("client_id", configuracion.clientId())
                .queryParam("redirect_uri", configuracion.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", configuracion.permisosPedidos()))
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .queryParam("code_challenge", desafioDe(verificador))
                .queryParam("code_challenge_method", "S256")
                .encode(StandardCharsets.UTF_8)
                .build().toUriString();
        return new Consentimiento(url, state, verificador);
    }

    @Override
    public PermisoConcedido canjear(String codigo, String codeVerifier) {
        MultiValueMap<String, String> campos = new LinkedMultiValueMap<>();
        campos.add("grant_type", "authorization_code");
        campos.add("code", codigo);
        campos.add("code_verifier", codeVerifier);
        campos.add("redirect_uri", configuracion.redirectUri());
        campos.add("client_id", configuracion.clientId());
        campos.add("client_secret", configuracion.clientSecret());
        Respuesta respuesta = llamadas.formulario(TokenDeAccesoDeGoogle.EXTREMO_DE_TOKENS, campos);
        if (!respuesta.ok()) {
            throw llamadas.falla(respuesta);
        }
        JsonNode cuerpo = respuesta.cuerpo();
        String refreshToken = cuerpo.path("refresh_token").asText("");
        if (refreshToken.isEmpty()) {
            throw new GoogleRechazoException("Google no dio el permiso permanente.", respuesta.codigo());
        }
        String correo = correoDelIdToken(cuerpo.path("id_token").asText(""));
        Set<String> concedidos = new LinkedHashSet<>(Arrays.asList(cuerpo.path("scope").asText("").trim().split("\\s+")));
        return new PermisoConcedido(refreshToken, concedidos, correo);
    }

    @Override
    public void revocar(String refreshToken) {
        MultiValueMap<String, String> campos = new LinkedMultiValueMap<>();
        campos.add("token", refreshToken);
        Respuesta respuesta = llamadas.formulario(EXTREMO_DE_REVOCACION, campos);
        boolean yaEstabaAnulado = respuesta.codigo() == 400 && "invalid_token".equals(respuesta.motivo());
        if (!respuesta.ok() && !yaEstabaAnulado) {
            throw llamadas.falla(respuesta);
        }
    }

    @Override
    public String crearCalendarioPropio(String refreshToken, String nombre) {
        String acceso = tokens.paraUnPermisoNuevo(refreshToken);
        Respuesta respuesta = llamadas.api(HttpMethod.POST, URI.create(CALENDAR + "/calendars"), acceso,
                Map.of("summary", nombre));
        String id = respuesta.cuerpo().path("id").asText("");
        if (!respuesta.ok() || id.isEmpty()) {
            throw respuesta.ok() ? new GoogleRechazoException("Google no devolvió el calendario.", respuesta.codigo()) : llamadas.falla(respuesta);
        }
        return id;
    }

    /**
     * El correo viene en el {@code id_token} que Google entrega por la conexión segura del canje, directo al servidor: no hace falta verificar su
     * firma (OIDC Core 3.1.3.7). Sin correo no se puede saber de qué cuenta es el permiso.
     */
    private String correoDelIdToken(String idToken) {
        try {
            String[] partes = idToken.split("\\.");
            if (partes.length < 2) {
                throw new GoogleRechazoException("Google no dijo de qué cuenta es el permiso.", 0);
            }
            JsonNode datos = json.readTree(Base64.getUrlDecoder().decode(partes[1]));
            String correo = datos.path("email").asText("");
            if (correo.isEmpty()) {
                throw new GoogleRechazoException("Google no dijo de qué cuenta es el permiso.", 0);
            }
            return correo;
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new GoogleRechazoException("Google no dijo de qué cuenta es el permiso.", 0);
        }
    }

    private String aleatorio() {
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String desafioDe(String verificador) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verificador.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", e);
        }
    }
}
