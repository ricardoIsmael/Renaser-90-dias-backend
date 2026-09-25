package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.rag.domain.model.semaforo.ColorDeLaSemana;
import com.renaser.os.rag.domain.model.semaforo.SemaforoEnChat;
import com.renaser.os.rag.domain.model.semaforo.SemaforoEnChat.CierreDeSemana;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que se despliega de verdad: el interruptor y los textos provisorios de
 * {@code src/main/resources/application.yaml}. Se lee el ARCHIVO y no el classpath porque en test el
 * {@code application.yaml} de test reemplaza al de main.
 */
class SemaforoEnChatConfigTest {

    private static final String PREFIJO = "renaser.ia.acompanante.semaforo-en-chat";
    private static final Properties MAIN = leerMain();

    private static Properties leerMain() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yaml"));
        return yaml.getObject();
    }

    private static SemaforoEnChat prendidoConLosTextosDelYaml() {
        return new SemaforoEnChatConfig().semaforoEnChat(true, MAIN.getProperty(PREFIJO + "-plantilla-verde"),
                MAIN.getProperty(PREFIJO + "-plantilla-amarillo"), MAIN.getProperty(PREFIJO + "-plantilla-rojo"),
                MAIN.getProperty(PREFIJO + "-plantilla-sin-datos"));
    }

    @Test
    @DisplayName("encendido por defecto: el dueño pidió avisos automáticos según el caso (2026-09-25)")
    void encendidoPorDefecto() {
        // Antes era FALSE hasta que el dueño aprobara los textos, como los otros mensajes (D-155).
        assertThat(MAIN.getProperty(PREFIJO)).isEqualTo("${IA_ACOMPANANTE_SEMAFORO_EN_CHAT:true}");
    }

    @Test
    @DisplayName("los cuatro casos tienen texto: encendido, ninguna semana se queda sin mensaje")
    void cuatroCasosConTexto() {
        for (String color : new String[]{"verde", "amarillo", "rojo", "sin-datos"}) {
            assertThat(MAIN.getProperty(PREFIJO + "-plantilla-" + color)).isNotBlank();
        }
    }

    @Test
    @DisplayName("cada color dice su color, su palabra y el porcentaje")
    void cadaColorDiceColorPalabraYPorcentaje() {
        SemaforoEnChat semaforo = prendidoConLosTextosDelYaml();

        assertThat(semaforo.redactar(cierre(ColorDeLaSemana.VERDE, ColorSemaforo.VERDE, "86.0")))
                .hasValueSatisfying(texto -> assertThat(texto).contains("verde", "Al día", "86 %"));
        assertThat(semaforo.redactar(cierre(ColorDeLaSemana.AMARILLO, ColorSemaforo.AMARILLO, "71.5")))
                .hasValueSatisfying(texto -> assertThat(texto).contains("amarillo", "Requiere atención", "71.5 %"));
        assertThat(semaforo.redactar(cierre(ColorDeLaSemana.ROJO, ColorSemaforo.ROJO, "40.0")))
                .hasValueSatisfying(texto -> assertThat(texto).contains("rojo", "Con problemas", "40 %"));
    }

    @Test
    @DisplayName("el texto de sin datos no dice ningun numero ni deja marcadores sueltos")
    void sinDatosSinNumero() {
        CierreDeSemana sinDatos = new CierreDeSemana(ColorDeLaSemana.SIN_DATOS, ColorSemaforo.SIN_DATOS.etiqueta(),
                null);

        assertThat(prendidoConLosTextosDelYaml().redactar(sinDatos)).hasValueSatisfying(texto -> {
            assertThat(texto).doesNotContainPattern("\\d");
            assertThat(texto).doesNotContain("{", "}");
        });
    }

    private static CierreDeSemana cierre(ColorDeLaSemana color, ColorSemaforo original, String porcentaje) {
        return new CierreDeSemana(color, original.etiqueta(), new BigDecimal(porcentaje));
    }
}
