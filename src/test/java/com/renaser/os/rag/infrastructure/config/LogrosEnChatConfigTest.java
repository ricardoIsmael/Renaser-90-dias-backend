package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.rag.domain.model.logro.LogrosEnChat;
import com.renaser.os.rag.domain.model.logro.TipoLogro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.util.Properties;

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

    /**
     * Lo que se despliega de verdad: el texto de {@code src/main/resources/application.yaml} (en test lo reemplaza el de test, por eso se lee el archivo).
     * Issue #62: la app dejo de decir «roca» (son «acciones» de los objetivos); si un entorno prende este logro, el chat no puede volver a decirlo.
     */
    // issue #62
    @Test
    @DisplayName("issue #62: el logro de una accion cumplida no dice «roca»")
    void elLogroDeLaAccionNoDiceRoca() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yaml"));
        Properties main = yaml.getObject();

        String plantilla = main.getProperty("renaser.ia.acompanante.logros-en-chat-plantilla-roca-completada");

        assertThat(plantilla).startsWith("¡Acción cumplida!").doesNotContainIgnoringCase("roca");
        assertThat(new LogrosEnChatConfig().logrosEnChat(true, "ROCA_COMPLETADA", "", plantilla).redactar(TipoLogro.ROCA_COMPLETADA))
                .hasValueSatisfying(texto -> assertThat(texto).contains("Acción cumplida"));
    }
}
