package com.renaser.os.chat.domain.model.emergencia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-244: el mensaje del pedido de emergencia en el chat de soporte. */
class AvisoDeEmergenciaTest {

    private final UUID solicitud = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    @DisplayName("el resumen que pidió el dueño: qué pasó, a qué día y en cuál está")
    void texto() {
        var aviso = new AvisoDeEmergencia(solicitud, "  Me operaron de urgencia ", 12, 20);

        assertThat(aviso.texto()).isEqualTo("Emergencia: Me operaron de urgencia\nPide volver al día 12 (hoy está en el día 20).");
    }

    @Test
    @DisplayName("el id del mensaje sale del pedido: el mismo pedido, el mismo id; otro pedido, otro id")
    void idDeterministico() {
        var uno = new AvisoDeEmergencia(solicitud, "x", 1, 2);
        var otraEntrega = new AvisoDeEmergencia(solicitud, "x", 1, 2);
        var otroPedido = new AvisoDeEmergencia(UUID.randomUUID(), "x", 1, 2);

        assertThat(uno.idDelMensaje()).isEqualTo(otraEntrega.idDelMensaje());
        assertThat(uno.idDelMensaje()).isNotEqualTo(otroPedido.idDelMensaje());
        assertThat(uno.idDelMensaje().value()).isNotEqualTo(solicitud);
    }

    @Test
    @DisplayName("sin texto no hay aviso")
    void sinTexto() {
        assertThatThrownBy(() -> new AvisoDeEmergencia(solicitud, " ", 1, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
