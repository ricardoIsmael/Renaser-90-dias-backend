package com.renaser.os.notifications.domain.model.chat;

import com.renaser.os.notifications.domain.model.chat.RedaccionDelMensajeDeChat.Aviso;
import com.renaser.os.notifications.domain.model.notificacion.EtiquetaDelAviso;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** D-221: el texto del aviso de un mensaje, como en WhatsApp. */
class RedaccionDelMensajeDeChatTest {

    @Test
    @DisplayName("grupo: título = nombre del chat, cuerpo = «Nombre: texto»")
    void grupo() {
        assertThat(RedaccionDelMensajeDeChat.redactar("Luisa y sus aprendices", 1, false, "Ana Pérez",
                ContenidoDelMensaje.TEXTO, "  hola\n a todos "))
                .isEqualTo(new Aviso("Luisa y sus aprendices", "Ana Pérez: hola a todos"));
    }

    @Test
    @DisplayName("con dos o más sin leer, el conteo va en el título (el aviso reemplaza al anterior)")
    void conteo() {
        assertThat(RedaccionDelMensajeDeChat.redactar("Formación Renaser Global", 3, false, "Ana Pérez",
                ContenidoDelMensaje.TEXTO, "hola").titulo()).isEqualTo("Formación Renaser Global (3 mensajes nuevos)");
    }

    @Test
    @DisplayName("1 a 1: el título es quien escribe y el cuerpo va sin «Nombre:»")
    void unoAUno() {
        assertThat(RedaccionDelMensajeDeChat.redactar("Luisa Quispe", 1, true, "Luisa Quispe",
                ContenidoDelMensaje.TEXTO, "¿cómo vas?")).isEqualTo(new Aviso("Luisa Quispe", "¿cómo vas?"));
    }

    @Test
    @DisplayName("sin texto dice qué es; una foto con epígrafe lo muestra")
    void sinTexto() {
        assertThat(RedaccionDelMensajeDeChat.resumen(ContenidoDelMensaje.FOTO, null)).isEqualTo("📷 Foto");
        assertThat(RedaccionDelMensajeDeChat.resumen(ContenidoDelMensaje.NOTA_DE_VOZ, " ")).isEqualTo("🎤 Nota de voz");
        assertThat(RedaccionDelMensajeDeChat.resumen(ContenidoDelMensaje.VIDEO, null)).isEqualTo("🎥 Video");
        assertThat(RedaccionDelMensajeDeChat.resumen(ContenidoDelMensaje.FOTO, "mi caja")).isEqualTo("📷 mi caja");
    }

    @Test
    @DisplayName("un texto largo se corta sin partir un emoji")
    void largo() {
        String largo = "😀".repeat(RedaccionDelMensajeDeChat.LARGO_MAXIMO + 10);
        String resumen = RedaccionDelMensajeDeChat.resumen(ContenidoDelMensaje.TEXTO, largo);
        assertThat(resumen.codePointCount(0, resumen.length())).isEqualTo(RedaccionDelMensajeDeChat.LARGO_MAXIMO);
        assertThat(resumen).endsWith("…").doesNotContain("�");
    }

    @Test
    @DisplayName("la etiqueta que agrupa sale de la ruta del chat, solo para MENSAJE_CHAT")
    void etiqueta() {
        assertThat(EtiquetaDelAviso.de(TipoNotificacion.MENSAJE_CHAT, "/chat/abc")).contains("chat-abc");
        assertThat(EtiquetaDelAviso.de(TipoNotificacion.MENSAJE_CHAT, "/chat/")).isEmpty();
        assertThat(EtiquetaDelAviso.de(TipoNotificacion.MENSAJE_CHAT, null)).isEmpty();
        assertThat(EtiquetaDelAviso.de(TipoNotificacion.HITO_PROGRAMA, "/chat/abc")).isEmpty();
    }

    @Test
    @DisplayName("el mensaje de chat es el único tipo que no se ve en la campana")
    void campana() {
        for (TipoNotificacion tipo : TipoNotificacion.values()) {
            assertThat(tipo.seVeEnLaCampana()).as(tipo.name()).isEqualTo(tipo != TipoNotificacion.MENSAJE_CHAT);
        }
    }
}
