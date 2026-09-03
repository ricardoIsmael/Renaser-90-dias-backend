package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Sin Spring y sin red: {@link EmbeddingModel} se mockea, nunca se llama a Gemini de verdad.
 */
@ExtendWith(MockitoExtension.class)
class GoogleGenAiEmbeddingAdapterTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void devuelveElVectorCuandoTieneLaDimensionEsperada() {
        float[] vectorDelModelo = new float[ChunkConocimiento.DIMENSION_EMBEDDING];
        vectorDelModelo[0] = 0.5f;
        vectorDelModelo[1] = -0.25f;
        when(embeddingModel.embed(anyString())).thenReturn(vectorDelModelo);

        List<Float> resultado = new GoogleGenAiEmbeddingAdapter(embeddingModel).generar("hola");

        assertThat(resultado).hasSize(ChunkConocimiento.DIMENSION_EMBEDDING);
        assertThat(resultado.get(0)).isEqualTo(0.5f);
        assertThat(resultado.get(1)).isEqualTo(-0.25f);
    }

    @Test
    void fallaConMensajeClaroSiElModeloDevuelveMasDimensionesQueLasEsperadas() {
        // gemini-embedding-001 sin truncar devuelve 3072, no 768 (D-51) — este es exactamente
        // el escenario que no debe pasar en silencio.
        when(embeddingModel.embed(anyString())).thenReturn(new float[3072]);

        assertThatThrownBy(() -> new GoogleGenAiEmbeddingAdapter(embeddingModel).generar("hola"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3072")
                .hasMessageContaining("768");
    }

    @Test
    void fallaConMensajeClaroSiElModeloDevuelveMenosDimensionesQueLasEsperadas() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[10]);

        assertThatThrownBy(() -> new GoogleGenAiEmbeddingAdapter(embeddingModel).generar("hola"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10")
                .hasMessageContaining("768");
    }
}
