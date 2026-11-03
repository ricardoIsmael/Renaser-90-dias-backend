package com.renaser.os.rocks.domain.model.rocamensual;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 026: qué se mide en un objetivo que nació del diagnóstico, leído de la unidad que la persona escribió. */
class DatosDelEjeDelObjetivoTest {

    private static MetaCuantitativa meta(String desde, String hasta, String unidad) {
        return MetaCuantitativa.desde(new BigDecimal(desde), new BigDecimal(hasta), unidad);
    }

    @Test
    @DisplayName("Cuerpo en kilos es un nivel con el tope saludable del peso")
    void cuerpoEnKilos() {
        DatosDelEje datos = DatosDelEje.delObjetivo(EjeObjetivo.CUERPO, meta("92", "85", "kg"));

        assertThat(datos.magnitud()).isEqualTo(Magnitud.NIVEL);
        assertThat(datos.topePorMes()).isNotNull();
        assertThat(datos.lineaBase()).isEqualByComparingTo("92");
        assertThat(datos.meta()).isEqualByComparingTo("85");
        assertThat(datos.unidad()).isEqualTo("kg");
    }

    @Test
    @DisplayName("Cuerpo en otra unidad (días de ejercicio) es un nivel sin tope")
    void cuerpoEnOtraUnidad() {
        DatosDelEje datos = DatosDelEje.delObjetivo(EjeObjetivo.CUERPO, meta("1", "4", "días por semana"));

        assertThat(datos.magnitud()).isEqualTo(Magnitud.NIVEL);
        assertThat(datos.topePorMes()).isNull();
    }

    @Test
    @DisplayName("una unidad que parece escala («puntos», «/10») se trata como escala, con cifras enteras")
    void unidadDeEscala() {
        assertThat(DatosDelEje.delObjetivo(EjeObjetivo.CUERPO, meta("3", "7", "puntos")).magnitud()).isEqualTo(Magnitud.ESCALA);
        assertThat(Magnitud.deLaUnidad("/10")).isEqualTo(Magnitud.ESCALA);
        assertThat(Magnitud.deLaUnidad("kg")).isEqualTo(Magnitud.NIVEL);
        assertThat(Magnitud.deLaUnidad(null)).isEqualTo(Magnitud.NIVEL);
    }

    @Test
    @DisplayName("Negocio es un nivel sin tope: nadie sabe si 15 000 soles al mes son mucho o poco")
    void negocio() {
        DatosDelEje datos = DatosDelEje.delObjetivo(EjeObjetivo.TRABAJO, meta("10000", "15000", "S/ al mes"));

        assertThat(datos.magnitud()).isEqualTo(Magnitud.NIVEL);
        assertThat(datos.topePorMes()).isNull();
        assertThat(datos.unidad()).isEqualTo("S/ al mes");
    }

    @Test
    @DisplayName("Relaciones es siempre la escala del 1 al 10, con su unidad /10")
    void relaciones() {
        DatosDelEje datos = DatosDelEje.delObjetivo(EjeObjetivo.RELACIONES, meta("4", "8", "/10"));

        assertThat(datos.magnitud()).isEqualTo(Magnitud.ESCALA);
        assertThat(datos.unidad()).isEqualTo(DatosDelEje.UNIDAD_ESCALA);
        assertThat(datos.topePorMes()).isNull();
        assertThat(datos.lineaBase()).isEqualByComparingTo("4");
        assertThat(datos.meta()).isEqualByComparingTo("8");
    }
}
