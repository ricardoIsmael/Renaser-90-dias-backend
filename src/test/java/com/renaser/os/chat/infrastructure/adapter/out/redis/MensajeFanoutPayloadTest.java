package com.renaser.os.chat.infrastructure.adapter.out.redis;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El aviso en vivo de un mensaje (D-199): el del programa no sale a nombre de la persona guardada. */
class MensajeFanoutPayloadTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T15:00:00Z");
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());

    @Test
    @DisplayName("D-199: el aviso de un mensaje del programa lleva el UUID nulo; si llevara a la aprendiz, su app lo descartaría como eco propio")
    void elDelProgramaLlevaElUuidNulo() {
        Mensaje delPrograma = Mensaje.delPrograma(MensajeId.of(UUID.randomUUID()), SOPORTE, ANA,
                ContenidoDelPrograma.texto("Te damos la bienvenida"), AHORA);

        MensajeFanoutPayload aviso = MensajeFanoutPayload.from(delPrograma);

        assertThat(aviso.senderId()).isEqualTo(new UUID(0L, 0L));
        assertThat(aviso.type()).isEqualTo("SISTEMA");
        assertThat(aviso.event()).isEqualTo("MESSAGE");
    }

    @Test
    @DisplayName("el de una persona lleva a su emisor")
    void elDeUnaPersonaLlevaASuEmisor() {
        Mensaje dePersona = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), SOPORTE, ANA, TipoMensaje.TEXTO, "hola",
                null, null, null, null, null, null, AHORA);

        assertThat(MensajeFanoutPayload.from(dePersona).senderId()).isEqualTo(ANA.value());
    }
}
