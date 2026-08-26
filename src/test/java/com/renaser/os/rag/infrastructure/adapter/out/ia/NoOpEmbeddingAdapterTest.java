package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.domain.model.conocimiento.ChunkConocimiento;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpEmbeddingAdapterTest {

    @Test
    void siempreDevuelveUnVectorDeCerosDeLaDimensionEsperada() {
        List<Float> vector = new NoOpEmbeddingAdapter().generar("cualquier texto");

        assertThat(vector).hasSize(ChunkConocimiento.DIMENSION_EMBEDDING);
        assertThat(vector).allMatch(v -> v == 0.0f);
    }
}
