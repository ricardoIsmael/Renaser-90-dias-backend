package com.renaser.os.rag.domain.model.analisis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La forma del análisis: cuatro partes, de uno a tres pasos, ninguna vacía ni larga (spec 031, FR-007). */
class TextoDelAnalisisTest {

    @Test
    @DisplayName("las partes se recortan y los pasos quedan en su orden")
    void recortaYConservaElOrden() {
        TextoDelAnalisis texto = new TextoDelAnalisis(" a ", "b", "c ", List.of("uno", "dos", "tres"));

        assertThat(texto.loQueMasMovio()).isEqualTo("a");
        assertThat(texto.comoEstuviste()).isEqualTo("c");
        assertThat(texto.proximosPasos()).containsExactly("uno", "dos", "tres");
        assertThat(texto.partes()).containsExactly("a", "b", "c", "uno", "dos", "tres");
    }

    @Test
    @DisplayName("falta una parte, o hay cero o cuatro pasos: no es un análisis")
    void formaInvalida() {
        assertThatThrownBy(() -> new TextoDelAnalisis("", "b", "c", List.of("x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextoDelAnalisis("a", "b", "c", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextoDelAnalisis("a", "b", "c", List.of("1", "2", "3", "4"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextoDelAnalisis("a", "b", "c", List.of(" "))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una parte demasiado larga no se guarda: se lee en menos de un minuto")
    void demasiadoLargo() {
        String largo = "x".repeat(TextoDelAnalisis.MAXIMO_POR_PARTE + 1);

        assertThatThrownBy(() -> new TextoDelAnalisis(largo, "b", "c", List.of("x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextoDelAnalisis("a", "b", "c", List.of("y".repeat(TextoDelAnalisis.MAXIMO_POR_PASO + 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
