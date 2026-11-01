package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las dos preguntas que cada pieza de la Comunidad se hace sobre su formación (spec 024): qué lee quien pide y dónde nace lo nuevo. */
class FormacionDelContenidoTest {

    @Test
    @DisplayName("pedida: sin parámetro es «sin filtro»; un código lo convierte; uno que no existe es un error de quien llama")
    void elParametroDeLaApi() {
        assertThat(FormacionDelContenido.pedida(null)).isNull();
        assertThat(FormacionDelContenido.pedida("")).isNull();
        assertThat(FormacionDelContenido.pedida("  ")).isNull();
        assertThat(FormacionDelContenido.pedida("diplomado")).isEqualTo(Formacion.DIPLOMADO);
        assertThatThrownBy(() -> FormacionDelContenido.pedida("MAESTRIA")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("paraLeer: quien ve una formación lee la suya; Administración lee Todas o la que elige; pedir una que no se ve no deja leer nada")
    void loQueSeLee() {
        AlcanceDeFormacion aprendiz = AlcanceDeFormacion.de(Formacion.ESCUELA);

        assertThat(FormacionDelContenido.paraLeer(aprendiz, null).formaciones()).containsExactly(Formacion.ESCUELA);
        assertThat(FormacionDelContenido.paraLeer(aprendiz, Formacion.ESCUELA).formaciones()).containsExactly(Formacion.ESCUELA);
        assertThat(FormacionDelContenido.paraLeer(aprendiz, Formacion.DIPLOMADO).esVacio()).isTrue();
        assertThat(FormacionDelContenido.paraLeer(AlcanceDeFormacion.todas(), null).esTodas()).isTrue();
        assertThat(FormacionDelContenido.paraLeer(AlcanceDeFormacion.todas(), Formacion.CERTIFICACION).formaciones())
                .containsExactly(Formacion.CERTIFICACION);
    }

    @Test
    @DisplayName("paraCrear: lo nuevo nace en la formación de quien crea; Administración tiene que elegirla; nunca «para todas» ni «sin formación»")
    void dondeNaceLoNuevo() {
        assertThat(FormacionDelContenido.paraCrear(AlcanceDeFormacion.de(Formacion.DIPLOMADO), null)).isEqualTo(Formacion.DIPLOMADO);
        assertThat(FormacionDelContenido.paraCrear(AlcanceDeFormacion.de(Formacion.DIPLOMADO), Formacion.DIPLOMADO)).isEqualTo(Formacion.DIPLOMADO);
        assertThat(FormacionDelContenido.paraCrear(AlcanceDeFormacion.todas(), Formacion.CERTIFICACION)).isEqualTo(Formacion.CERTIFICACION);

        assertThatThrownBy(() -> FormacionDelContenido.paraCrear(AlcanceDeFormacion.todas(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Elige la formación");
        assertThatThrownBy(() -> FormacionDelContenido.paraCrear(AlcanceDeFormacion.de(Formacion.ESCUELA), Formacion.DIPLOMADO))
                .isInstanceOf(ObjetoDeOtraFormacionException.class);
        assertThatThrownBy(() -> FormacionDelContenido.paraCrear(AlcanceDeFormacion.ninguna(), null))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("TipoDeComunidad: los cuatro de hoy, por código sin importar mayúsculas")
    void losTiposDeComunidad() {
        assertThat(TipoDeComunidad.values()).extracting(TipoDeComunidad::codigo)
                .containsExactly("GENERAL", "GRUPO", "SOPORTE", "DIRECTO");
        assertThat(TipoDeComunidad.desde("directo")).isEqualTo(TipoDeComunidad.DIRECTO);
        assertThatThrownBy(() -> TipoDeComunidad.desde("aula")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TipoDeComunidad.desde(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
