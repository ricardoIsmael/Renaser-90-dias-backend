package com.renaser.os.rocks.domain.model.rocamensual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La traduccion de lo que el Mapa guarda como texto a como se comporta el numero.
 *
 * <p>Los valores que se prueban aca son <b>exactamente</b> los que siembra la V41 en
 * {@code opciones_pregunta_onboarding}. Si alguien agrega una opcion nueva al Mapa y no la traduce,
 * cae en "todavia no eligio" y la persona se queda sin cifra sin saber por que: por eso el test
 * recorre la lista entera.
 */
class MagnitudTest {

    @Test
    @DisplayName("los ocho tipos de salud de la V41 estan traducidos")
    void losOchoTiposDeSalud() {
        assertThat(Magnitud.deSalud("peso", "kg")).contains(Magnitud.NIVEL);
        assertThat(Magnitud.deSalud("medidas", "cm")).contains(Magnitud.NIVEL);
        assertThat(Magnitud.deSalud("fuerza", "kg")).contains(Magnitud.NIVEL);
        assertThat(Magnitud.deSalud("resistencia", "min")).contains(Magnitud.NIVEL);
        assertThat(Magnitud.deSalud("sueno", "h")).contains(Magnitud.NIVEL);
        assertThat(Magnitud.deSalud("otro", "km")).contains(Magnitud.NIVEL);
        assertThat(Magnitud.deSalud("energia", "/10")).contains(Magnitud.ESCALA);
        assertThat(Magnitud.deSalud("condicion_clinica", "mg/dL")).contains(Magnitud.CLINICO);
    }

    @Test
    @DisplayName("\"otro\" con unidad de puntaje se trata como escala")
    void otroConUnidadDeEscala() {
        assertThat(Magnitud.deSalud("otro", "/10")).contains(Magnitud.ESCALA);
        assertThat(Magnitud.deSalud("otro", "puntos")).contains(Magnitud.ESCALA);
        assertThat(Magnitud.deSalud("otro", "pts")).contains(Magnitud.ESCALA);
    }

    @Test
    @DisplayName("sin tipo, o con uno desconocido, no se adivina")
    void sinTipoNoSeAdivina() {
        assertThat(Magnitud.deSalud(null, "kg")).isEmpty();
        assertThat(Magnitud.deSalud("  ", "kg")).isEmpty();
        assertThat(Magnitud.deSalud("telepatia", "kg")).isEmpty();
    }

    @Test
    @DisplayName("en negocio lo decide el periodo, no el tipo")
    void enNegocioMandaElPeriodo() {
        assertThat(Magnitud.deNegocio("facturacion", "mensual")).contains(Magnitud.NIVEL);
        assertThat(Magnitud.deNegocio("facturacion", "semanal")).contains(Magnitud.NIVEL);
        assertThat(Magnitud.deNegocio("facturacion", "acumulado_dia_90")).contains(Magnitud.ACUMULADO);
        assertThat(Magnitud.deNegocio("ventas", "acumulado_dia_90")).contains(Magnitud.ACUMULADO);
    }

    @Test
    @DisplayName("falta el tipo o falta el periodo: los dos bloquean igual")
    void negocioNecesitaLosDos() {
        assertThat(Magnitud.deNegocio(null, "mensual")).isEmpty();
        assertThat(Magnitud.deNegocio("facturacion", null)).isEmpty();
    }

    @Test
    @DisplayName("solo un nivel o un acumulado admiten decimales")
    void decimales() {
        assertThat(Magnitud.NIVEL.admiteDecimales()).isTrue();
        assertThat(Magnitud.ACUMULADO.admiteDecimales()).isTrue();
        assertThat(Magnitud.ESCALA.admiteDecimales()).isFalse();
        assertThat(Magnitud.CLINICO.admiteDecimales()).isFalse();
    }
}
