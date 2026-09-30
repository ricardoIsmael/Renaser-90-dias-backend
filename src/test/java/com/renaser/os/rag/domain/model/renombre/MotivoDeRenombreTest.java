package com.renaser.os.rag.domain.model.renombre;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E-466: el motivo de un renombre tiene que haberlo escrito la persona. */
class MotivoDeRenombreTest {

    private static final String NOMBRE = "batido de papaya";

    @Test
    @DisplayName("el motivo dicho con sus palabras, en otro orden, sin tildes o en otro mensaje, vale")
    void dichoPorLaPersona() {
        assertThat(MotivoDeRenombre.loDijoLaPersona("porque el apio me cae mal", NOMBRE,
                List.of("quiero que mi jugo verde se llame batido de papaya", "el apio me cae mal"))).isTrue();
        assertThat(MotivoDeRenombre.loDijoLaPersona("Tengo gastritis", NOMBRE,
                List.of("ponle batido de papaya, tengo gastritis"))).isTrue();
        assertThat(MotivoDeRenombre.loDijoLaPersona("me gusta más así", NOMBRE, List.of("porque me gusta mas asi")))
                .isTrue();
    }

    @Test
    @DisplayName("un motivo que la persona no escribio, o que solo repite el pedido, no vale")
    void inventadoOSoloElPedido() {
        List<String> soloElPedido = List.of("quiero que mi jugo verde se llame batido de papaya");

        assertThat(MotivoDeRenombre.loDijoLaPersona("Prefiero llamarlo batido de papaya", NOMBRE, soloElPedido))
                .isFalse();
        assertThat(MotivoDeRenombre.loDijoLaPersona("porque el apio me cae mal", NOMBRE, soloElPedido)).isFalse();
        assertThat(MotivoDeRenombre.loDijoLaPersona("quiero que se llame batido de papaya", NOMBRE, soloElPedido))
                .isFalse();
        assertThat(MotivoDeRenombre.loDijoLaPersona("porque si", NOMBRE, List.of("porque si"))).isFalse();
        assertThat(MotivoDeRenombre.loDijoLaPersona("gastritis", NOMBRE, List.of())).isFalse();
    }
}
