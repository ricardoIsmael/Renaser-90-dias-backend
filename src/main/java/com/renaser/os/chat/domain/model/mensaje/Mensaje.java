package com.renaser.os.chat.domain.model.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Un mensaje dentro de una conversacion (tabla `mensajes`). Replica en dominio los dos
 * CHECK de la base ANTES de llegar a Postgres (CLAUDE.MD sec. 5.4.4):
 * <ul>
 *   <li>{@code mensaje_con_contenido}: SISTEMA no necesita texto/media; cualquier otro tipo
 *   necesita al menos uno de los dos.</li>
 *   <li>{@code media_completa}: bucket y ruta viajan juntos o no viaja ninguno.</li>
 * </ul>
 *
 * <p>Sin mutadores de moderacion (ocultar/eliminar): ningun caso de uso de este encargo los
 * pide (ver docs/MODULO_CHAT.md §6, fuera de alcance explicito). {@code oculto}/{@code
 * eliminadoEn} solo se leen via {@link #rehydrate} para reflejar el estado ya persistido.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Mensaje {

    private final MensajeId id;
    private final ConversacionId conversacionId;
    private final UserId emisorId;
    private final TipoMensaje tipo;
    private final String texto;
    private final String mediaBucket;
    private final String mediaRuta;
    private final String mediaMime;
    private final Integer mediaBytes;
    private final Short mediaDuracionS;
    private final boolean oculto;
    private final Instant eliminadoEn;
    private final MensajeId respuestaAId;
    private final Instant creadoEn;

    public static Mensaje escribir(ConversacionId conversacionId, UserId emisorId, TipoMensaje tipo, String texto,
                                    String mediaBucket, String mediaRuta, String mediaMime, Integer mediaBytes,
                                    Short mediaDuracionS, MensajeId respuestaAId, Instant ahora) {
        requireConContenido(tipo, texto, mediaRuta);
        requireMediaCompleta(mediaBucket, mediaRuta);
        requirePositivosSiVienen(mediaBytes, mediaDuracionS);
        return new Mensaje(MensajeId.newId(), conversacionId, emisorId, tipo, texto, mediaBucket, mediaRuta,
                mediaMime, mediaBytes, mediaDuracionS, false, null, respuestaAId, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static Mensaje rehydrate(MensajeId id, ConversacionId conversacionId, UserId emisorId, TipoMensaje tipo,
                                     String texto, String mediaBucket, String mediaRuta, String mediaMime,
                                     Integer mediaBytes, Short mediaDuracionS, boolean oculto, Instant eliminadoEn,
                                     MensajeId respuestaAId, Instant creadoEn) {
        return new Mensaje(id, conversacionId, emisorId, tipo, texto, mediaBucket, mediaRuta, mediaMime, mediaBytes,
                mediaDuracionS, oculto, eliminadoEn, respuestaAId, creadoEn);
    }

    private static void requireConContenido(TipoMensaje tipo, String texto, String mediaRuta) {
        if (tipo != TipoMensaje.SISTEMA && (texto == null || texto.isBlank()) && mediaRuta == null) {
            throw new IllegalArgumentException("El mensaje necesita texto o media (salvo tipo SISTEMA)");
        }
    }

    private static void requireMediaCompleta(String mediaBucket, String mediaRuta) {
        if ((mediaRuta == null) != (mediaBucket == null)) {
            throw new IllegalArgumentException("mediaBucket y mediaRuta deben viajar juntos o ninguno de los dos");
        }
    }

    private static void requirePositivosSiVienen(Integer mediaBytes, Short mediaDuracionS) {
        if (mediaBytes != null && mediaBytes <= 0) {
            throw new IllegalArgumentException("mediaBytes debe ser positivo");
        }
        if (mediaDuracionS != null && mediaDuracionS <= 0) {
            throw new IllegalArgumentException("mediaDuracionS debe ser positivo");
        }
    }

    @Override
    public String toString() {
        return "Mensaje[" + id + ", conversacion=" + conversacionId + ", tipo=" + tipo + "]";
    }
}
