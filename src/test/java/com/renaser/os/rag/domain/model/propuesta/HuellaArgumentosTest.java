package com.renaser.os.rag.domain.model.propuesta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HuellaArgumentosTest {

    @Test
    @DisplayName("el orden en que llegan los argumentos no cambia la huella")
    void elOrdenNoImporta() {
        Map<String, String> unOrden = new LinkedHashMap<>();
        unOrden.put("registro_id", "r-1");
        unOrden.put("hora", "07:00");
        Map<String, String> otroOrden = new LinkedHashMap<>();
        otroOrden.put("hora", "07:00");
        otroOrden.put("registro_id", "r-1");

        assertThat(HuellaArgumentos.de("cambiar_horario", unOrden))
                .isEqualTo(HuellaArgumentos.de("cambiar_horario", otroOrden));
    }

    @Test
    @DisplayName("cambiar un valor, o la herramienta, cambia la huella")
    void cualquierCambioCambiaLaHuella() {
        HuellaArgumentos original = HuellaArgumentos.de("cambiar_horario", Map.of("hora", "07:00"));

        assertThat(HuellaArgumentos.de("cambiar_horario", Map.of("hora", "08:00"))).isNotEqualTo(original);
        assertThat(HuellaArgumentos.de("pausar_habito", Map.of("hora", "07:00"))).isNotEqualTo(original);
    }

    @Test
    @DisplayName("los separadores dentro de un valor no permiten fabricar la misma huella con otro mapa")
    void losSeparadoresNoColisionan() {
        assertThat(HuellaArgumentos.de("h", Map.of("a", "b=c")))
                .isNotEqualTo(HuellaArgumentos.de("h", Map.of("a=b", "c")));
    }

    @Test
    @DisplayName("es un sha256 en hexadecimal")
    void esSha256Hexadecimal() {
        assertThat(HuellaArgumentos.de("h", Map.of()).valor()).hasSize(64).matches("[0-9a-f]+");
    }
}
