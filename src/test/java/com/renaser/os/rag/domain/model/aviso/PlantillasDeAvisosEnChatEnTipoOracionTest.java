package com.renaser.os.rag.domain.model.aviso;

import com.renaser.os.rag.domain.model.aviso.AvisosEnChat.DatosDelAviso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #71: los avisos de hábito que el acompañante deja en el panel de SER («Se te vence …», «Tu hábito … empieza a las …») salen de las plantillas REALES de
 * {@code src/main/resources/application.yaml}. Se leen de ahí —no se copian— para fijar el texto que llega a la persona con el título del catálogo en MAYÚSCULAS, en los dos tipos.
 */
class PlantillasDeAvisosEnChatEnTipoOracionTest {

    private static String plantilla(String clave) throws IOException {
        // El de src/main: en el classpath de las pruebas hay otro application.yaml (el de test) que lo tapa.
        String texto = Files.readString(Path.of("src/main/resources/application.yaml"), StandardCharsets.UTF_8);
        Matcher encontrada = Pattern.compile("(?m)^\\s*" + Pattern.quote(clave) + ":\\s*\"(.*)\"\\s*$").matcher(texto);
        assertThat(encontrada.find()).as("la plantilla " + clave + " está en src/main/resources/application.yaml").isTrue();
        return encontrada.group(1);
    }

    private static AvisosEnChat conLasPlantillasDelYaml() throws IOException {
        return new AvisosEnChat(true, Set.of("INICIO", "POR_VENCER"), Map.of(
                "INICIO", plantilla("avisos-en-chat-plantilla-inicio"),
                "POR_VENCER", plantilla("avisos-en-chat-plantilla-por-vencer")));
    }

    @Test
    @DisplayName("«Se te vence ÚLTIMA COMIDA DEL DÍA a las 18:00» se lee «Se te vence Última comida del día a las 18:00»")
    void seTeVence() throws IOException {
        String texto = conLasPlantillasDelYaml().redactar("POR_VENCER",
                new DatosDelAviso("ÚLTIMA COMIDA DEL DÍA", LocalTime.of(18, 0), 8, 15)).orElseThrow();

        assertThat(texto).contains("Se te vence Última comida del día a las 18:00. Si lo haces ahora sumas 8 puntos.");
        assertThat(texto).doesNotContain("ÚLTIMA COMIDA");
    }

    @Test
    @DisplayName("el aviso del ritual de la noche: «Ritual tierra - agua - fuego (noche)», en el inicio y en el vencimiento")
    void ritualDeLaNoche() throws IOException {
        var avisos = conLasPlantillasDelYaml();
        var datos = new DatosDelAviso("RITUAL TIERRA - AGUA - FUEGO (noche)", LocalTime.of(21, 0), 10, 15);

        String inicio = avisos.redactar("INICIO", datos).orElseThrow();
        String vence = avisos.redactar("POR_VENCER", datos).orElseThrow();

        assertThat(inicio).isEqualTo("Tu hábito Ritual tierra - agua - fuego (noche) empieza a las 21:00. Hay 10 puntos en juego.");
        assertThat(vence).startsWith("Se te vence Ritual tierra - agua - fuego (noche) a las 21:00.");
        assertThat(inicio).doesNotContain("RITUAL");
        assertThat(vence).doesNotContain("RITUAL");
    }

    @Test
    @DisplayName("ninguna plantilla del yaml deja el hábito sin pasar por {habito}: no hay un título pegado en el texto")
    void lasPlantillasUsanElMarcador() throws IOException {
        assertThat(plantilla("avisos-en-chat-plantilla-inicio")).contains("{habito}");
        assertThat(plantilla("avisos-en-chat-plantilla-por-vencer")).contains("{habito}");
    }
}
