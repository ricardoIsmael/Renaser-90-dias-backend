package com.renaser.os.rag.application.services.analisis;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * El JSON del análisis 80/20 (spec 031): el pedido a la IA y la foto de cifras que se guarda junto al texto. Un mapper propio, como el
 * de {@code CompactarConversacionGeminiAdapter} (E-33: Boot 4 autoconfigura Jackson 3 y no se inyecta). Fechas en ISO, no en números, y
 * tolerante con campos que no conoce: una foto guardada hoy se lee mañana aunque la cuenta agregue un campo.
 */
public final class JsonDelAnalisis {

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonDelAnalisis() {
    }

    public static String escribir(Object valor) {
        return JSON.writeValueAsString(valor);
    }

    public static <T> T leer(String json, Class<T> tipo) {
        return JSON.readValue(json, tipo);
    }

    public static JsonNode arbol(String json) {
        return JSON.readTree(json);
    }
}
