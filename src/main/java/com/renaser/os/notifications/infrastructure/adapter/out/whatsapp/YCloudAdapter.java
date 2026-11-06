package com.renaser.os.notifications.infrastructure.adapter.out.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.notifications.application.ports.out.whatsapp.EnvioDeWhatsAppPort;
import com.renaser.os.notifications.domain.model.whatsapp.CausaDeFalla;
import com.renaser.os.notifications.domain.model.whatsapp.ResultadoDelEnvio;
import com.renaser.os.notifications.infrastructure.config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El envio por WhatsApp a traves de YCloud (spec 041, FR-018): {@code POST /v2/whatsapp/messages/sendDirectly} con la cabecera {@code X-API-Key}. Copia la idea
 * de RenaserOs (la plantilla con sus huecos y el boton de respuesta rapida) adaptada a este puerto.
 *
 * <p><b>Lo que NO hace.</b> Ni reintenta nada (FR-023: un 5xx o un corte pudo haber salido, y un 429 tampoco: el WhatsApp es un canal extra) ni usa
 * {@code externalId} como llave de idempotencia (YCloud dice que no lo es: dos veces el mismo {@code externalId} son dos mensajes; por eso la entrega se aparta ANTES
 * de llamar). Tampoco escribe en el registro la clave, el cuerpo del mensaje ni el numero completo: solo el estado HTTP y el tipo de la excepcion (FR-028).
 *
 * <p>Sin la configuracion ({@link WhatsAppProperties#listoParaEnviar()}) no sale a la red: {@link #configurado()} es falso y quien llama no lo invoca.
 */
@Component
class YCloudAdapter implements EnvioDeWhatsAppPort {

    private static final Logger log = LoggerFactory.getLogger(YCloudAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String RUTA_DE_ENVIO = "/whatsapp/messages/sendDirectly";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final WhatsAppProperties propiedades;

    YCloudAdapter(WhatsAppProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public boolean configurado() {
        return propiedades.listoParaEnviar();
    }

    @Override
    public ResultadoDelEnvio enviarRecordatorio(String destino, UUID externalId, List<String> huecos, String cargaDelBoton) {
        if (!configurado()) {
            return ResultadoDelEnvio.fallido(CausaDeFalla.OTRA, "WhatsApp sin configurar");
        }
        try {
            HttpResponse<String> respuesta = http.send(pedido(cuerpoDeLaPlantilla(destino, externalId, huecos, cargaDelBoton)),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return interpretar(respuesta.statusCode(), respuesta.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultadoDelEnvio.noSeSupo("Envío interrumpido");
        } catch (IOException | RuntimeException e) {
            // Pudo haber salido y la respuesta perderse por el camino: no se reintenta.
            log.warn("[notifications.ycloud] no se supo si salio el mensaje ({})", e.getClass().getSimpleName());
            return ResultadoDelEnvio.noSeSupo("YCloud no contestó a tiempo");
        }
    }

    @Override
    public boolean configuradoParaCodigos() {
        return propiedades.listoParaCodigos();
    }

    /** Spec 043: la plantilla de AUTENTICACION. Nunca escribe el codigo ni el numero en el registro. */
    @Override
    public ResultadoDelEnvio enviarCodigo(String destino, String codigo) {
        if (!configuradoParaCodigos()) {
            return ResultadoDelEnvio.fallido(CausaDeFalla.OTRA, "WhatsApp sin configurar");
        }
        try {
            HttpResponse<String> respuesta = http.send(pedido(cuerpoDelCodigo(destino, codigo)),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return interpretar(respuesta.statusCode(), respuesta.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultadoDelEnvio.noSeSupo("Envío interrumpido");
        } catch (IOException | RuntimeException e) {
            log.warn("[notifications.ycloud] no se supo si salio el codigo ({})", e.getClass().getSimpleName());
            return ResultadoDelEnvio.noSeSupo("YCloud no contestó a tiempo");
        }
    }

    /**
     * La plantilla de AUTENTICACION de Meta (spec 043, FR-004): el codigo va en el unico hueco del cuerpo y otra vez en el boton «Copiar código», que
     * Meta exige como un boton {@code url} con el codigo de parametro. Sin {@code externalId}: un codigo no tiene fila de entrega que seguir.
     */
    Map<String, Object> cuerpoDelCodigo(String destino, String codigo) {
        List<Map<String, Object>> componentes = List.of(
                Map.of("type", "body", "parameters", List.of(Map.of("type", "text", "text", codigo))),
                Map.of("type", "button", "sub_type", "url", "index", "0",
                        "parameters", List.of(Map.of("type", "text", "text", codigo))));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("from", propiedades.numeroEmisor());
        cuerpo.put("to", destino);
        cuerpo.put("type", "template");
        cuerpo.put("template", Map.of("name", propiedades.plantillaCodigo(), "language", Map.of("code", propiedades.idioma()),
                "components", componentes));
        return cuerpo;
    }

    @Override
    public void enviarTexto(String destino, String texto) {
        if (!configurado()) {
            return;
        }
        try {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("from", propiedades.numeroEmisor());
            cuerpo.put("to", destino);
            cuerpo.put("type", "text");
            cuerpo.put("text", Map.of("body", texto));
            HttpResponse<String> respuesta = http.send(pedido(cuerpo), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (respuesta.statusCode() / 100 != 2) {
                log.warn("[notifications.ycloud] la respuesta de baja no salio (HTTP {})", respuesta.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            // La baja ya esta apuntada: contestar es un mejor esfuerzo.
            log.warn("[notifications.ycloud] la respuesta de baja no salio ({})", e.getClass().getSimpleName());
        }
    }

    private HttpRequest pedido(Map<String, Object> cuerpo) throws JsonProcessingException {
        return HttpRequest.newBuilder(URI.create(propiedades.url() + RUTA_DE_ENVIO))
                .timeout(Duration.ofMillis(propiedades.timeoutMs()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-API-Key", propiedades.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                .build();
    }

    /** La plantilla con sus huecos en el cuerpo y la carga del boton «No recibir más» (un componente {@code button} de respuesta rapida). */
    Map<String, Object> cuerpoDeLaPlantilla(String destino, UUID externalId, List<String> huecos, String cargaDelBoton) {
        List<Map<String, Object>> parametros = new ArrayList<>();
        huecos.forEach(hueco -> parametros.add(Map.of("type", "text", "text", hueco)));
        List<Map<String, Object>> componentes = new ArrayList<>();
        componentes.add(Map.of("type", "body", "parameters", parametros));
        componentes.add(Map.of("type", "button", "sub_type", "quick_reply", "index", 0,
                "parameters", List.of(Map.of("type", "payload", "payload", cargaDelBoton))));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("from", propiedades.numeroEmisor());
        cuerpo.put("to", destino);
        cuerpo.put("type", "template");
        cuerpo.put("externalId", externalId.toString());
        cuerpo.put("template", Map.of("name", propiedades.plantilla(), "language", Map.of("code", propiedades.idioma()),
                "components", componentes));
        return cuerpo;
    }

    /**
     * 2xx: aceptado (o rechazado si dice {@code failed}); 401 y 403: la clave; 429: ir mas despacio, sin reintento; 5xx: no se supo (pudo salir); el
     * resto de los 4xx: fallo, con la causa que se reconozca en el texto.
     */
    static ResultadoDelEnvio interpretar(int http, String cuerpo) {
        JsonNode json = leer(cuerpo);
        String motivo = motivoDe(json, http);
        if (http >= 500) {
            return ResultadoDelEnvio.noSeSupo("YCloud respondió " + http);
        }
        if (http / 100 != 2) {
            return ResultadoDelEnvio.fallido(CausaDeFalla.segun(http, motivo + " " + (cuerpo == null ? "" : cuerpo)), motivo);
        }
        String proveedorId = texto(json, "id");
        String wamid = texto(json, "wamid");
        if ("failed".equalsIgnoreCase(texto(json, "status"))) {
            return ResultadoDelEnvio.rechazado(proveedorId, wamid, CausaDeFalla.segun(null, motivo), motivo);
        }
        return ResultadoDelEnvio.aceptado(proveedorId, wamid);
    }

    private static JsonNode leer(String cuerpo) {
        try {
            return cuerpo == null || cuerpo.isBlank() ? null : JSON.readTree(cuerpo);
        } catch (JsonProcessingException sinJson) {
            return null;
        }
    }

    private static String motivoDe(JsonNode json, int http) {
        for (String campo : List.of("errorMessage", "message", "errorCode")) {
            String valor = texto(json, campo);
            if (valor != null) {
                return valor;
            }
        }
        JsonNode error = json == null ? null : json.get("error");
        String delError = texto(error, "message");
        if (delError != null) {
            return delError;
        }
        JsonNode api = json == null ? null : json.get("whatsappApiError");
        String delApi = texto(api, "message");
        return delApi != null ? delApi : "YCloud respondió " + http;
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo == null ? null : nodo.get(campo);
        return valor == null || valor.isNull() || valor.asText().isBlank() ? null : valor.asText();
    }
}
