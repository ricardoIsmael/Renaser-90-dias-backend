package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.ChatIAPort.Consulta;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpRenasiaChatAdapterTest {

    /** D-102: el placeholder sirve a los dos agentes y no bautiza a ninguno (el nombre del
     * acompanante todavia no esta confirmado por el dueno). */
    @ParameterizedTest
    @EnumSource(AgenteConversacional.class)
    void emiteUnTextoExplicandoQueFaltaConfiguracionYLuegoFin(AgenteConversacional agente) {
        // Solo reactor-core (sin reactor-test, no declarado como dependencia propia del pom):
        // .collectList().block() alcanza para verificar un Flux finito y corto como este.
        var eventos = new NoOpRenasiaChatAdapter()
                .responder(new Consulta(agente, "hola", List.of(), null, List.of()))
                .collectList().block();

        assertThat(eventos).hasSize(2);
        assertThat(eventos.get(0)).isInstanceOf(EventoRenasia.Texto.class);
        String texto = ((EventoRenasia.Texto) eventos.get(0)).fragmento();
        assertThat(texto).contains("credenciales de IA");
        assertThat(texto.toLowerCase()).doesNotContain("sparkie").doesNotContain("renasia");
        assertThat(eventos.get(1)).isEqualTo(new EventoRenasia.Fin());
    }
}
