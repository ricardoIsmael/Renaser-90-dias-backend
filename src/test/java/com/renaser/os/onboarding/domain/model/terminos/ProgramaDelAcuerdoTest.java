package com.renaser.os.onboarding.domain.model.terminos;

import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cómo se nombra el programa en «Mi aceptación» (spec 039): lógica pura. */
class ProgramaDelAcuerdoTest {

    @Test
    @DisplayName("cada formación tiene su nombre: la Escuela se llama «Formación RENASER»")
    void nombres() {
        assertThat(ProgramaDelAcuerdo.nombre(Formacion.ESCUELA)).isEqualTo("Formación RENASER");
        assertThat(ProgramaDelAcuerdo.nombre(Formacion.DIPLOMADO)).isEqualTo("Diplomado RENASER");
        assertThat(ProgramaDelAcuerdo.nombre(Formacion.CERTIFICACION)).isEqualTo("Certificación RENASER");
    }

    @Test
    @DisplayName("la Escuela y la Certificación dicen sus 90 días; el Diplomado dice 3 meses o 1 año")
    void etiquetaConLaDuracion() {
        assertThat(ProgramaDelAcuerdo.etiqueta(Formacion.ESCUELA, 90)).isEqualTo("Formación RENASER · 90 días");
        assertThat(ProgramaDelAcuerdo.etiqueta(Formacion.CERTIFICACION, 90)).isEqualTo("Certificación RENASER · 90 días");
        assertThat(ProgramaDelAcuerdo.etiqueta(Formacion.DIPLOMADO, 90)).isEqualTo("Diplomado RENASER · 3 meses");
        assertThat(ProgramaDelAcuerdo.etiqueta(Formacion.DIPLOMADO, 365)).isEqualTo("Diplomado RENASER · 1 año");
    }

    @Test
    @DisplayName("sin programa que mostrar no se inventa un plazo")
    void sinDuracion() {
        assertThat(ProgramaDelAcuerdo.etiqueta(Formacion.ESCUELA, null)).isEqualTo("Formación RENASER");
    }

    @Test
    @DisplayName("la formación es obligatoria")
    void formacionObligatoria() {
        assertThatThrownBy(() -> ProgramaDelAcuerdo.nombre(null)).isInstanceOf(NullPointerException.class);
    }
}
