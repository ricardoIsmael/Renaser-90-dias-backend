package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.rag.domain.model.logro.LogrosEnChat;
import com.renaser.os.rag.domain.model.logro.TipoLogro;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogrosEnChatConfigTest {

    @Test
    void ignoraLosNombresDesconocidosYLosEspacios() {
        assertThat(LogrosEnChatConfig.tiposConocidos(" RACHA_SIN_CELULAR , OTRO,,ROCA_COMPLETADA"))
                .containsExactlyInAnyOrder(TipoLogro.RACHA_SIN_CELULAR, TipoLogro.ROCA_COMPLETADA);
        assertThat(LogrosEnChatConfig.tiposConocidos("")).isEmpty();
    }

    @Test
    void sinPropiedadesQuedaApagado() {
        LogrosEnChat logros = new LogrosEnChatConfig().logrosEnChat(false, "", "", "");

        assertThat(logros.aplicaA(TipoLogro.RACHA_SIN_CELULAR)).isFalse();
        assertThat(logros.aplicaA(TipoLogro.ROCA_COMPLETADA)).isFalse();
    }

    @Test
    void conLosTiposPorDefectoDelYamlSoloSeCelebraLaRacha() {
        LogrosEnChat logros = new LogrosEnChatConfig().logrosEnChat(true, "RACHA_SIN_CELULAR", "¡Bien!", "¡Roca!");

        assertThat(logros.aplicaA(TipoLogro.RACHA_SIN_CELULAR)).isTrue();
        assertThat(logros.aplicaA(TipoLogro.ROCA_COMPLETADA)).isFalse();
    }
}
