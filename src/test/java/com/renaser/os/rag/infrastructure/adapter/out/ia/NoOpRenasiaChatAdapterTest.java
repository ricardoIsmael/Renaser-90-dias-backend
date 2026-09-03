package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpRenasiaChatAdapterTest {

    @Test
    void emiteUnTextoExplicandoQueFaltaConfiguracionYLuegoFin() {
        // Solo reactor-core (sin reactor-test, no declarado como dependencia propia del pom):
        // .collectList().block() alcanza para verificar un Flux finito y corto como este.
        var eventos = new NoOpRenasiaChatAdapter().responder("hola", List.of()).collectList().block();

        assertThat(eventos).hasSize(2);
        assertThat(eventos.get(0)).isInstanceOf(EventoRenasia.Texto.class);
        assertThat(((EventoRenasia.Texto) eventos.get(0)).fragmento().toLowerCase()).contains("renasia");
        assertThat(eventos.get(1)).isEqualTo(new EventoRenasia.Fin());
    }
}
