package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.rag.domain.model.aviso.AvisosEnChat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** La lista de tipos llega como texto separado por comas desde el YAML o el entorno. */
class AvisosEnChatConfigTest {

    @Test
    void separaLosTiposIgnorandoEspaciosYComasDeMas() {
        assertThat(AvisosEnChatConfig.separarTipos(" INICIO , ,POR_VENCER,"))
                .containsExactlyInAnyOrder("INICIO", "POR_VENCER");
        assertThat(AvisosEnChatConfig.separarTipos("")).isEmpty();
    }

    @Test
    void sinPropiedadesQuedaApagado() {
        AvisosEnChat avisos = new AvisosEnChatConfig().avisosEnChat(false, "", "", "");

        assertThat(avisos.aplicaA("INICIO")).isFalse();
        assertThat(avisos.aplicaA("POR_VENCER")).isFalse();
    }
}
