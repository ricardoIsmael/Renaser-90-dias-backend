package com.renaser.os.chat.domain.model.bienvenida;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Qué texto de bienvenida puede guardar Administración desde la app (D-210). Sin Spring. */
class TextoDeBienvenidaTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t "})
    @DisplayName("no puede quedar vacío")
    void noPuedeQuedarVacio(String texto) {
        assertThatThrownBy(() -> TextoDeBienvenida.validar(PiezaDeBienvenida.SOPORTE_FORMAL, texto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje no puede quedar vacío.");
    }

    @ParameterizedTest
    @EnumSource(value = PiezaDeBienvenida.class, names = {"SOPORTE_CON_LA_TARJETA", "SOPORTE_FORMAL", "GRUPO"})
    @DisplayName("se rechaza un texto sin {nombre}, en los tres mensajes")
    void sinNombreSeRechaza(PiezaDeBienvenida pieza) {
        String sinNombre = pieza == PiezaDeBienvenida.GRUPO
                ? "¡Hola! Te acompaña {mentor}." : "Hola, te damos la bienvenida al programa.";

        assertThatThrownBy(() -> TextoDeBienvenida.validar(pieza, sinNombre))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Al mensaje le falta {nombre}: es donde va el nombre de la persona.");
    }

    @Test
    @DisplayName("el del grupo tiene que nombrar al mentor ({mentor}), como el de hoy")
    void elDelGrupoNecesitaAlMentor() {
        assertThatThrownBy(() -> TextoDeBienvenida.validar(PiezaDeBienvenida.GRUPO, "¡Hola, {nombre}! Bienvenida al grupo."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Al mensaje le falta {mentor}: es donde va el nombre de su mentor.");
    }

    @Test
    @DisplayName("un {mentor} en el soporte no se reemplazaría por nada: se rechaza")
    void unMarcadorQueNadieReemplazaSeRechaza() {
        assertThatThrownBy(() -> TextoDeBienvenida.validar(PiezaDeBienvenida.SOPORTE_FORMAL,
                "Hola, {nombre}. Tu mentor es {mentor}."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje tiene {mentor}, que no se reemplaza por nada: en este mensaje solo se puede "
                        + "usar {nombre}.");
    }

    @Test
    @DisplayName("un marcador mal escrito ({Nombre}) sugiere el correcto")
    void unMarcadorMalEscritoSugiereElCorrecto() {
        assertThatThrownBy(() -> TextoDeBienvenida.validar(PiezaDeBienvenida.SOPORTE_FORMAL, "Hola, {Nombre}."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje tiene {Nombre}: escríbelo {nombre}, en minúsculas, para que se reemplace.");
    }

    @Test
    @DisplayName("hasta 1000 caracteres contados como Postgres: un emoji es uno")
    void largoMaximoEnCaracteres() {
        String base = "{nombre} ";
        String justo = base + "🌿".repeat(TextoDeBienvenida.LARGO_MAXIMO - base.length());
        assertThat(justo.length()).as("en UTF-16 pasa de 1000").isGreaterThan(TextoDeBienvenida.LARGO_MAXIMO);

        assertThat(TextoDeBienvenida.validar(PiezaDeBienvenida.SOPORTE_FORMAL, justo)).isEqualTo(justo);
        assertThatThrownBy(() -> TextoDeBienvenida.validar(PiezaDeBienvenida.SOPORTE_FORMAL, justo + "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje tiene 1001 caracteres: el máximo es 1000.");
    }

    @Test
    @DisplayName("se guarda sin espacios en los bordes y con sus saltos de línea")
    void quedaSinEspaciosEnLosBordes() {
        assertThat(TextoDeBienvenida.validar(PiezaDeBienvenida.GRUPO, "  ¡Hola, {nombre}!\n\nTe acompaña {mentor}.  \n"))
                .isEqualTo("¡Hola, {nombre}!\n\nTe acompaña {mentor}.");
    }

    @Test
    @DisplayName("la portada no es un texto")
    void laPortadaNoEsUnTexto() {
        assertThatThrownBy(() -> TextoDeBienvenida.validar(PiezaDeBienvenida.PORTADA, "{nombre}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
