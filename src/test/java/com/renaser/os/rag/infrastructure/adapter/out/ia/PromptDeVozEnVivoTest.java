package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El prompt de la sesion en vivo (D-162) es el del acompanante + el bloque de voz + el bloque en
 * vivo (E-239). Se renderizan los {@code .st} reales: una llave suelta en la prosa falla aca y no
 * al abrir el orbe.
 */
class PromptDeVozEnVivoTest {

    private final PromptDeVozEnVivo prompt = new PromptDeVozEnVivo();

    @Test
    @DisplayName("es el acompanante, con el bloque de voz y el bloque en vivo, sin comentarios de plantilla")
    void armaLosTresBloques() {
        String texto = prompt.para(new SituacionDelAprendiz(17, 1));

        assertThat(texto).contains("Eres Renasia")
                .contains("Hoy es su dia 17 de 90")
                .contains("Esta respuesta se va a escuchar")
                .contains("conversando por voz, en tiempo real")
                .contains("Hablas SIEMPRE en espanol")
                .contains("No alcance a oirte")
                // Directo (pedido del dueno, 2026-09-24): sin preambulos y sin describir la propuesta.
                .contains("una o dos frases")
                .contains("Te deje la propuesta abajo")
                .contains("Nunca un \"no es posible\" a secas")
                .doesNotContain("!}")
                .doesNotContain("Bloque EN VIVO");
        // Los limites clinicos y de crisis siguen ahi: el bloque en vivo solo suma forma e idioma.
        assertThat(texto).contains("No diagnosticas").contains("Linea 113");
    }

    @Test
    @DisplayName("sin situacion (no cursa el programa) igual arma el prompt y no inventa un dia")
    void sinSituacion() {
        String texto = prompt.para(null);

        assertThat(texto).contains("no esta cursando el programa").doesNotContain("Hoy es su dia");
    }
}
