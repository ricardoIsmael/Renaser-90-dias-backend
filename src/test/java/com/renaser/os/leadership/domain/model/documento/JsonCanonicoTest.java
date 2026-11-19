package com.renaser.os.leadership.domain.model.documento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La forma única en que se escriben los datos para sacar la huella (spec 056, FR-039). */
class JsonCanonicoTest {

    private record Ejemplo(String texto, BigDecimal numero, LocalDate dia, List<Integer> lista, Ejemplo hijo) {
    }

    @Test
    @DisplayName("spec 056 FR-039: un registro sale con sus campos en el orden en que se declararon, sin espacios y con las fechas como texto")
    void unRegistroSaleEnOrden() {
        Ejemplo ejemplo = new Ejemplo("hola", new BigDecimal("66.70"), LocalDate.of(2026, 11, 14), List.of(1, 2), null);

        assertThat(JsonCanonico.de(ejemplo))
                .isEqualTo("{\"texto\":\"hola\",\"numero\":66.7,\"dia\":\"2026-11-14\",\"lista\":[1,2],\"hijo\":null}");
    }

    @Test
    @DisplayName("spec 056 FR-039: 66.7 y 66.70 son el mismo número, y el cero no lleva decimales")
    void losNumerosSinCerosDeSobra() {
        assertThat(JsonCanonico.de(new BigDecimal("66.70"))).isEqualTo(JsonCanonico.de(new BigDecimal("66.7")));
        assertThat(JsonCanonico.de(new BigDecimal("0.0"))).isEqualTo("0");
        assertThat(JsonCanonico.de(new BigDecimal("100"))).isEqualTo("100");
    }

    @Test
    @DisplayName("spec 056 FR-039: las comillas, las barras y los saltos de línea de un texto se escapan: dos textos distintos nunca dan el mismo JSON")
    void elTextoSeEscapa() {
        assertThat(JsonCanonico.de("a\"b\\c\nd")).isEqualTo("\"a\\\"b\\\\c\\nd\"");
        assertThat(JsonCanonico.de("<script>")).isEqualTo("\"<script>\"");
    }

    @Test
    @DisplayName("spec 056 FR-039: un mapa sale con las claves ordenadas, así el orden en que se llenó no cambia la huella")
    void elMapaSaleOrdenado() {
        Map<String, Integer> uno = new LinkedHashMap<>();
        uno.put("b", 2);
        uno.put("a", 1);
        Map<String, Integer> otro = new LinkedHashMap<>();
        otro.put("a", 1);
        otro.put("b", 2);

        assertThat(JsonCanonico.de(uno)).isEqualTo("{\"a\":1,\"b\":2}").isEqualTo(JsonCanonico.de(otro));
    }

    @Test
    @DisplayName("spec 056 FR-039: un tipo que no se sabe escribir falla fuerte en vez de salir de la huella en silencio")
    void unTipoDesconocidoFalla() {
        assertThatThrownBy(() -> JsonCanonico.de(new Object())).isInstanceOf(IllegalArgumentException.class);
    }
}
