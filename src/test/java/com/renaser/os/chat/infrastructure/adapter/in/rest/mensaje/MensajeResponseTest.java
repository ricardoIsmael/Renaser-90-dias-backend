package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
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

/**
 * El contrato del mensaje del programa en el cable (D-199/D-204), el que la app nueva pinta como
 * «Formación Renaser» con el fénix y el que el APK publicado tiene que poder validar.
 */
class MensajeResponseTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T15:00:00Z");
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final String PROGRAMA = "00000000-0000-0000-0000-000000000000";

    @Test
    @DisplayName("D-199: un mensaje del programa sale SYSTEM, firmado por el UUID nulo y «Formación Renaser», nunca por la persona guardada ni con senderId null")
    void elMensajeDelProgramaEnElListado() {
        Mensaje formal = Mensaje.delPrograma(MensajeId.of(UUID.randomUUID()), SOPORTE, ANA,
                ContenidoDelPrograma.texto("Hola, Ana. Te damos la bienvenida"), AHORA);

        MensajeResponse cable = MensajeResponse.from(new MensajeEnriquecido(formal, "Formación Renaser", null, null, null));

        assertThat(cable.type()).isEqualTo("SYSTEM");
        assertThat(cable.senderId()).isEqualTo(PROGRAMA).isNotEqualTo(ANA.toString());
        assertThat(cable.senderName()).isEqualTo("Formación Renaser");
        assertThat(cable.senderAvatarUrl()).isNull();
        assertThat(cable.text()).isEqualTo("Hola, Ana. Te damos la bienvenida");
        assertThat(cable.mediaPath()).isNull();
    }

    @Test
    @DisplayName("D-199: la tarjeta es un SYSTEM con su imagen; y como último mensaje de la bandeja también firma el programa")
    void laTarjetaYElUltimoMensaje() {
        Mensaje tarjeta = Mensaje.delPrograma(MensajeId.of(UUID.randomUUID()), SOPORTE, ANA,
                ContenidoDelPrograma.imagen("chat/" + SOPORTE + "/fotos/t", "image/jpeg", 120_000), AHORA);

        MensajeResponse enElListado = MensajeResponse.from(
                new MensajeEnriquecido(tarjeta, "Formación Renaser", null, null, "https://firmada"));
        MensajeResponse enLaBandeja = MensajeResponse.from(tarjeta);

        assertThat(enElListado.type()).isEqualTo("SYSTEM");
        assertThat(enElListado.text()).isNull();
        assertThat(enElListado.mediaBucket()).isEqualTo("chat");
        assertThat(enElListado.mediaMime()).isEqualTo("image/jpeg");
        assertThat(enElListado.mediaUrl()).isEqualTo("https://firmada");
        assertThat(enLaBandeja.senderId()).isEqualTo(PROGRAMA);
        assertThat(enLaBandeja.senderName()).isNull();
    }

    @Test
    @DisplayName("un mensaje de una persona sigue saliendo con su emisor")
    void elDeUnaPersonaNoCambia() {
        Mensaje dePersona = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), SOPORTE, ANA, TipoMensaje.TEXTO, "hola",
                null, null, null, null, null, null, AHORA);

        MensajeResponse cable = MensajeResponse.from(dePersona);

        assertThat(cable.type()).isEqualTo("TEXT");
        assertThat(cable.senderId()).isEqualTo(ANA.toString());
    }
}
