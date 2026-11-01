package com.renaser.os.habits.domain.model.categoria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodigoDeCategoriaTest {

    @Test
    @DisplayName("las cuatro de la Escuela salen con el codigo en ingles que la app publicada ya entiende")
    void deClave() {
        assertThat(CodigoDeCategoria.deClave("CUERPO")).isEqualTo("BODY");
        assertThat(CodigoDeCategoria.deClave("MENTE")).isEqualTo("MIND");
        assertThat(CodigoDeCategoria.deClave("CONSCIENCIA")).isEqualTo("CONSCIENCE");
        assertThat(CodigoDeCategoria.deClave("ESPIRITU")).isEqualTo("SPIRIT");
    }

    @Test
    @DisplayName("cualquier otra categoria sale con su clave tal cual")
    void otraClave() {
        assertThat(CodigoDeCategoria.deClave("DIPLOMADO_ESTUDIO")).isEqualTo("DIPLOMADO_ESTUDIO");
    }

    @Test
    @DisplayName("lo que manda la app se vuelve a la clave de la base, con el codigo en ingles o con la clave")
    void aClave() {
        assertThat(CodigoDeCategoria.aClave("BODY")).isEqualTo("CUERPO");
        assertThat(CodigoDeCategoria.aClave("body")).isEqualTo("CUERPO");
        assertThat(CodigoDeCategoria.aClave("CUERPO")).isEqualTo("CUERPO");
        assertThat(CodigoDeCategoria.aClave(" diplomado_estudio ")).isEqualTo("DIPLOMADO_ESTUDIO");
    }

    @Test
    @DisplayName("una categoria vacia no se acepta")
    void vacia() {
        assertThatThrownBy(() -> CodigoDeCategoria.aClave(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CodigoDeCategoria.aClave(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
