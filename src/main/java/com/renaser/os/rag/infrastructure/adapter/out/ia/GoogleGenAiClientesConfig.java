package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Arma a mano los clientes de Google GenAI (Spring AI 2.0) para Renasia, sin depender de las
 * autoconfiguraciones de Spring AI: esas siguen excluidas a propósito en
 * {@code application.yaml} (bloque {@code spring.autoconfigure.exclude} y
 * {@code spring.ai.model.*: none}) porque el resto del sistema — validación de evidencia,
 * onboarding V90 — todavía no tiene sus adaptadores reales y esa exclusión evita que Spring
 * intente resolver un {@code ChatModel}/{@code EmbeddingModel} ambiguo entre proveedores. Esta
 * clase construye los beans explícitamente, activa solo con
 * {@code renaser.ia.proveedor=google}, así que no reabre esa ambigüedad.
 *
 * <p><b>Clases exactas, verificadas contra el bytecode de {@code spring-ai-google-genai:2.0.0},
 * {@code spring-ai-google-genai-embedding:2.0.0} y {@code google-genai:1.58.0} en el repositorio
 * local de Maven</b> — no contra la documentación: {@code com.google.genai.Client},
 * {@code org.springframework.ai.google.genai.GoogleGenAiChatModel} (+ su {@code Builder}),
 * {@code org.springframework.ai.google.genai.GoogleGenAiChatOptions},
 * {@code org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails},
 * {@code org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel} y
 * {@code org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions}. El cableado
 * de cada bean replica, línea por línea, lo que hacen
 * {@code GoogleGenAiChatAutoConfiguration}/{@code GoogleGenAiEmbeddingConnectionAutoConfiguration}/
 * {@code GoogleGenAiTextEmbeddingAutoConfiguration} del propio Spring AI (desensamblado con
 * {@code javap -c} para confirmar el orden exacto de llamadas) — la diferencia es que esas
 * autoconfiguraciones están excluidas y esta clase hace lo mismo a mano.
 *
 * <p>Un solo {@link Client} (una sola API key, {@code spring.ai.google.genai.api-key}) se
 * comparte entre chat y embeddings — {@link GoogleGenAiEmbeddingConnectionDetails} acepta un
 * {@code Client} ya construido en vez de armar el suyo propio, así que no hace falta declarar
 * una segunda credencial para lo mismo.
 *
 * <p>{@link ToolCallingManager} lo sigue proveyendo la autoconfiguración de Spring AI
 * ({@code ToolCallingAutoConfiguration}, en {@code spring-ai-autoconfigure-model-tool}), que NO
 * está en la lista de exclusiones — Renasia no declara herramientas hoy, pero
 * {@link GoogleGenAiChatModel.Builder#toolCallingManager} es un parámetro obligatorio del
 * builder real.
 */
@Configuration
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "google")
class GoogleGenAiClientesConfig {

