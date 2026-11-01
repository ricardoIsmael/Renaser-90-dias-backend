package com.renaser.os.users.domain.model.accountrequest;

import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoDeIngresoTest {

    @Test
    @DisplayName("sin formaciones (null o vacío) es el pedido de siempre")
    void sinFormaciones() {
        assertThat(new PedidoDeIngreso(null, null, false).sinFormacionElegida()).isTrue();
        assertThat(PedidoDeIngreso.deSiempre().sinFormacionElegida()).isTrue();
        assertThat(new PedidoDeIngreso(Set.of(Formacion.DIPLOMADO), 90, true).sinFormacionElegida()).isFalse();
    }
}
