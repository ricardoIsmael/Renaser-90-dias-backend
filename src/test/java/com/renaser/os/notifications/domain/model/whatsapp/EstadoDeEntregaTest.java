package com.renaser.os.notifications.domain.model.whatsapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoDeEntregaTest {

    // spec 041 FR-022
    @Test
    @DisplayName("el estado solo avanza: un aviso tardio («enviado» despues de «leido») no deshace nada")
    void soloAvanza() {
        assertThat(EstadoDeEntrega.ACEPTADO.puedeAvanzarA(EstadoDeEntrega.ENVIADO)).isTrue();
        assertThat(EstadoDeEntrega.ENVIADO.puedeAvanzarA(EstadoDeEntrega.ENTREGADO)).isTrue();
        assertThat(EstadoDeEntrega.ENTREGADO.puedeAvanzarA(EstadoDeEntrega.LEIDO)).isTrue();
        assertThat(EstadoDeEntrega.LEIDO.puedeAvanzarA(EstadoDeEntrega.ENVIADO)).isFalse();
        assertThat(EstadoDeEntrega.LEIDO.puedeAvanzarA(EstadoDeEntrega.ENTREGADO)).isFalse();
        assertThat(EstadoDeEntrega.ENTREGADO.puedeAvanzarA(EstadoDeEntrega.ACEPTADO)).isFalse();
        assertThat(EstadoDeEntrega.ENTREGADO.puedeAvanzarA(EstadoDeEntrega.ENTREGADO)).isFalse();
    }

    // spec 041 FR-022
    @Test
    @DisplayName("lo omitido y lo fallido son finales; algo ya entregado no puede pasar a fallido")
    void losFinales() {
        for (EstadoDeEntrega nuevo : EstadoDeEntrega.values()) {
            assertThat(EstadoDeEntrega.OMITIDO.puedeAvanzarA(nuevo)).as("OMITIDO -> " + nuevo).isFalse();
            assertThat(EstadoDeEntrega.FALLIDO.puedeAvanzarA(nuevo)).as("FALLIDO -> " + nuevo).isFalse();
        }
        assertThat(EstadoDeEntrega.ENTREGADO.puedeAvanzarA(EstadoDeEntrega.FALLIDO)).isFalse();
        assertThat(EstadoDeEntrega.ENVIADO.puedeAvanzarA(EstadoDeEntrega.FALLIDO)).isTrue();
        assertThat(EstadoDeEntrega.ENVIANDO.puedeAvanzarA(EstadoDeEntrega.OMITIDO)).as("lo omitido nace omitido, no se llega ahi").isFalse();
    }

    // spec 041 FR-022
    @Test
    @DisplayName("«no se supo» (un corte) se resuelve cuando el proveedor avisa despues el estado de verdad")
    void noSeSupoSeResuelve() {
        assertThat(EstadoDeEntrega.NO_SE_SUPO.puedeAvanzarA(EstadoDeEntrega.ENTREGADO)).isTrue();
        assertThat(EstadoDeEntrega.NO_SE_SUPO.puedeAvanzarA(EstadoDeEntrega.FALLIDO)).isTrue();
        assertThat(EstadoDeEntrega.ENVIANDO.puedeAvanzarA(EstadoDeEntrega.NO_SE_SUPO)).isTrue();
    }

    // spec 041 FR-022
    @Test
    @DisplayName("lee los estados del proveedor y no inventa uno para lo que no conoce")
    void estadosDelProveedor() {
        assertThat(EstadoDeEntrega.delProveedor("accepted")).contains(EstadoDeEntrega.ACEPTADO);
        assertThat(EstadoDeEntrega.delProveedor("SENT")).contains(EstadoDeEntrega.ENVIADO);
        assertThat(EstadoDeEntrega.delProveedor("delivered")).contains(EstadoDeEntrega.ENTREGADO);
        assertThat(EstadoDeEntrega.delProveedor("read")).contains(EstadoDeEntrega.LEIDO);
        assertThat(EstadoDeEntrega.delProveedor("failed")).contains(EstadoDeEntrega.FALLIDO);
        assertThat(EstadoDeEntrega.delProveedor("deleted")).isEmpty();
        assertThat(EstadoDeEntrega.delProveedor(null)).isEmpty();
    }
}
