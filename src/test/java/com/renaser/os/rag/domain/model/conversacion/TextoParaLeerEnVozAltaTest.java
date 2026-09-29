package com.renaser.os.rag.domain.model.conversacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextoParaLeerEnVozAltaTest {

    @Test
    @DisplayName("D-227: los emojis del acompanante no llegan a la voz")
    void quitaLosEmojisDelAcompanante() {
        assertThat(TextoParaLeerEnVozAlta.sinEmojis("¡Bien hecho! 💪 Ya tienes tu jugo verde ✅"))
                .isEqualTo("¡Bien hecho! Ya tienes tu jugo verde");
        assertThat(TextoParaLeerEnVozAlta.sinEmojis("🌿 Respira hondo. Vuelve mañana 🌅."))
                .isEqualTo("Respira hondo. Vuelve mañana.");
        assertThat(TextoParaLeerEnVozAlta.sinEmojis("Tu racha sigue 🔥🔥, ¡a seguir ✨!"))
                .isEqualTo("Tu racha sigue, ¡a seguir!");
    }

    @Test
    @DisplayName("quita tambien los emojis compuestos: tono de piel, union, selector y banderas")
    void quitaLosCompuestos() {
        assertThat(TextoParaLeerEnVozAlta.sinEmojis("Vamos 🙌🏽 equipo 👩‍👩‍👧 de ❤️ Peru 🇵🇪"))
                .isEqualTo("Vamos equipo de Peru");
    }

    @Test
    @DisplayName("no toca letras con tilde, numeros, horas ni los numeros de ayuda")
    void noTocaElTextoNormal() {
        String texto = "Llama ya al 106 o a la Linea 113, opcion 5. Son las 8:30, año 2026: ¿cómo estás?";

        assertThat(TextoParaLeerEnVozAlta.sinEmojis(texto)).isEqualTo(texto);
        assertThat(TextoParaLeerEnVozAlta.sinEmojis("Paso 1️⃣ y #2 *hoy*")).isEqualTo("Paso 1 y #2 *hoy*");
    }

    @Test
    @DisplayName("un texto que era solo emojis queda vacio, y null tambien")
    void soloEmojisQuedaVacio() {
        assertThat(TextoParaLeerEnVozAlta.sinEmojis(" 💪✨ ")).isEmpty();
        assertThat(TextoParaLeerEnVozAlta.sinEmojis(null)).isEmpty();
    }
}
