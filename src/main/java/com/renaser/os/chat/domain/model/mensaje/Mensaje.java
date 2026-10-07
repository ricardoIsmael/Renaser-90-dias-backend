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
 *   <li>Desde V95 (D-262) un mensaje del programa puede no referirse a NADIE ({@link #delProgramaSinPersona}):
 *   el podio semanal del grupo general nombra a varios aprendices y no puede desaparecer porque uno cierre
 *   su cuenta. Ahí {@link #emisorId} es {@code null}; la base solo lo admite en {@code SISTEMA}. Por eso
 *   nadie lee {@code emisorId} de un mensaje del programa: se pregunta antes {@link #esDelPrograma}.</li>
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

    /**
     * El largo máximo del texto de un mensaje que escribe una persona (D-215, 2026-09-27; E-374: uno de 1 MB
     * entraba entero). Se cuenta como lo cuenta la app ({@code String.length()}, igual que el
     * {@code maxLength} de su campo), así la app y el servidor nunca discrepan. 6.000 alcanza de sobra para
     * escribir y para compartir una publicación del Muro, que puede tener 5.000 más su encabezado. Los
     * mensajes del programa no llevan tope: los escribe el servidor.
     */
    public static final int LARGO_MAXIMO_DEL_TEXTO = 6_000;

    private final MensajeId id;
    private final ConversacionId conversacionId;
    /** {@code null} solo en un mensaje del programa que no se refiere a nadie ({@link #delProgramaSinPersona}). */
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
     *
     * @param cita {@code null} si no responde a nada (la app vieja nunca cita). Si viene, ya la validó
     *             {@link Cita#aResponder}; acá solo se vuelve a exigir que sea de esta conversación (D-251).
     */
    public static Mensaje escribir(MensajeId id, ConversacionId conversacionId, UserId emisorId, TipoMensaje tipo,
                                    String texto, String mediaBucket, String mediaRuta, String mediaMime,
                                    Integer mediaBytes, Short mediaDuracionS, Cita cita,
                                    Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        requireEscritoPorUnaPersona(tipo);
        requireConContenido(texto, mediaRuta);
        requireLargoAdmitido(texto);
        requireMediaCompleta(mediaBucket, mediaRuta);
        requirePositivosSiVienen(mediaBytes, mediaDuracionS);
        requireCitaDeEstaConversacion(cita, conversacionId);
        return new Mensaje(id, conversacionId, emisorId, tipo, texto, mediaBucket, mediaRuta,
                mediaMime, mediaBytes, mediaDuracionS, false, null, cita != null ? cita.citado().id() : null, ahora);
    }

    /**
     * Un mensaje del programa (SISTEMA): nadie lo escribe, así que no hay emisor que validar.
     *
     * @param sobreQuien la persona a quien se refiere (ver la clase): queda en {@code emisor_id}
     */
    public static Mensaje delPrograma(MensajeId id, ConversacionId conversacionId, UserId sobreQuien,
                                      ContenidoDelPrograma contenido, Instant ahora) {
        Objects.requireNonNull(sobreQuien, "un mensaje del programa se guarda a nombre de la persona a quien se refiere");
        return delProgramaGuardadoComo(id, conversacionId, sobreQuien, contenido, ahora);
    }

    /**
     * Un mensaje del programa que no se refiere a una persona (D-262, el podio semanal del grupo general): se
     * guarda sin {@code emisor_id}, así no cae con la cuenta de nadie. Hacia afuera es igual a cualquier otro
     * mensaje del programa ({@link #remitentePublico}).
     */
    public static Mensaje delProgramaSinPersona(MensajeId id, ConversacionId conversacionId,
                                                ContenidoDelPrograma contenido, Instant ahora) {
        return delProgramaGuardadoComo(id, conversacionId, null, contenido, ahora);
    }

    private static Mensaje delProgramaGuardadoComo(MensajeId id, ConversacionId conversacionId, UserId emisorId,
                                                   ContenidoDelPrograma contenido, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        requireConContenido(contenido.texto(), contenido.mediaRuta());
        requireMediaCompleta(contenido.mediaBucket(), contenido.mediaRuta());
        requirePositivosSiVienen(contenido.mediaBytes(), null);
        return new Mensaje(id, conversacionId, emisorId, TipoMensaje.SISTEMA, contenido.texto(),
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

    /**
     * Si es una respuesta (D-251). Lo dice {@code respuestaAId} y nada más: desde V92 la base ya no lo pone en
     * NULL cuando el citado se borra, así que una respuesta sigue siéndolo aunque lo que citaba ya no esté.
     */
    public boolean esRespuesta() {
        return respuestaAId != null;
    }

    /**
     * Ni su autor lo borró ({@code eliminadoEn}) ni la moderación lo retiró ({@code oculto}). Lo pregunta
     * {@link Cita}, al responder y al mostrar (D-251).
     */
    boolean sigueALaVista() {
        return !oculto && eliminadoEn == null;
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

    /**
     * Si este mensaje lo escribió {@code persona}: es de ella y no del programa. Un mensaje del
     * programa guardado a su nombre (la bienvenida) no es suyo, igual que en {@link #remitentePublico}:
     * no lleva marca de enviado ni de leído (D-208).
     */
    public boolean escritoPor(UserId persona) {
        return !esDelPrograma() && emisorId.equals(persona);
    }

    /** SISTEMA es la voz del programa, no de una persona (E-332). */
    private static void requireEscritoPorUnaPersona(TipoMensaje tipo) {
        if (tipo == TipoMensaje.SISTEMA) {
            throw new IllegalArgumentException("Un mensaje de sistema lo escribe el programa, no una persona");
        }
    }

    private static void requireCitaDeEstaConversacion(Cita cita, ConversacionId conversacionId) {
        if (cita != null && !cita.esDe(conversacionId)) {
            throw new IllegalArgumentException(Cita.NO_ESTA_EN_LA_CONVERSACION);
        }
    }

    private static void requireConContenido(String texto, String mediaRuta) {
        if ((texto == null || texto.isBlank()) && mediaRuta == null) {
            throw new IllegalArgumentException("El mensaje necesita texto o media");
        }
    }

    private static void requireLargoAdmitido(String texto) {
        if (texto != null && texto.length() > LARGO_MAXIMO_DEL_TEXTO) {
            throw new IllegalArgumentException("El mensaje puede tener hasta " + LARGO_MAXIMO_DEL_TEXTO + " caracteres");
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
