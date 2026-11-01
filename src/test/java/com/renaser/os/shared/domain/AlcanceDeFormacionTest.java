package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlcanceDeFormacionTest {

    @Test
    @DisplayName("Todas incluye las tres; ninguna no incluye ninguna")
    void todasYNinguna() {
        assertThat(AlcanceDeFormacion.todas().esTodas()).isTrue();
        for (Formacion formacion : Formacion.values()) {
            assertThat(AlcanceDeFormacion.todas().incluye(formacion)).isTrue();
            assertThat(AlcanceDeFormacion.ninguna().incluye(formacion)).isFalse();
        }
        assertThat(AlcanceDeFormacion.ninguna().esVacio()).isTrue();
        assertThat(AlcanceDeFormacion.de(Set.<Formacion>of()).esVacio()).isTrue();
    }

    @Test
    @DisplayName("Acotar a una formación pedida deja solo esa si el alcance la incluye; si no, queda vacío (nunca se amplía)")
    void acotadoNuncaAmplia() {
        assertThat(AlcanceDeFormacion.todas().acotadoA(Formacion.DIPLOMADO).formaciones()).containsExactly(Formacion.DIPLOMADO);
        assertThat(AlcanceDeFormacion.todas().acotadoA(null)).isEqualTo(AlcanceDeFormacion.todas());
        assertThat(AlcanceDeFormacion.de(Formacion.ESCUELA).acotadoA(Formacion.DIPLOMADO).esVacio()).isTrue();
        assertThat(AlcanceDeFormacion.de(Formacion.ESCUELA).acotadoA(Formacion.ESCUELA).formaciones()).containsExactly(Formacion.ESCUELA);
    }

    @Test
    @DisplayName("Una sola formación es «única»; con varias o ninguna no hay única")
    void unica() {
        assertThat(AlcanceDeFormacion.de(Formacion.CERTIFICACION).unica()).contains(Formacion.CERTIFICACION);
        assertThat(AlcanceDeFormacion.todas().unica()).isEmpty();
        assertThat(AlcanceDeFormacion.ninguna().unica()).isEmpty();
    }

    @Test
    @DisplayName("Pedir algo de una formación fuera del alcance es «de otra formación» (404)")
    void exigirQueIncluya() {
        AlcanceDeFormacion escuela = AlcanceDeFormacion.de(Formacion.ESCUELA);
        escuela.exigirQueIncluya(Formacion.ESCUELA);
        assertThatThrownBy(() -> escuela.exigirQueIncluya(Formacion.DIPLOMADO)).isInstanceOf(ObjetoDeOtraFormacionException.class)
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    @DisplayName("Los códigos de las formaciones se leen sin importar mayúsculas y lo desconocido se rechaza")
    void desdeElCodigo() {
        assertThat(Formacion.desde("diplomado")).isEqualTo(Formacion.DIPLOMADO);
        assertThat(Formacion.desde(" ESCUELA ")).isEqualTo(Formacion.ESCUELA);
        assertThatThrownBy(() -> Formacion.desde("MAESTRIA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Formacion.desde(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
