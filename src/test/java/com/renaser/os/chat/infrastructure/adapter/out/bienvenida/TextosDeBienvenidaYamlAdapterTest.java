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
    @DisplayName("D-190: el recurso del repo trae los dos textos del soporte y el del grupo (D-191), con sus marcadores")
    void elRecursoDelRepoTraeLosTextos() throws Exception {
        TextosDeBienvenidaYamlAdapter textos = new TextosDeBienvenidaYamlAdapter();

        assertThat(textos.soporteConLaTarjeta()).contains("{nombre}").doesNotEndWith("\n");
        assertThat(textos.soporteFormal()).contains("{nombre}").contains("confirmado").contains("sesión técnica");
        assertThat(textos.grupo()).contains("{nombre}").contains("{mentor}");
        String crudo = new ClassPathResource(TextosDeBienvenidaYamlAdapter.RECURSO)
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(crudo).contains("BORRADORES");
    }

    @Test
    @DisplayName("D-204: el texto del grupo lo dice el programa (el mentor en tercera persona), sin horarios ni links")
    void elTextoDelGrupoLoDiceElPrograma() {
        String grupo = new TextosDeBienvenidaYamlAdapter().grupo();

        assertThat(grupo).startsWith("¡Hola, {nombre}!").contains("con {mentor}, que te va a acompañar")
                .endsWith("¡Te damos la bienvenida!");
        // Antes: «Soy {mentor} y voy a acompañarte…», el mentor en primera persona.
        assertThat(grupo).doesNotContain("Soy {mentor}").doesNotContain("http").doesNotContainPattern("\\d{1,2}:\\d{2}");
    }

    @Test
    @DisplayName("una clave que falta o viene vacía apaga solo ese mensaje")
    void claveVaciaApagaEseMensaje() {
        TextosDeBienvenidaYamlAdapter textos = new TextosDeBienvenidaYamlAdapter(new ByteArrayResource(
                "soporte:\n  con-la-tarjeta: \"\"\n".getBytes(StandardCharsets.UTF_8)));

        assertThat(textos.soporteConLaTarjeta()).isEmpty();
        assertThat(textos.soporteFormal()).isEmpty();
        assertThat(textos.grupo()).isEmpty();
    }

    @Test
    @DisplayName("si el recurso falta, el arranque falla en vez de mandar una bienvenida vacía")
    void sinRecursoFallaAlArrancar() {
        assertThatThrownBy(() -> new TextosDeBienvenidaYamlAdapter(new ClassPathResource("bienvenida/no-existe.yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("textos de bienvenida");
    }

    @Test
    @DisplayName("D-190/D-199: application.yaml ya no lee el texto de BIENVENIDA_TEXTO ni el remitente de BIENVENIDA_REMITENTE_EMAIL: la firma el programa")
    void elTextoYaNoVienePorEntorno() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        // Del disco y no del classpath: en las pruebas src/test/resources/application.yaml la tapa.
        yaml.setResources(new FileSystemResource("src/main/resources/application.yaml"));
        Properties config = yaml.getObject();

        assertThat(config).doesNotContainKey("renaser.bienvenida.texto");
        // Corregido 2026-09-27 (D-199): acá se exigía que el remitente siguiera por entorno.
        assertThat(config).doesNotContainKey("renaser.bienvenida.remitente-email");
    }

    @Test
    @DisplayName("D-199/D-204: el interruptor de las bienvenidas existe en application.yaml y viene APAGADO")
    void elInterruptorVieneApagado() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        // Del disco y no del classpath: en las pruebas src/test/resources/application.yaml la tapa (E-318).
        yaml.setResources(new FileSystemResource("src/main/resources/application.yaml"));

        assertThat(yaml.getObject().getProperty("renaser.chat.bienvenida.activa")).isEqualTo("${BIENVENIDA_ACTIVA:false}");
    }
}
