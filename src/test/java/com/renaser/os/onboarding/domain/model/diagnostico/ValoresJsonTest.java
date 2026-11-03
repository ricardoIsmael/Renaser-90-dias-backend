package com.renaser.os.onboarding.domain.model.diagnostico;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValoresJsonTest {

    @Test
    @DisplayName("lee y escribe listas con tildes, comillas y saltos de línea")
    void listas() {
        List<String> valores = List.of("Lo postergo, aunque tenga tiempo.", "Dijo \"no\"", "línea\ndos", "ñandú");
        assertThat(ValoresJson.lista(ValoresJson.deLista(valores))).isEqualTo(valores);
        assertThat(ValoresJson.lista(" [ ] ")).isEmpty();
        assertThat(ValoresJson.lista("[\"\\u00e9\"]")).containsExactly("é");
    }

    @Test
    @DisplayName("lee y escribe la cuadrícula")
    void objetos() {
        Map<String, Integer> filas = new LinkedHashMap<>();
        filas.put("Mis relaciones personales y familiares", 4);
        filas.put("El tiempo \"libre\"", 2);
        assertThat(ValoresJson.objetoDeEnteros(ValoresJson.deObjeto(filas))).isEqualTo(filas);
        assertThat(ValoresJson.objetoDeEnteros("{}")).isEmpty();
    }

    @Test
    @DisplayName("lo que no es del formato esperado es un error claro, no un valor a medias")
    void malFormado() {
        assertThatThrownBy(() -> ValoresJson.lista("[\"a\",")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValoresJson.lista("{\"a\":1}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValoresJson.objetoDeEnteros("{\"a\":\"x\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValoresJson.lista("[\"a\"] sobra")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValoresJson.lista(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
