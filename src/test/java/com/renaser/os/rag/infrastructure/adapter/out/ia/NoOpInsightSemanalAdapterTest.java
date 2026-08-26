package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpInsightSemanalAdapterTest {

    @Test
    void siempreDevuelveVacio() {
        var resultado = new NoOpInsightSemanalAdapter().analizar(List.of("entrada 1", "entrada 2"));

        assertThat(resultado).isEmpty();
    }
}
