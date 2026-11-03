package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.TextoDeBienvenida;
import com.renaser.os.shared.domain.Formacion;
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
    @DisplayName("D-210: son los ORIGINALES, por pieza; la portada no es un texto")
    void losOriginalesPorPieza() {
        TextosDeBienvenidaYamlAdapter textos = new TextosDeBienvenidaYamlAdapter();

        assertThat(textos.original(Formacion.ESCUELA, PiezaDeBienvenida.SOPORTE_CON_LA_TARJETA)).isEqualTo(textos.soporteConLaTarjeta());
        assertThat(textos.original(Formacion.ESCUELA, PiezaDeBienvenida.SOPORTE_FORMAL)).isEqualTo(textos.soporteFormal());
        assertThat(textos.original(Formacion.ESCUELA, PiezaDeBienvenida.GRUPO)).isEqualTo(textos.grupo());
        assertThatThrownBy(() -> textos.original(Formacion.ESCUELA, PiezaDeBienvenida.PORTADA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("D-210: los originales del repo cumplen las reglas de un texto guardado desde la app")
    void losOriginalesCumplenLasReglas() {
        TextosDeBienvenidaYamlAdapter textos = new TextosDeBienvenidaYamlAdapter();

        for (PiezaDeBienvenida pieza : PiezaDeBienvenida.textos()) {
            assertThat(TextoDeBienvenida.validar(pieza, textos.original(Formacion.ESCUELA, pieza))).as(pieza.name())
                    .isEqualTo(textos.original(Formacion.ESCUELA, pieza));
        }
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
    @DisplayName("Spec 035: el Diplomado y la Certificación traen su sección con el nombre de su institución; el grupo (sin institución) usa el de la Escuela")
    void cadaFormacionTraeLosSuyos() {
        TextosDeBienvenidaYamlAdapter textos = new TextosDeBienvenidaYamlAdapter();

        assertThat(textos.original(Formacion.ESCUELA, PiezaDeBienvenida.SOPORTE_FORMAL)).contains("Formación Renaser");
        assertThat(textos.original(Formacion.DIPLOMADO, PiezaDeBienvenida.SOPORTE_FORMAL)).contains("al Diplomado Renaser")
                .doesNotContain("Formación Renaser").contains("{nombre}");
        assertThat(textos.original(Formacion.DIPLOMADO, PiezaDeBienvenida.SOPORTE_CON_LA_TARJETA)).contains("tu Diplomado Renaser");
        assertThat(textos.original(Formacion.CERTIFICACION, PiezaDeBienvenida.SOPORTE_FORMAL)).contains("a la Certificación Renaser")
                .doesNotContain("Formación Renaser");
        assertThat(textos.original(Formacion.CERTIFICACION, PiezaDeBienvenida.SOPORTE_CON_LA_TARJETA)).contains("tu Certificación Renaser");
        for (Formacion formacion : Formacion.values()) {
            assertThat(textos.original(formacion, PiezaDeBienvenida.GRUPO)).isEqualTo(textos.grupo());
            for (PiezaDeBienvenida pieza : PiezaDeBienvenida.textos()) {
                assertThat(TextoDeBienvenida.validar(pieza, textos.original(formacion, pieza))).as(formacion + " " + pieza)
                        .isEqualTo(textos.original(formacion, pieza));
            }
        }
    }

    @Test
    @DisplayName("Spec 035: a una formación que no trae un texto le sirve el de la Escuela; si lo trae vacío a propósito, ese mensaje no se manda SOLO en ella")
    void faltaUnaClaveOLaTraeVacia() {
        TextosDeBienvenidaYamlAdapter textos = new TextosDeBienvenidaYamlAdapter(new ByteArrayResource((
                "soporte:\n  con-la-tarjeta: Tarjeta {nombre}\n  formal: Formal {nombre}\ngrupo: Grupo {nombre} {mentor}\n"
                        + "diplomado:\n  soporte:\n    formal: \"\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(textos.original(Formacion.DIPLOMADO, PiezaDeBienvenida.SOPORTE_CON_LA_TARJETA)).isEqualTo("Tarjeta {nombre}");
        assertThat(textos.original(Formacion.DIPLOMADO, PiezaDeBienvenida.SOPORTE_FORMAL)).isEmpty();
        assertThat(textos.original(Formacion.ESCUELA, PiezaDeBienvenida.SOPORTE_FORMAL)).isEqualTo("Formal {nombre}");
        assertThat(textos.original(Formacion.CERTIFICACION, PiezaDeBienvenida.SOPORTE_FORMAL)).isEqualTo("Formal {nombre}");
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
