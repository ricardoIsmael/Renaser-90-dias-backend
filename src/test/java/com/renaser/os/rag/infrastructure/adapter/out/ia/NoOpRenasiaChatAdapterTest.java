package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpRenasiaChatAdapterTest {

    @Test
    void devuelveUnUnicoMensajeExplicandoQueFaltaConfiguracion() {
        // Solo reactor-core (sin reactor-test, no declarado como dependencia propia del pom):
        // .collectList().block() alcanza para verificar un Flux finito y corto como este.
        var mensajes = new NoOpRenasiaChatAdapter().responder("hola", List.of()).collectList().block();

        assertThat(mensajes).hasSize(1);
        assertThat(mensajes.get(0).toLowerCase()).contains("renasia");
    }
}
