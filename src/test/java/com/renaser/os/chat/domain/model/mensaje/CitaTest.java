package com.renaser.os.chat.domain.model.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Responder a un mensaje (D-251): las reglas de la cita viven en el dominio, sin Spring ni base. Antes de D-251
 * la regla de la misma conversación estaba en {@code MensajeService} y un id inexistente salía como 404 —con
 * lo que «no existe» y «es de otro chat» se distinguían desde afuera—; {@code Mensaje.escribir} recibía un
 * {@code MensajeId} cualquiera y nadie miraba si el citado seguía a la vista.
 */
class CitaTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T03:00:00Z");
    private static final ConversacionId CHAT = ConversacionId.of(UUID.randomUUID());
    private static final ConversacionId OTRO_CHAT = ConversacionId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId LUIS = UserId.of(UUID.randomUUID());

    @Test
    @DisplayName("se cita un mensaje de la misma conversación: la respuesta guarda su id y es respuesta")
    void citaValida() {
        Mensaje deLuis = deTexto(LUIS, CHAT, "¿Vamos mañana a correr?");

        Cita cita = Cita.aResponder(deLuis.id(), Optional.of(deLuis), CHAT);
        Mensaje respuesta = responde(ANA, CHAT, cita);

        assertThat(cita.citado()).isSameAs(deLuis);
        assertThat(respuesta.respuestaAId()).isEqualTo(deLuis.id());
        assertThat(respuesta.esRespuesta()).isTrue();
        assertThat(Cita.sePuedeMostrar(respuesta, deLuis)).isTrue();
    }

    @Test
    @DisplayName("sin cita, igual que antes de D-251: no es respuesta y no muestra nada")
    void sinCitaIgualQueAntes() {
        Mensaje mensaje = responde(ANA, CHAT, null);

        assertThat(mensaje.respuestaAId()).isNull();
        assertThat(mensaje.esRespuesta()).isFalse();
        assertThat(Cita.sePuedeMostrar(mensaje, deTexto(LUIS, CHAT, "hola"))).isFalse();
    }

    @Test
    @DisplayName("citar un mensaje de OTRA conversación se rechaza, con el mismo mensaje que un id que no existe")
    void otraConversacionONoExisteDicenLoMismo() {
        Mensaje deOtroChat = deTexto(LUIS, OTRO_CHAT, "algo privado de otro chat");
        MensajeId inexistente = MensajeId.of(UUID.randomUUID());

        assertThatThrownBy(() -> Cita.aResponder(deOtroChat.id(), Optional.of(deOtroChat), CHAT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje que quieres responder no está en esta conversación")
                .hasMessageNotContaining("privado");
        assertThatThrownBy(() -> Cita.aResponder(inexistente, Optional.empty(), CHAT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje que quieres responder no está en esta conversación");
    }

    @Test
    @DisplayName("si el puerto devolviera otro mensaje que el pedido, tampoco se cita")
    void elEncontradoTieneQueSerElPedido() {
        Mensaje otro = deTexto(LUIS, CHAT, "otro");

        assertThatThrownBy(() -> Cita.aResponder(MensajeId.of(UUID.randomUUID()), Optional.of(otro), CHAT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje que quieres responder no está en esta conversación");
    }

    @Test
    @DisplayName("no se responde a lo que su autor borró ni a lo que retiró la moderación")
    void borradoORetiradoNoSeResponde() {
        Mensaje borrado = guardado(LUIS, CHAT, false, AHORA);
        Mensaje retirado = guardado(LUIS, CHAT, true, null);

        assertThatThrownBy(() -> Cita.aResponder(borrado.id(), Optional.of(borrado), CHAT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ese mensaje fue eliminado y ya no se puede responder");
        assertThatThrownBy(() -> Cita.aResponder(retirado.id(), Optional.of(retirado), CHAT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ese mensaje fue eliminado y ya no se puede responder");
    }

    @Test
    @DisplayName("una cita validada para un chat no sirve para escribir en otro")
    void laCitaEsDeSuConversacion() {
        Mensaje deLuis = deTexto(LUIS, CHAT, "hola");
        Cita deEsteChat = Cita.aResponder(deLuis.id(), Optional.of(deLuis), CHAT);

        assertThatThrownBy(() -> responde(ANA, OTRO_CHAT, deEsteChat))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje que quieres responder no está en esta conversación");
    }

    @Test
    @DisplayName("al leer: el citado borrado, retirado, de otro chat o que ya no está no se muestra")
    void alLeerSoloSeMuestraLoQueSigueALaVista() {
        Mensaje deLuis = deTexto(LUIS, CHAT, "hola");
        Mensaje respuesta = responde(ANA, CHAT, Cita.aResponder(deLuis.id(), Optional.of(deLuis), CHAT));
        Mensaje mismoIdEnOtroChat = Mensaje.rehydrate(deLuis.id(), OTRO_CHAT, LUIS, TipoMensaje.TEXTO, "privado", null,
                null, null, null, null, false, null, null, AHORA);
        Mensaje mismoIdBorrado = Mensaje.rehydrate(deLuis.id(), CHAT, LUIS, TipoMensaje.TEXTO, "hola", null,
                null, null, null, null, false, AHORA, null, AHORA);

        assertThat(Cita.sePuedeMostrar(respuesta, null)).as("se borró con la cuenta de Luis").isFalse();
        assertThat(Cita.sePuedeMostrar(respuesta, mismoIdEnOtroChat)).as("fila de otro chat").isFalse();
        assertThat(Cita.sePuedeMostrar(respuesta, mismoIdBorrado)).as("borrado por su autor").isFalse();
        assertThat(Cita.sePuedeMostrar(respuesta, deTexto(LUIS, CHAT, "otro"))).as("no es el citado").isFalse();
    }

    @Test
    @DisplayName("una respuesta guardada sigue siendo respuesta aunque el citado ya no esté (V92)")
    void guardadaSigueSiendoRespuesta() {
        MensajeId yaNoEsta = MensajeId.of(UUID.randomUUID());
        Mensaje respuesta = Mensaje.rehydrate(MensajeId.of(UUID.randomUUID()), CHAT, ANA, TipoMensaje.TEXTO, "¡Sí!",
                null, null, null, null, null, false, null, yaNoEsta, AHORA);

        assertThat(respuesta.esRespuesta()).isTrue();
        assertThat(Cita.sePuedeMostrar(respuesta, null)).isFalse();
    }

    private static Mensaje deTexto(UserId quien, ConversacionId donde, String texto) {
        return Mensaje.escribir(MensajeId.of(UUID.randomUUID()), donde, quien, TipoMensaje.TEXTO, texto, null, null,
                null, null, null, null, AHORA);
    }

    private static Mensaje responde(UserId quien, ConversacionId donde, Cita cita) {
        return Mensaje.escribir(MensajeId.of(UUID.randomUUID()), donde, quien, TipoMensaje.TEXTO, "¡Sí, yo también!",
                null, null, null, null, null, cita, AHORA);
    }

    private static Mensaje guardado(UserId quien, ConversacionId donde, boolean oculto, Instant eliminadoEn) {
        return Mensaje.rehydrate(MensajeId.of(UUID.randomUUID()), donde, quien, TipoMensaje.TEXTO, "lo que dijo", null,
                null, null, null, null, oculto, eliminadoEn, null, AHORA);
    }
}
