package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los textos de la bienvenida salen del recurso versionado {@code bienvenida/mensajes.yaml} y ya no
 * del entorno (D-190). Sin Spring: el adaptador se construye a mano.
 */
class TextosDeBienvenidaYamlAdapterTest {

    @Test
    @DisplayName("D-190: el recurso del repo trae los dos textos del soporte, con {nombre}, y el borrador del grupo")
    void elRecursoDelRepoTraeLosTextos() throws Exception {
        TextosDeBienvenidaYamlAdapter textos = new TextosDeBienvenidaYamlAdapter();

        assertThat(textos.soporteConLaTarjeta()).contains("{nombre}").doesNotEndWith("\n");
        assertThat(textos.soporteFormal()).contains("{nombre}").contains("confirmado").contains("sesión técnica");
        String crudo = new ClassPathResource(TextosDeBienvenidaYamlAdapter.RECURSO)
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(crudo).contains("BORRADORES").contains("grupo:").contains("{mentor}");
    }

    @Test
    @DisplayName("una clave que falta o viene vacía apaga solo ese mensaje")
    void claveVaciaApagaEseMensaje() {
        TextosDeBienvenidaYamlAdapter textos = new TextosDeBienvenidaYamlAdapter(new ByteArrayResource(
                "soporte:\n  con-la-tarjeta: \"\"\n".getBytes(StandardCharsets.UTF_8)));

        assertThat(textos.soporteConLaTarjeta()).isEmpty();
        assertThat(textos.soporteFormal()).isEmpty();
    }

    @Test
    @DisplayName("si el recurso falta, el arranque falla en vez de mandar una bienvenida vacía")
    void sinRecursoFallaAlArrancar() {
        assertThatThrownBy(() -> new TextosDeBienvenidaYamlAdapter(new ClassPathResource("bienvenida/no-existe.yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("textos de bienvenida");
    }

    @Test
    @DisplayName("D-190: application.yaml ya no lee el texto de BIENVENIDA_TEXTO; el remitente sigue por entorno")
    void elTextoYaNoVienePorEntorno() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        // Del disco y no del classpath: en las pruebas src/test/resources/application.yaml la tapa.
        yaml.setResources(new FileSystemResource("src/main/resources/application.yaml"));
        Properties config = yaml.getObject();

        assertThat(config).doesNotContainKey("renaser.bienvenida.texto");
        assertThat(config.getProperty("renaser.bienvenida.remitente-email")).isEqualTo("${BIENVENIDA_REMITENTE_EMAIL:}");
    }
}
