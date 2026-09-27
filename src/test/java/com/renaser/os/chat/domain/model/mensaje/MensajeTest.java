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

    /**
     * E-332: SISTEMA es la voz del programa (D-199). Antes {@code escribir} aceptaba un SISTEMA de
     * cualquier emisor —hasta vacío— y {@code POST .../messages} con {@code type: SYSTEM} lo guardaba:
     * cualquier participante podía firmar como el programa.
     */
    @Test
    void unaPersonaNoEscribeMensajesDeSistemaNiVaciosNiConTexto() {
        assertThatThrownBy(() -> Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.SISTEMA, null,
                null, null, null, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lo escribe el programa");
        assertThatThrownBy(() -> Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.SISTEMA,
                "Formación Renaser te da la bienvenida", null, null, null, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** D-199: el programa sí escribe SISTEMA, guardado a nombre de la persona a quien se refiere. */
    @Test
    void elProgramaEscribeSistemaConTextoOConImagen() {
        Mensaje texto = Mensaje.delPrograma(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID,
                ContenidoDelPrograma.texto("Te damos la bienvenida"), AHORA);
        Mensaje tarjeta = Mensaje.delPrograma(MensajeId.of(UUID.randomUUID()), CONVERSACION_ID, EMISOR_ID,
                ContenidoDelPrograma.imagen("chat/c/fotos/t", "image/jpeg", 120_000), AHORA);

        assertThat(texto.tipo()).isEqualTo(TipoMensaje.SISTEMA);
        assertThat(texto.esDelPrograma()).isTrue();
        assertThat(texto.emisorId()).isEqualTo(EMISOR_ID);
        assertThat(tarjeta.tipo()).isEqualTo(TipoMensaje.SISTEMA);
        assertThat(tarjeta.mediaBucket()).isEqualTo(Mensaje.BUCKET_DEFAULT);
        assertThat(tarjeta.mediaRuta()).isEqualTo("chat/c/fotos/t");
        assertThat(tarjeta.mediaBytes()).isEqualTo(120_000);
    }

    @Test
    void unMensajeDelProgramaSinContenidoOSinPersonaEsInvalido() {
        assertThatThrownBy(() -> Mensaje.delPrograma(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID,
                ContenidoDelPrograma.texto("  "), AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Mensaje.delPrograma(MENSAJE_ID, CONVERSACION_ID, null,
                ContenidoDelPrograma.texto("hola"), AHORA)).isInstanceOf(NullPointerException.class);
    }

    /**
     * D-199: hacia afuera un mensaje del programa lo firma el UUID nulo, nunca la persona guardada (que
     * puede ser quien lo mira). Uno de una persona, su emisor.
     */
    @Test
    void elRemitentePublicoDeUnMensajeDelProgramaEsElProgramaYNoLaPersonaGuardada() {
        Mensaje delPrograma = Mensaje.delPrograma(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID,
                ContenidoDelPrograma.texto("hola"), AHORA);
        Mensaje dePersona = Mensaje.escribir(MensajeId.of(UUID.randomUUID()), CONVERSACION_ID, EMISOR_ID,
                TipoMensaje.TEXTO, "hola", null, null, null, null, null, null, AHORA);

        assertThat(delPrograma.remitentePublico()).isEqualTo(new UUID(0L, 0L)).isNotEqualTo(EMISOR_ID.value());
        assertThat(dePersona.remitentePublico()).isEqualTo(EMISOR_ID.value());
        assertThat(dePersona.esDelPrograma()).isFalse();
    }

    @Test
    void unSistemaYaGuardadoSeSigueLeyendo() {
        Mensaje guardado = Mensaje.rehydrate(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.SISTEMA, null, null,
                null, null, null, null, false, null, null, AHORA);

        assertThat(guardado.tipo()).isEqualTo(TipoMensaje.SISTEMA);
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

    /**
     * CHT-06 del e2e del 2026-09-27 (E-374, D-215): un mensaje de 1 MB entraba entero. Lo que escribe una
     * persona tiene un tope: {@value Mensaje#LARGO_MAXIMO_DEL_TEXTO} caracteres, que alcanza para
     * compartir una publicación del Muro (hasta 5.000) con su encabezado.
     */
    @Test
    void unMensajeDeUnaPersonaTieneUnLargoMaximo() {
        String alTope = "a".repeat(6_000);
        String pasado = "a".repeat(6_001);

        assertThat(Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.TEXTO, alTope, null, null,
                null, null, null, null, AHORA).texto()).hasSize(6_000);
        assertThatThrownBy(() -> Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.TEXTO, pasado,
                null, null, null, null, null, null, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje puede tener hasta 6000 caracteres");
        assertThatThrownBy(() -> Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.IMAGEN, pasado,
                "bucket", "ruta.jpg", null, null, null, null, AHORA))
                .as("también el epígrafe de una foto").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elTopeCuentaCaracteresNoBytes() {
        // Un emoji o una letra con tilde es UN carácter para quien escribe, aunque ocupe varios bytes.
        String conTildes = "ñ".repeat(6_000);

        assertThat(Mensaje.escribir(MENSAJE_ID, CONVERSACION_ID, EMISOR_ID, TipoMensaje.TEXTO, conTildes, null, null,
                null, null, null, null, AHORA).texto()).isEqualTo(conTildes);
    }
}
