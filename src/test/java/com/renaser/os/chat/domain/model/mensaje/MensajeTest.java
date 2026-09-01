package com.renaser.os.chat.domain.model.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Replica en dominio los dos CHECK de `mensajes` (V1__baseline_renaser.sql:1321-1324)
 * ANTES de llegar a Postgres (CLAUDE.MD sec. 5.4.4): `mensaje_con_contenido` y
 * `media_completa`. */
class MensajeTest {

    private static final Instant AHORA = Instant.parse("2026-08-25T10:00:00Z");
    private static final MensajeId MENSAJE_ID = MensajeId.of(UUID.randomUUID());
    private static final ConversacionId CONVERSACION_ID = ConversacionId.of(UUID.randomUUID());
    private static final UserId EMISOR_ID = UserId.of(UUID.randomUUID());

    @Test
    void unMensajeDeTextoSinTextoNiMediaEsInvalido() {
        assertThatThrownBy(() -> Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.TEXTO, null,
                null, null, null, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unMensajeDeTextoEnBlancoSinMediaEsInvalido() {
        assertThatThrownBy(() -> Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.TEXTO, "   ",
                null, null, null, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unMensajeSistemaNoNecesitaTextoNiMedia() {
        Mensaje mensaje = Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.SISTEMA, null, null,
                null, null, null, null, null, AHORA);

        assertThat(mensaje.id()).isEqualTo(MENSAJE_ID);
        assertThat(mensaje.tipo()).isEqualTo(TipoMensaje.SISTEMA);
        assertThat(mensaje.texto()).isNull();
    }

    @Test
    void unMensajeDeTextoConTextoEsValido() {
        Mensaje mensaje = Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.TEXTO, "hola", null,
                null, null, null, null, null, AHORA);

        assertThat(mensaje.texto()).isEqualTo("hola");
    }

    @Test
    void unMensajeDeImagenSinTextoPeroConMediaEsValido() {
        Mensaje mensaje = Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.IMAGEN, null,
                "bucket", "ruta.jpg", "image/jpeg", 1024, null, null, AHORA);

        assertThat(mensaje.mediaRuta()).isEqualTo("ruta.jpg");
    }

    @Test
    void mediaBucketSinMediaRutaEsInvalido() {
        assertThatThrownBy(() -> Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.IMAGEN, "algo",
                "bucket", null, null, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mediaRutaSinMediaBucketEsInvalido() {
        assertThatThrownBy(() -> Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.IMAGEN, "algo",
                null, "ruta.jpg", null, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mediaBytesNoPositivoEsInvalido() {
        assertThatThrownBy(() -> Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.IMAGEN, "algo",
                "bucket", "ruta.jpg", null, 0, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mediaDuracionNoPositivaEsInvalida() {
        assertThatThrownBy(() -> Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.AUDIO, "algo",
                "bucket", "ruta.mp3", null, null, (short) 0, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unMensajeNuevoNuncaEstaOcultoNiEliminado() {
        Mensaje mensaje = Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.TEXTO, "hola", null,
                null, null, null, null, null, AHORA);

        assertThat(mensaje.oculto()).isFalse();
        assertThat(mensaje.eliminadoEn()).isNull();
    }
}
