package com.renaser.os.habits.domain.model.categoria;

import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoriaHabitoTest {

    private static final Instant AHORA = Instant.parse("2026-10-31T15:00:00Z");

    @Test
    @DisplayName("una categoria nueva nace activa, con su formacion, su orden y su icono")
    void nace() {
        CategoriaHabito c = CategoriaHabito.crear("DIPLOMADO_ESTUDIO", Formacion.DIPLOMADO, "  Estudio ", 3, "bookOpen", AHORA);

        assertThat(c.clave()).isEqualTo("DIPLOMADO_ESTUDIO");
        assertThat(c.formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(c.nombre()).isEqualTo("Estudio");
        assertThat(c.orden()).isEqualTo(3);
        assertThat(c.icono()).isEqualTo("bookOpen");
        assertThat(c.activa()).isTrue();
    }

    @Test
    @DisplayName("sin icono elegido queda sin icono (la app usa uno generico)")
    void sinIcono() {
        assertThat(CategoriaHabito.crear("ESCUELA_X", Formacion.ESCUELA, "Uno", 1, " ", AHORA).icono()).isNull();
    }

    @Test
    @DisplayName("un icono que la app no dibuja se rechaza")
    void iconoDesconocido() {
        assertThatThrownBy(() -> CategoriaHabito.crear("ESCUELA_X", Formacion.ESCUELA, "Uno", 1, "inventado", AHORA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ícono");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "A", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    @DisplayName("el nombre va de 2 a 40 caracteres")
    void nombreFueraDeRango(String nombre) {
        assertThatThrownBy(() -> CategoriaHabito.crear("ESCUELA_X", Formacion.ESCUELA, nombre, 1, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "minuscula", "CON ESPACIO", "1EMPIEZA"})
    @DisplayName("la clave es en mayusculas, sin espacios y de 3 caracteres o mas")
    void claveInvalida(String clave) {
        assertThatThrownBy(() -> CategoriaHabito.crear(clave, Formacion.ESCUELA, "Uno", 1, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la clave de una categoria nueva sale de su formacion y su nombre, sin tildes ni signos")
    void claveNueva() {
        assertThat(CategoriaHabito.claveNueva(Formacion.DIPLOMADO, "Estudio profundo")).isEqualTo("DIPLOMADO_ESTUDIO_PROFUNDO");
        assertThat(CategoriaHabito.claveNueva(Formacion.CERTIFICACION, "Práctica y ética")).isEqualTo("CERTIFICACION_PRACTICA_Y_ETICA");
        assertThat(CategoriaHabito.claveNueva(Formacion.ESCUELA, "¿¿Mente??")).isEqualTo("ESCUELA_MENTE");
    }

    @Test
    @DisplayName("con el nombre más largo permitido (40) la clave sigue siendo válida y de 60 caracteres o menos")
    void claveLarga() {
        String clave = CategoriaHabito.claveNueva(Formacion.CERTIFICACION, "a".repeat(19) + " " + "b".repeat(20));
        assertThat(clave).isEqualTo("CERTIFICACION_" + "A".repeat(19) + "_" + "B".repeat(20));
        assertThat(clave.length()).isLessThanOrEqualTo(60);
        CategoriaHabito.crear(clave, Formacion.CERTIFICACION, "Larga", 1, null, AHORA);
    }

    @Test
    @DisplayName("dos nombres que solo difieren en mayusculas o tildes son el mismo")
    void nombreNormalizado() {
        assertThat(CategoriaHabito.nombreNormalizado(" Emoción ")).isEqualTo(CategoriaHabito.nombreNormalizado("EMOCION"));
    }

    @Test
    @DisplayName("renombrar, cambiar el icono, mover, desactivar y activar actualizan la hora del cambio")
    void cambios() {
        CategoriaHabito c = CategoriaHabito.crear("ESCUELA_X", Formacion.ESCUELA, "Uno", 1, "heart", AHORA);
        Instant despues = AHORA.plusSeconds(60);

        c.renombrar("Dos", despues);
        c.cambiarIcono("brain", despues);
        c.mover(5, despues);
        c.desactivar(despues);

        assertThat(c.nombre()).isEqualTo("Dos");
        assertThat(c.icono()).isEqualTo("brain");
        assertThat(c.orden()).isEqualTo(5);
        assertThat(c.activa()).isFalse();
        assertThat(c.actualizadoEn()).isEqualTo(despues);
        c.activar(despues);
        assertThat(c.activa()).isTrue();
    }

    @Test
    @DisplayName("un icono vacio al cambiarlo quita el icono")
    void quitarIcono() {
        CategoriaHabito c = CategoriaHabito.crear("ESCUELA_X", Formacion.ESCUELA, "Uno", 1, "heart", AHORA);
        c.cambiarIcono("", AHORA);
        assertThat(c.icono()).isNull();
    }

    @Test
    @DisplayName("el orden no puede ser negativo")
    void ordenNegativo() {
        CategoriaHabito c = CategoriaHabito.crear("ESCUELA_X", Formacion.ESCUELA, "Uno", 1, null, AHORA);
        assertThatThrownBy(() -> c.mover(-1, AHORA)).isInstanceOf(IllegalArgumentException.class);
    }
}
