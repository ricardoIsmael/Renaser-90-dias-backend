package com.renaser.os.calendar.infrastructure.adapter.out.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Las llamadas HTTP a Google, con lo común a todas (spec 040): el límite propio de pedidos, el tiempo máximo, y traducir un fallo a una de las
 * excepciones del puerto SIN el cuerpo de lo que respondió Google —que puede traer datos— ni los tokens. Un fallo de red es {@code 0}.
 */
@Component
class LlamadasAGoogle {

    /** Lo que respondió Google: el código HTTP y, si lo hubo, el cuerpo JSON. */
    record Respuesta(int codigo, JsonNode cuerpo) {

        boolean ok() {
            return codigo >= 200 && codigo < 300;
        }

        /** El motivo corto de un error de Google ({@code rateLimitExceeded}, {@code invalid_grant}…), o vacío. */
        String motivo() {
            JsonNode error = cuerpo.path("error");
            if (error.isTextual()) {
                return error.asText();
            }
            String razon = error.path("errors").path(0).path("reason").asText("");
            return razon.isEmpty() ? error.path("status").asText("") : razon;
        }
    }

    private static final Set<String> MOTIVOS_DE_CUOTA = Set.of("rateLimitExceeded", "userRateLimitExceeded", "quotaExceeded",
            "dailyLimitExceeded", "RESOURCE_EXHAUSTED");

    private final RestClient cliente;
    private final LimitadorDePedidosAGoogle limitador;
    private final ObjectMapper json = new ObjectMapper();

    LlamadasAGoogle(@Qualifier(ClienteDeGoogleConfig.NOMBRE) RestClient cliente, LimitadorDePedidosAGoogle limitador) {
        this.cliente = cliente;
        this.limitador = limitador;
    }

    /** Una llamada a la Calendar API o a la Meet REST API, con el token de acceso. {@code cuerpo} puede ser nulo. */
    Respuesta api(HttpMethod metodo, URI url, String tokenDeAcceso, Map<String, Object> cuerpo) {
        if (!limitador.intentar()) {
            throw new GoogleNoRespondeException("Se llegó al límite de pedidos por minuto a Google. Se reintenta solo.", 429);
        }
        return enviar(() -> {
            RestClient.RequestBodySpec pedido = cliente.method(metodo).uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeAcceso).accept(MediaType.APPLICATION_JSON);
            if (cuerpo != null) {
                pedido.contentType(MediaType.APPLICATION_JSON).body(serializar(cuerpo));
            }
            return pedido;
        });
    }

    /** Una llamada a los extremos de OAuth de Google (token, revocar): formulario, sin el límite de pedidos de la API. */
    Respuesta formulario(URI url, MultiValueMap<String, String> campos) {
        return enviar(() -> cliente.post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON).body(campos));
    }

    private Respuesta enviar(java.util.function.Supplier<RestClient.RequestBodySpec> pedido) {
        try {
            return pedido.get().exchange((req, res) -> new Respuesta(res.getStatusCode().value(), leer(res.getBody())));
        } catch (RestClientException e) {
            // Sin el mensaje de la excepcion: podria traer la URL con parametros. Se dice que no hubo respuesta.
            throw new GoogleNoRespondeException("No pudimos hablar con Google. Se reintenta solo.", 0);
        }
    }

    /**
     * Si la respuesta no es un éxito, la excepción que corresponde (siempre una): reintentable, definitiva o de cuenta revocada. 404 y 410 los
     * decide quien llama (borrar un evento que ya no existe es un éxito).
     */
    RuntimeException falla(Respuesta respuesta) {
        int codigo = respuesta.codigo();
        String motivo = respuesta.motivo();
        if (codigo == 401 || "invalid_grant".equals(motivo)) {
            return new CuentaGoogleRevocadaException("Google rechazó el permiso de la cuenta (código " + codigo + ").");
        }
        boolean esDeCuota = codigo == 403 && MOTIVOS_DE_CUOTA.contains(motivo);
        if (codigo == 408 || codigo == 429 || codigo >= 500 || esDeCuota) {
            return new GoogleNoRespondeException("Google no pudo atender ahora (código " + codigo + "). Se reintenta solo.", codigo);
        }
        String detalle = codigo == 403 ? "Google no dio permiso para esto (código 403): revisa los permisos de la cuenta."
                : "Google no aceptó el pedido (código " + codigo + ").";
        return new GoogleRechazoException(detalle, codigo);
    }

    private String serializar(Map<String, Object> cuerpo) {
        try {
            return json.writeValueAsString(cuerpo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo armar el pedido a Google.", e);
        }
    }

    private JsonNode leer(java.io.InputStream cuerpo) throws IOException {
        byte[] bytes = cuerpo == null ? new byte[0] : cuerpo.readAllBytes();
        if (bytes.length == 0) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(bytes);
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }
}
