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
    @DisplayName("en el Día 0 no hay día pedido: pide ayuda")
    void textoDelDiaCero() {
        assertThat(new AvisoDeEmergencia(solicitud, "Me enfermé", null, 0).texto())
                .isEqualTo("Emergencia: Me enfermé\nPide ayuda (todavía está en el día 0).");
    }

    @Test
    @DisplayName("la respuesta al resolverlo: con el día aplicado, o sin cambio con el día de hoy")
    void respuesta() {
        assertThat(new RespuestaAEmergencia(solicitud, 12, 12).texto())
                .isEqualTo("Listo: volviste al día 12. Si necesitas algo más, escríbenos aquí.");
        assertThat(new RespuestaAEmergencia(solicitud, null, 20).texto())
                .isEqualTo("Revisamos tu pedido; seguimos en el día 20. Escríbenos si necesitas algo.");
        assertThat(new RespuestaAEmergencia(solicitud, null, 20).idDelMensaje())
                .isEqualTo(new RespuestaAEmergencia(solicitud, 12, 12).idDelMensaje())
                .isNotEqualTo(new AvisoDeEmergencia(solicitud, "x", 1, 2).idDelMensaje());
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
