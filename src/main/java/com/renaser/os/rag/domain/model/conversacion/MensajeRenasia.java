package com.renaser.os.rag.domain.model.conversacion;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

/**
 * Un mensaje del chat de Renasia (tabla `mensajes_renasia`). {@code usuarioId} apunta a
 * `conversaciones_renasia.usuario_id` (la conversacion, PK = FK), no directamente a
 * `usuarios` — la conversacion debe existir antes del primer mensaje (docs/MODULO_RAG.md §2).
 *
 * <p><b>D-49:</b> las columnas {@code marcado_por_usuario}, {@code nota_marca} y
 * {@code anulado_por_admin} de la tabla real NO se modelan aca a proposito — el dueno del
 * proyecto decidio quitar la funcion de "marcar" un mensaje. La BD esta congelada y las
 * columnas siguen existiendo con sus valores por defecto ({@code false}/{@code null}); esto
 * es deliberado, no un olvido ni un bug de persistencia.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class MensajeRenasia {

    private final MensajeRenasiaId id;
    private final UserId usuarioId;
    private final RolMensaje rol;
    private final String contenido;
    private final List<FuenteMensaje> fuentes;
    private final Instant creadoEn;

    public static MensajeRenasia escribirDeUsuario(UserId usuarioId, String contenido, Instant ahora) {
        return crear(usuarioId, RolMensaje.USUARIO, contenido, List.of(), ahora);
    }

    public static MensajeRenasia escribirDeAsistente(UserId usuarioId, String contenido, List<FuenteMensaje> fuentes,
                                                       Instant ahora) {
        return crear(usuarioId, RolMensaje.ASISTENTE, contenido, fuentes, ahora);
    }

    private static MensajeRenasia crear(UserId usuarioId, RolMensaje rol, String contenido,
                                         List<FuenteMensaje> fuentes, Instant ahora) {
        requireUsuarioId(usuarioId);
        requireContenido(contenido);
        requireFuentesSoloDeAsistente(rol, fuentes);
        return new MensajeRenasia(MensajeRenasiaId.newId(), usuarioId, rol, contenido, List.copyOf(fuentes), ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static MensajeRenasia rehydrate(MensajeRenasiaId id, UserId usuarioId, RolMensaje rol, String contenido,
                                            List<FuenteMensaje> fuentes, Instant creadoEn) {
        requireFuentesSoloDeAsistente(rol, fuentes);
        return new MensajeRenasia(id, usuarioId, rol, contenido, List.copyOf(fuentes), creadoEn);
    }

    private static void requireUsuarioId(UserId usuarioId) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("usuarioId es obligatorio en un mensaje de Renasia");
        }
    }

    private static void requireContenido(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("El contenido del mensaje es obligatorio");
        }
    }

    /** Las fuentes son citas de la base de conocimiento que respaldan una respuesta del
     * ASISTENTE — un mensaje de USUARIO nunca puede llevarlas (auditoria adversarial: el
     * adaptador de persistencia reconstruye objetos sin cruzar rol contra fuentes, asi que
     * esta validacion tiene que vivir en el dominio, no confiar en que el llamador se porte bien). */
    private static void requireFuentesSoloDeAsistente(RolMensaje rol, List<FuenteMensaje> fuentes) {
        if (rol == RolMensaje.USUARIO && !fuentes.isEmpty()) {
            throw new IllegalArgumentException("Un mensaje de USUARIO no puede tener fuentes");
        }
    }

    @Override
    public String toString() {
        // Sin contenido: es dato personal (CLAUDE.MD sec. 5.4.9 y docs/MODULO_RAG.md D-47).
        return "MensajeRenasia[" + id + ", rol=" + rol + "]";
    }
}
