package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.analisis.GenerarAnalisis8020Port;
import com.renaser.os.rag.domain.model.analisis.TextoDelAnalisis;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * El análisis 80/20 sobre Gemini (spec 031, D-306): UNA llamada, sin herramientas, con el modelo, la credencial y el cliente de SER
 * ({@code GoogleGenAiClientesConfig}); el JSON de datos va como mensaje de usuario y el prompt de sistema es texto plano
 * ({@code prompts/analisis-80-20.txt}: lleva un ejemplo con llaves, que romperían una plantilla). Devuelve las cuatro partes en JSON.
 *
 * <p>Lo que lanza el SDK de Google se traduce como en el chat: un 429, un 408, un 5xx o un timeout es
 * {@link com.renaser.os.shared.domain.ProveedorIaNoDisponibleException} (falla del proveedor, cuenta para el circuito); lo demás sigue como
 * llegó. Una respuesta que no es un análisis legible es {@link IllegalStateException}: el proveedor respondió, el texto no sirve.
 *
 * <p><b>Ni el pedido ni la respuesta se escriben en ningún registro</b>: son datos personales (FR-024).
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "google")
class GoogleGenAiAnalisis8020Adapter implements GenerarAnalisis8020Port {

    static final String RECURSO_PROMPT = "prompts/analisis-80-20.txt";

    /** Propio y no inyectado (E-33: Boot 4 autoconfigura Jackson 3). */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ChatClient chatClient;
    private final String instrucciones;

    GoogleGenAiAnalisis8020Adapter(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
        this.instrucciones = leer(RECURSO_PROMPT);
    }

    @Override
    public boolean disponible() {
        return true;
    }

    @Override
    public TextoDelAnalisis generar(PedidoDeAnalisis pedido) {
        String respuesta;
        try {
            respuesta = chatClient.prompt().system(instrucciones).user(pedido.datosEnJson()).call().content();
        } catch (RuntimeException falla) {
            throw TraduccionErroresGoogleGenAi.traducir(falla);
        }
        return leerTexto(respuesta);
    }

    /**
     * Tolera el JSON envuelto en un bloque de código. Falta una clave, o una parte vacía o demasiado larga: es una respuesta que no sirve y
     * se reintenta en otro turno; nunca se guarda a medias.
     */
    static TextoDelAnalisis leerTexto(String respuesta) {
        String json = respuesta == null ? "" : respuesta.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        JsonNode raiz;
        try {
            raiz = JSON.readTree(json);
        } catch (RuntimeException ilegible) {
            throw new IllegalStateException("El análisis no devolvió un JSON legible");
        }
        JsonNode pasos = raiz.path("proximosPasos");
        if (!pasos.isArray()) {
            throw new IllegalStateException("El análisis no devolvió la lista de próximos pasos");
        }
        List<String> proximos = new ArrayList<>();
        pasos.forEach(paso -> proximos.add(paso.asString("")));
        try {
            return new TextoDelAnalisis(raiz.path("loQueMasMovio").asString(""), raiz.path("loQueSeTeFue").asString(""),
                    raiz.path("comoEstuviste").asString(""), proximos);
        } catch (IllegalArgumentException incompleto) {
            throw new IllegalStateException("El análisis llegó incompleto o demasiado largo");
        }
    }

    private static String leer(String recurso) {
        try {
            return new ClassPathResource(recurso).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + recurso, e);
        }
    }
}
