package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.google.genai.errors.ClientException;
import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * El adaptador de embeddings es la unica llamada SINCRONA al proveedor de IA en el camino del
 * chat (la busqueda de contexto ocurre antes de que arranque el stream), asi que es por donde
 * un 429 de Google salia como 500 al cliente. Estas pruebas fijan que ya no.
 */
@ExtendWith(MockitoExtension.class)
class GoogleGenAiEmbeddingAdapterTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    @DisplayName("un 429 del proveedor sale como ProveedorIaNoDisponibleException, no crudo")
    void cuotaAgotadaSeTraduce() {
        when(embeddingModel.embed("hola")).thenThrow(
                new ClientException(429, "RESOURCE_EXHAUSTED", "Quota exceeded"));

        assertThatThrownBy(() -> new GoogleGenAiEmbeddingAdapter(embeddingModel).generar("hola"))
                .isInstanceOf(ProveedorIaNoDisponibleException.class);
    }

    @Test
    @DisplayName("un vector con la dimension esperada se devuelve tal cual")
    void vectorCorrecto() {
        when(embeddingModel.embed("hola")).thenReturn(new float[ChunkConocimiento.DIMENSION_EMBEDDING]);

        assertThat(new GoogleGenAiEmbeddingAdapter(embeddingModel).generar("hola"))
                .hasSize(ChunkConocimiento.DIMENSION_EMBEDDING);
    }

    @Test
    @DisplayName("una dimension distinta a la de la tabla sigue siendo un error de configuracion, no del proveedor")
    void dimensionEquivocada() {
        when(embeddingModel.embed("hola")).thenReturn(new float[3]);

        assertThatThrownBy(() -> new GoogleGenAiEmbeddingAdapter(embeddingModel).generar("hola"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimensiones");
    }
}
