package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.AprendizDelMentor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrdenPorRiesgoTest {

    private static AprendizDelMentor aprendiz(String nombre, ColorDeLaFila estado, String porcentaje) {
        return new AprendizDelMentor(nombre, "Fénix", estado, porcentaje == null ? null : new BigDecimal(porcentaje), "IGUAL");
    }

    @Test
    @DisplayName("spec 056 FR-036: por riesgo: rojo, ámbar, sin datos y verde; en cada color el menor porcentaje primero, y por nombre en castellano")
    void ordenPorRiesgo() {
        List<AprendizDelMentor> lista = new ArrayList<>(List.of(
                aprendiz("Diego", ColorDeLaFila.VERDE, "95"),
                aprendiz("Zoila", ColorDeLaFila.ROJO, "40"),
                aprendiz("Carla", ColorDeLaFila.SIN_DATOS, null),
                aprendiz("Ángela", ColorDeLaFila.ROJO, "40"),
                aprendiz("Beto", ColorDeLaFila.AMARILLO, "70"),
                aprendiz("Rosa", ColorDeLaFila.ROJO, "10")));

        lista.sort(OrdenPorRiesgo.orden());

        assertThat(lista).extracting(AprendizDelMentor::nombre).containsExactly("Rosa", "Ángela", "Zoila", "Beto", "Carla", "Diego");
    }
}
