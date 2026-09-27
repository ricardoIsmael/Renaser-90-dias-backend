package com.renaser.os.chat.domain.model.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un mensaje dentro de una conversacion (tabla `mensajes`). Replica en dominio los dos
 * CHECK de la base ANTES de llegar a Postgres (CLAUDE.MD sec. 5.4.4):
 * <ul>
 *   <li>{@code mensaje_con_contenido}: todo mensaje que escribe una persona necesita texto o
 *   media. (La base exime a SISTEMA, pero una persona ya no puede escribir SISTEMA: ver abajo.)</li>
 *   <li>{@code media_completa}: bucket y ruta viajan juntos o no viaja ninguno.</li>
 * </ul>
 *
 * <p><b>SISTEMA es la voz del programa</b> (D-199/D-204, 2026-09-27): lo que no escribe ninguna
 * persona, como las bienvenidas. La app nueva lo pinta como «Formación Renaser», con el fénix.
 * <ul>
 *   <li>Solo se crea con {@link #delPrograma}. {@link #escribir} (una persona) lo rechaza con 400
 *   (E-332): si no, cualquier participante podría firmar como el programa.</li>
 *   <li>{@code mensajes.emisor_id} es NOT NULL (V1) y no se inventa un usuario técnico, así que en
 *   un mensaje del programa {@link #emisorId} guarda a QUIÉN se refiere: la persona a quien se le da
 *   la bienvenida. Así la cascada de {@code emisor_id} lo borra con su cuenta, junto con lo suyo.</li>
 *   <li>Hacia afuera nunca se atribuye a esa persona: {@link #remitentePublico} devuelve
 *   {@link #ID_PUBLICO_DEL_PROGRAMA}.</li>
 * </ul>
 * <blockquote><b>Corregido 2026-09-27.</b> Decía «SISTEMA no necesita texto/media; cualquier otro
 * tipo necesita al menos uno de los dos», y {@code escribir} aceptaba un SISTEMA vacío de
 * cualquier emisor.</blockquote>
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

    /**
     * Nombre logico del deposito de los medios de chat. Vive en el dominio, igual que
     * {@code MediaPublicacion.BUCKET_DEFAULT} en `community`: es parte de como se identifica un
     * mensaje con media, no un detalle del adaptador de S3.
     */
    public static final String BUCKET_DEFAULT = "chat";

    /**
     * Con qué id firma hacia afuera un mensaje del programa (D-199): el UUID nulo, que ningún usuario
     * tiene. No {@code null}: todas las versiones publicadas de la app validan {@code senderId} como
     * texto obligatorio, y un {@code null} dejaría sin bandeja a quien lo reciba.
     */
    public static final UUID ID_PUBLICO_DEL_PROGRAMA = new UUID(0L, 0L);

    /** Con qué nombre firma el programa: el mismo que cierra el nombre de cada chat de soporte. */
    public static final String NOMBRE_DEL_PROGRAMA = "Formación Renaser";

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

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code MensajeService.enviar}). Asi la
     * factoria es referencialmente transparente y un test puede fijar el id que espera
     * (CLAUDE.MD §5.4.7).
     */
    public static Mensaje escribir(MensajeId id, ConversacionId conversacionId, UserId emisorId, TipoMensaje tipo,
                                    String texto, String mediaBucket, String mediaRuta, String mediaMime,
                                    Integer mediaBytes, Short mediaDuracionS, MensajeId respuestaAId,
                                    Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        requireEscritoPorUnaPersona(tipo);
        requireConContenido(texto, mediaRuta);
        requireMediaCompleta(mediaBucket, mediaRuta);
        requirePositivosSiVienen(mediaBytes, mediaDuracionS);
        return new Mensaje(id, conversacionId, emisorId, tipo, texto, mediaBucket, mediaRuta,
                mediaMime, mediaBytes, mediaDuracionS, false, null, respuestaAId, ahora);
    }

    /**
     * Un mensaje del programa (SISTEMA): nadie lo escribe, así que no hay emisor que validar.
     *
     * @param sobreQuien la persona a quien se refiere (ver la clase): queda en {@code emisor_id}
     */
    public static Mensaje delPrograma(MensajeId id, ConversacionId conversacionId, UserId sobreQuien,
                                      ContenidoDelPrograma contenido, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(sobreQuien, "un mensaje del programa se guarda a nombre de la persona a quien se refiere");
        requireConContenido(contenido.texto(), contenido.mediaRuta());
        requireMediaCompleta(contenido.mediaBucket(), contenido.mediaRuta());
        requirePositivosSiVienen(contenido.mediaBytes(), null);
        return new Mensaje(id, conversacionId, sobreQuien, TipoMensaje.SISTEMA, contenido.texto(),
                contenido.mediaBucket(), contenido.mediaRuta(), contenido.mediaMime(), contenido.mediaBytes(), null,
                false, null, null, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static Mensaje rehydrate(MensajeId id, ConversacionId conversacionId, UserId emisorId, TipoMensaje tipo,
                                     String texto, String mediaBucket, String mediaRuta, String mediaMime,
                                     Integer mediaBytes, Short mediaDuracionS, boolean oculto, Instant eliminadoEn,
                                     MensajeId respuestaAId, Instant creadoEn) {
        return new Mensaje(id, conversacionId, emisorId, tipo, texto, mediaBucket, mediaRuta, mediaMime, mediaBytes,
                mediaDuracionS, oculto, eliminadoEn, respuestaAId, creadoEn);
    }

    public boolean esDelPrograma() {
        return tipo == TipoMensaje.SISTEMA;
    }

    /**
     * Quién firma este mensaje ante quien lo lee: el emisor, o el programa si es de SISTEMA. Nunca la
     * persona guardada en un mensaje del programa, aunque sea quien lo está mirando.
     */
    public UUID remitentePublico() {
        return esDelPrograma() ? ID_PUBLICO_DEL_PROGRAMA : emisorId.value();
    }

    /** SISTEMA es la voz del programa, no de una persona (E-332). */
    private static void requireEscritoPorUnaPersona(TipoMensaje tipo) {
        if (tipo == TipoMensaje.SISTEMA) {
            throw new IllegalArgumentException("Un mensaje de sistema lo escribe el programa, no una persona");
        }
    }

    private static void requireConContenido(String texto, String mediaRuta) {
        if ((texto == null || texto.isBlank()) && mediaRuta == null) {
            throw new IllegalArgumentException("El mensaje necesita texto o media");
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
