package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.EstadoDeEntrega;
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

    /**
     * D-208: la marca viaja en inglés, como el resto del cable (D-36). En la bandeja y en la respuesta
     * de enviar va {@code null}, que la app toma como ✓: el campo es nuevo y un APK sin él lo ignora.
     */
    @Test
    @DisplayName("D-208: status sale SENT o READ en el listado, y null donde no se resuelve o el mensaje no es de quien mira")
    void laMarcaDeEntregaEnElCable() {
        Mensaje dePersona = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), SOPORTE, ANA, TipoMensaje.TEXTO, "hola",
                null, null, null, null, null, null, AHORA);
        MensajeEnriquecido enriquecido = new MensajeEnriquecido(dePersona, "Ana", null, null, null);

        assertThat(MensajeResponse.from(enriquecido.conEstadoDeEntrega(EstadoDeEntrega.LEIDO)).status()).isEqualTo("READ");
        assertThat(MensajeResponse.from(enriquecido.conEstadoDeEntrega(EstadoDeEntrega.ENVIADO)).status()).isEqualTo("SENT");
        assertThat(MensajeResponse.from(enriquecido).status()).as("de otra persona").isNull();
        assertThat(MensajeResponse.from(dePersona).status()).as("bandeja y respuesta de enviar").isNull();
    }

    /**
     * D-251: la cita en el cable. Los campos que el APK publicado valida ({@code replyTo.id}/{@code type} como
     * texto, {@code text}/{@code deletedAt} presentes) siguen ahí; lo nuevo se suma.
     */
    @Test
    @DisplayName("D-251: una respuesta lleva replyToId, el resumen con sus campos nuevos y replyToDeleted en false")
    void laCitaEnElCable() {
        MensajeId citado = MensajeId.of(UUID.randomUUID());
        Mensaje respuesta = Mensaje.rehydrate(MensajeId.of(UUID.randomUUID()), SOPORTE, ANA, TipoMensaje.TEXTO,
                "¡Sí!", null, null, null, null, null, false, null, citado, AHORA);
        var resumen = new MensajeEnriquecido.RespuestaPreview(citado, "Luis Soto", TipoMensaje.IMAGEN,
                "Sticker Renaser: Muy bien", "image/webp", null, "https://firmada/s", true);

        MensajeResponse cable = MensajeResponse.from(new MensajeEnriquecido(respuesta, "Ana", null, resumen, null));

        assertThat(cable.replyToId()).isEqualTo(citado.toString());
        assertThat(cable.replyToDeleted()).isFalse();
        assertThat(cable.replyTo().id()).isEqualTo(citado.toString());
        assertThat(cable.replyTo().senderName()).isEqualTo("Luis Soto");
        assertThat(cable.replyTo().type()).isEqualTo("IMAGE");
        assertThat(cable.replyTo().text()).isEqualTo("Sticker Renaser: Muy bien");
        assertThat(cable.replyTo().deletedAt()).isNull();
        assertThat(cable.replyTo().mediaMime()).isEqualTo("image/webp");
        assertThat(cable.replyTo().mediaUrl()).isEqualTo("https://firmada/s");
        assertThat(cable.replyTo().mine()).isTrue();
    }

    @Test
    @DisplayName("D-251: si lo citado ya no está, replyTo y replyToId van null y replyToDeleted en true; sin cita, todo como antes")
    void laCitaQueYaNoEsta() {
        Mensaje respuesta = Mensaje.rehydrate(MensajeId.of(UUID.randomUUID()), SOPORTE, ANA, TipoMensaje.TEXTO,
                "¡Sí!", null, null, null, null, null, false, null, MensajeId.of(UUID.randomUUID()), AHORA);
        Mensaje sinCita = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), SOPORTE, ANA, TipoMensaje.TEXTO, "hola",
                null, null, null, null, null, null, AHORA);

        MensajeResponse sinElCitado = MensajeResponse.from(new MensajeEnriquecido(respuesta, "Ana", null, null, null));
        MensajeResponse comoAntes = MensajeResponse.from(new MensajeEnriquecido(sinCita, "Ana", null, null, null));

        assertThat(sinElCitado.replyTo()).isNull();
        assertThat(sinElCitado.replyToId()).as("no se publica un id que ya no lleva a nada").isNull();
        assertThat(sinElCitado.replyToDeleted()).isTrue();
        assertThat(comoAntes.replyTo()).isNull();
        assertThat(comoAntes.replyToId()).isNull();
        assertThat(comoAntes.replyToDeleted()).isFalse();
        assertThat(MensajeResponse.from(sinCita).replyToDeleted()).as("bandeja").isFalse();
    }
}
