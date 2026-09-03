package com.renaser.os.rag.infrastructure.adapter.out.ia;

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
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey) {
        return Client.builder().apiKey(apiKey).build();
    }

    @Bean
    GoogleGenAiChatModel googleGenAiChatModel(Client googleGenAiClient, ToolCallingManager toolCallingManager,
            ObservationRegistry observationRegistry,
            @Value("${spring.ai.google.genai.chat.model}") String modelo) {
        GoogleGenAiChatOptions opciones = GoogleGenAiChatOptions.builder().model(modelo).build();
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
