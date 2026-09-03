package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.EmbeddingPort;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación real de {@link EmbeddingPort} sobre el {@link EmbeddingModel} de Spring AI
 * (el bean {@code GoogleGenAiTextEmbeddingModel} que arma {@link GoogleGenAiClientesConfig}).
 * Se programa contra la interfaz {@code EmbeddingModel}, no contra la clase concreta de
 * Google GenAI: no hay otro bean de ese tipo en el contexto mientras las autoconfiguraciones
 * de Spring AI sigan excluidas, así que la inyección no es ambigua, y este adaptador queda
 * desacoplado del proveedor concreto.
 *
 * <p><b>Nunca trunca en silencio (D-51).</b> {@code spring.ai.google.genai.embedding.text.dimensions}
 * (768 por defecto) va como parámetro del propio pedido a Gemini — el modelo
 * ({@code gemini-embedding-001}) devuelve nativamente esa cantidad de valores (truncado
 * Matryoshka, no un recorte casero). Si aun así el modelo devolviera otra cantidad —
 * configuración incoherente entre el yaml y lo que Google realmente entrega — este adaptador
 * falla con {@link IllegalStateException} y un mensaje explícito en vez de truncar/rellenar
 * el vector, porque guardar un vector de tamaño equivocado en {@code vector(768)} corrompe la
 * búsqueda por similitud de todo lo indexado con ese registro.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "google")
class GoogleGenAiEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;

    GoogleGenAiEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<Float> generar(String texto) {
        float[] vector = embeddingModel.embed(texto);
        if (vector.length != ChunkConocimiento.DIMENSION_EMBEDDING) {
            throw new IllegalStateException(
                    "El modelo de embeddings devolvio " + vector.length + " dimensiones, se esperaban "
                            + ChunkConocimiento.DIMENSION_EMBEDDING
                            + ". Revisar spring.ai.google.genai.embedding.text.dimensions y el modelo "
                            + "configurado en spring.ai.google.genai.embedding.text.model.");
        }
        List<Float> resultado = new ArrayList<>(vector.length);
        for (float valor : vector) {
            resultado.add(valor);
        }
        return resultado;
    }
}