    @Bean
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey,
                             @Value("${renaser.ia.google.timeout-ms:60000}") int timeoutMs) {
        // Auditoria NFR 2026-09-06. Sin esto el cliente venia con DOS problemas invisibles:
        //
        // (1) NINGUN timeout. Una llamada a Gemini que se colgara retenia el hilo virtual y la
        //     conexion SSE del chat sin limite. 60 s cubre una respuesta larga con herramientas y
        //     sigue por debajo del timeout async de MVC (120 s, application.yaml), que es el que
        //     corta la conexion del lado del servidor.
        //
        // (2) El SDK REINTENTA SOLO aunque nadie lo configure: `ApiClient` instala un
        //     `RetryInterceptor` con `HttpRetryOptions.builder().build()` si no le pasan nada,
        //     y sus defaults (verificados en el bytecode de google-genai 1.58.0) son 5 intentos,
        //     1 s -> 60 s con base 2, sobre 408/429/500/502/503/504. Es decir: ante un 429 de
        //     cuota agotada, cada mensaje del chat golpeaba a Google 5 veces con ~15 s de esperas
        //     entre medio, gastando mas cuota y colgando al aprendiz, para fallar igual. Aca se
        //     deja 1 reintento rapido para los fallos que SI se recuperan en un instante (5xx,
        //     timeout) y se saca el 429 de la lista: una cuota no vuelve en dos segundos, y esa
        //     espera la decide el cliente con el Retry-After (TraduccionErroresGoogleGenAi).
        HttpRetryOptions reintentos = HttpRetryOptions.builder()
                .attempts(2)
                .initialDelay(0.5)
                .maxDelay(2.0)
                .httpStatusCodes(408, 500, 502, 503, 504)
                .build();
        HttpOptions opciones = HttpOptions.builder()
                .timeout(timeoutMs)
                .retryOptions(reintentos)
                .build();
        return Client.builder().apiKey(apiKey).httpOptions(opciones).build();
    }

    /**
     * {@code renaser.ia.busqueda-web} enciende el grounding con Google Search (2026-09-04). Es
     * lo que le permite a Renasia responder algo util cuando la base de conocimiento del
     * programa no cubre la pregunta, en vez de abstenerse — que era la queja concreta del
     * dueño: el asistente quedaba mudo demasiado seguido.
     *
     * <p><b>Verificado contra el bytecode de {@code spring-ai-google-genai:2.0.0}</b>, no contra
     * la documentacion (misma regla que el resto de esta clase):
     * {@code GoogleGenAiChatOptions.Builder.googleSearchRetrieval(Boolean)} existe, y
     * {@code GoogleGenAiChatModel} lo traduce a
     * {@code Tool.builder().googleSearch(GoogleSearch.builder().build())} al armar el pedido.
     *
     * <p>Se separa en su propio bean de opciones para no pasar del techo de 4 parametros por
     * metodo (CLAUDE.MD sec. 5.4.8) al sumarle el interruptor.
     */
    @Bean
    GoogleGenAiChatOptions googleGenAiChatOptions(@Value("${spring.ai.google.genai.chat.model}") String modelo,
            @Value("${renaser.ia.busqueda-web}") Boolean busquedaWeb) {
        return GoogleGenAiChatOptions.builder()
                .model(modelo)
                .googleSearchRetrieval(busquedaWeb)
                .build();
    }

    @Bean
    GoogleGenAiChatModel googleGenAiChatModel(Client googleGenAiClient, ToolCallingManager toolCallingManager,
            ObservationRegistry observationRegistry, GoogleGenAiChatOptions opciones) {
        return GoogleGenAiChatModel.builder()
                .genAiClient(googleGenAiClient)
                .options(opciones)
                .toolCallingManager(toolCallingManager)
                .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                .observationRegistry(observationRegistry)
                .build();
    }

    @Bean
    GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(Client googleGenAiClient) {
        return GoogleGenAiEmbeddingConnectionDetails.builder().genAiClient(googleGenAiClient).build();
    }

    /**
     * Separado de {@link #googleGenAiTextEmbeddingModel} para no superar el techo de 4
     * parámetros por método (CLAUDE.MD sec. 5.4.8).
     */
    @Bean
    GoogleGenAiTextEmbeddingOptions googleGenAiTextEmbeddingOptions(
            @Value("${spring.ai.google.genai.embedding.text.model}") String modelo,
            @Value("${spring.ai.google.genai.embedding.text.dimensions}") Integer dimensiones,
            @Value("${spring.ai.google.genai.embedding.text.task-type}") GoogleGenAiTextEmbeddingOptions.TaskType tipoTarea) {
        return GoogleGenAiTextEmbeddingOptions.builder()
                .model(modelo)
                .dimensions(dimensiones)
                .taskType(tipoTarea)
                .build();
    }

    @Bean
    GoogleGenAiTextEmbeddingModel googleGenAiTextEmbeddingModel(
            GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails,
            GoogleGenAiTextEmbeddingOptions googleGenAiTextEmbeddingOptions,
            ObservationRegistry observationRegistry) {
        return new GoogleGenAiTextEmbeddingModel(googleGenAiEmbeddingConnectionDetails,
                googleGenAiTextEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE, observationRegistry);
    }
}
