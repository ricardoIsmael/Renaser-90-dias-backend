package com.renaser.os.leadership.domain.model.reporte;

import com.renaser.os.leadership.domain.model.reporte.OrdenDelReporte.Clasificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrdenDelReporteTest {

    record Fila(String nombre, BigDecimal valor) {
    }

    @Test
    @DisplayName("RL-23: quien no tiene muestra queda fuera del orden, no ultimo con un cero")
    void sinMuestraFueraDelOrden() {
        List<Fila> filas = List.of(new Fila("Marta Ruiz", null), new Fila("Ana Quispe", new BigDecimal("100")),
                new Fila("Luis Romero", new BigDecimal("75")));

        Clasificacion<Fila> c = OrdenDelReporte.clasificar(filas, Fila::valor, Fila::nombre);

        assertThat(c.ordenados()).extracting(Fila::nombre).containsExactly("Ana Quispe", "Luis Romero");
        assertThat(c.sinMuestra()).extracting(Fila::nombre).containsExactly("Marta Ruiz");
    }

    @Test
    @DisplayName("se ordena con el valor sin redondear: 79.96 va antes que 79.95 aunque en pantalla los dos digan 80")
    void sinRedondear() {
        List<Fila> filas = List.of(new Fila("B", new BigDecimal("79.95")), new Fila("A", new BigDecimal("79.96")));

        assertThat(OrdenDelReporte.clasificar(filas, Fila::valor, Fila::nombre).ordenados())
                .extracting(Fila::nombre).containsExactly("A", "B");
    }

    @Test
    @DisplayName("empate: por nombre, con orden alfabetico en castellano")
    void empatePorNombre() {
        List<Fila> filas = List.of(new Fila("Úrsula", BigDecimal.TEN), new Fila("Óscar", BigDecimal.TEN),
                new Fila("Ana", BigDecimal.TEN));

        assertThat(OrdenDelReporte.clasificar(filas, Fila::valor, Fila::nombre).ordenados())
                .extracting(Fila::nombre).containsExactly("Ana", "Óscar", "Úrsula");
    }
}
