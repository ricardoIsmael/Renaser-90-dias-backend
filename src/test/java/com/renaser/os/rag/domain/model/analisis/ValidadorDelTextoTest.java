package com.renaser.os.rag.domain.model.analisis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que un texto del análisis tiene que cumplir para mostrarse (spec 031, FR-008, FR-009, SC-002): cifras que son las calculadas, sin
 * diagnóstico, sin compararse, sin frases citadas y sin pasarse de emojis. Pura.
 */
class ValidadorDelTextoTest {

    private static final Set<String> CIFRAS = Set.of("9", "12", "75", "47", "6.4", "3");

    private static TextoDelAnalisis texto(String movio, String fue, String estuvo, String... pasos) {
        return new TextoDelAnalisis(movio, fue, estuvo, List.of(pasos));
    }

    private static TextoDelAnalisis bueno() {
        return texto("Cumpliste 9 de 12 acciones esta semana, el 75 % de lo que planificaste. Entrenar fue lo que más movió.",
                "Llamar al cliente grande quedó pendiente; pasa, y se puede retomar.",
                "Tu energía promedio fue 6.4 y bajó por las tardes.", "Empieza el lunes por la llamada al cliente.");
    }

    @Test
    @DisplayName("un texto con las cifras calculadas, tuteo cálido y sin diagnóstico se puede mostrar")
    void textoBueno() {
        assertThat(ValidadorDelTexto.incumplimientos(bueno(), CIFRAS)).isEmpty();
    }

    @Test
    @DisplayName("FR-009 / SC-002: una cifra que no es una de las calculadas descarta el texto")
    void cifraAjena() {
        TextoDelAnalisis ajeno = texto("Cumpliste 10 de 12 acciones.", "Nada se fue.", "Bien.", "Sigue así.");

        assertThat(ValidadorDelTexto.incumplimientos(ajeno, CIFRAS)).containsExactly("una cifra que no es una de las calculadas");
    }

    @Test
    @DisplayName("la coma decimal y los ceros de más no cambian el número: «6,40» es el 6.4 calculado")
    void numerosNormalizados() {
        assertThat(ValidadorDelTexto.numerosEn("Tu energía fue 6,40 y el 075 %, de 12.0"))
                .containsExactlyInAnyOrder("6.4", "75", "12");
        assertThat(ValidadorDelTexto.incumplimientos(texto("Energía 6,40.", "Nada.", "Bien.", "Sigue."), CIFRAS)).isEmpty();
    }

    @Test
    @DisplayName("FR-008: ni un cuadro clínico ni una palabra de diagnóstico, con o sin tildes y en mayúsculas")
    void diagnostico() {
        for (String frase : List.of("Parece una Depresión.", "Tienes ANSIEDAD.", "Es un trastorno del sueño.", "Eso es burnout.",
                "Mi diagnóstico es claro.", "Hay signos de bipolaridad.", "Habla con un psicólogo.")) {
            assertThat(ValidadorDelTexto.incumplimientos(texto("Bien.", frase, "Bien.", "Sigue."), CIFRAS))
                    .as(frase).containsExactly("nombra un cuadro clínico o diagnostica");
        }
    }

    @Test
    @DisplayName("FR-008: no se compara con otras personas ni las nombra como grupo")
    void comparaciones() {
        TextoDelAnalisis comparado = texto("Bien.", "Estás por debajo de tus compañeros.", "Bien.", "Sigue.");

        assertThat(ValidadorDelTexto.incumplimientos(comparado, CIFRAS)).containsExactly("compara con otras personas");
    }

    @Test
    @DisplayName("031-3 A: «Cómo estuviste» no cita frases de lo que la persona escribió: ninguna comilla")
    void sinFrasesCitadas() {
        TextoDelAnalisis citado = texto("Bien.", "Nada.", "Escribiste «estoy cansada» varias veces.", "Sigue.");
        TextoDelAnalisis conComillasRectas = texto("Bien.", "Nada.", "Escribiste \"cansada\".", "Sigue.");
        TextoDelAnalisis tituloEntreComillas = texto("Hiciste «Entrenar» y fue lo que más movió.", "Nada.", "Sin respuestas del Código Renaser.", "Sigue.");

        assertThat(ValidadorDelTexto.incumplimientos(citado, CIFRAS)).containsExactly("cita frases en «Cómo estuviste»");
        assertThat(ValidadorDelTexto.incumplimientos(conComillasRectas, CIFRAS)).containsExactly("cita frases en «Cómo estuviste»");
        assertThat(ValidadorDelTexto.incumplimientos(tituloEntreComillas, CIFRAS)).isEmpty();
    }

    @Test
    @DisplayName("hasta tres emojis; el cuarto descarta el texto")
    void emojis() {
        TextoDelAnalisis tres = texto("Buen avance 💪🔥", "Nada.", "Bien 🙂.", "Sigue.");
        TextoDelAnalisis cuatro = texto("Buen avance 💪🔥🙌", "Nada.", "Bien 🙂.", "Sigue.");

        assertThat(ValidadorDelTexto.incumplimientos(tres, CIFRAS)).isEmpty();
        assertThat(ValidadorDelTexto.incumplimientos(cuatro, CIFRAS)).containsExactly("tiene más de 3 emojis");
    }

    @Test
    @DisplayName("un paso numerado con un dígito que no es una cifra calculada también se descarta")
    void pasosNumerados() {
        TextoDelAnalisis numerado = texto("Bien.", "Nada.", "Bien.", "1. Llama al cliente", "2. Entrena");

        assertThat(ValidadorDelTexto.incumplimientos(numerado, CIFRAS)).isNotEmpty();
    }
}
