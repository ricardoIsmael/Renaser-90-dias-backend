package com.renaser.os.community.domain.model.publicacion;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Una publicacion del Muro (tabla `publicaciones_muro`). Traduccion 1:1 de
 * `features/wall/service.ts` (docs/MODULO_COMMUNITY.md sec. 1).
 *
 * <p>El carrusel es OBLIGATORIO y va de 1 a 10 archivos (wall/schema.ts:41-54, mismo tope
 * que el carrusel de Instagram) — "el Muro es un feed visual, una publicacion sin foto
 * rompe la retícula". `categoriaClave` es opcional: un cliente viejo que no manda
 * categoria sigue publicando igual (wall/schema.ts:51-53); la EXISTENCIA de la clave la
 * comprueba el caso de uso contra `categorias_muro`, el dominio solo valida la forma.
 *
 * <p>"Borrar" oculta (`oculta`), nunca destruye — moderar no debe destruir evidencia
 * (wall/service.ts:220-224, comentario de `hidePost`). El borrado fisico
 * (`permanentlyDeletePost`) queda reservado a la cola de moderacion, fuera de este agregado
 * (lo hace el puerto de salida directo, ver `EliminarPublicacionUseCase`).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Publicacion {

    public static final int TEXTO_MAX = 5000;
    private static final int MEDIA_MIN = 1;
    private static final int MEDIA_MAX = 10;

    private final PublicacionId id;
    private final UserId autorId;
    private final TipoPublicacion tipo;
    private String categoriaClave;
    private String texto;
    private List<MediaPublicacion> media;
    private boolean oculta;
    private final Instant creadoEn;
    private Instant actualizadoEn;
    /**
     * Dia de programa del AUTOR en el momento de publicar (1..90), o {@code null} si no tenia
     * programa activo.
     *
     * <p>Se guarda, no se deriva al leer. Derivarlo desde {@code participantes_programa} haria que
     * una publicacion vieja cambiara de dia cada vez que se ajusta el programa de su autor
     * ({@code dias_ajuste_programa} existe para eso). Lo que la publicacion cuenta es donde estaba
     * esa persona ESE dia, y eso no se reescribe despues. Ver V51.
     *
     * <p>{@code final}: el dia en que algo se publico no cambia. Editar el texto no lo mueve.
     */
    private final Integer diaPrograma;

    /** La existencia/actividad de `categoriaClave` NO se valida aca: es una consulta a
     * `categorias_muro` que el dominio no puede hacer (CLAUDE.MD sec. 5.1) — la comprueba
     * el caso de uso antes de llamar a este factory.
     *
     * <p>El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code PublicacionMuroService.publicar}).
     * Asi la factoria es referencialmente transparente y un test puede fijar el id que espera,
     * en vez de tener que caer a {@link #rehydrate} para lograrlo. */
    public static Publicacion publicar(PublicacionId id, UserId autorId, String texto,
                                        List<MediaPublicacion> media, String categoriaClave, Instant ahora) {
        return publicar(id, autorId, texto, media, categoriaClave, ahora, null);
    }

    /**
     * Igual que {@link #publicar}, con el dia de programa del autor.
     *
     * <p>Es una sobrecarga y no un parametro mas en la firma de siempre a proposito: el dia lo
     * resuelve el caso de uso contra otro modulo, y hacerlo obligatorio habria obligado a inventar
     * un dia en los trece sitios que ya construyen publicaciones —tests incluidos— donde el
     * programa del autor no viene al caso. {@code null} significa "no tenia programa", que es un
     * estado real (staff que nunca lo arranco), no un hueco por llenar.
     */
    public static Publicacion publicar(PublicacionId id, UserId autorId, String texto,
                                        List<MediaPublicacion> media, String categoriaClave, Instant ahora,
                                        Integer diaPrograma) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(autorId, "autorId es obligatorio");
        requireTextoValido(texto);
        List<MediaPublicacion> mediaOrdenada = requireMediaValida(media);
        return new Publicacion(id, autorId, TipoPublicacion.MANUAL, categoriaClave, texto.trim(),
                mediaOrdenada, false, ahora, ahora, requireDiaValido(diaPrograma));
    }

    /**
     * Publicacion generada automaticamente por OTRO modulo (ej. `rocks` al completar una
     * Roca con evidencia y {@code publishedToWall=true}), no por un POST directo de un
     * actor sobre el Muro. Produce {@code HITO_AUTOMATICO} — el unico valor del enum
     * pensado para esto (ver {@link TipoPublicacion}, CM-7 de docs/MODULO_COMMUNITY.md:
     * documentado desde el inicio como "sin ningun trigger que lo genere todavia"). Sin
     * categoria: clasificar en una categoria del Muro es una decision manual del autor,
     * no aplica a un post que ningun humano redacto desde el editor.
     *
     * <p>El {@code id} entra por parametro, igual que en {@link #publicar}: lo pide el caso de uso
     * al puerto {@code IdGenerator} ({@code PublicacionMuroService.publicarDesdeEvidencia}).
     */
    public static Publicacion publicarAutomatica(PublicacionId id, UserId autorId, String texto,
                                                  List<MediaPublicacion> media, Instant ahora) {
        return publicarAutomatica(id, autorId, texto, media, ahora, null);
    }

    /** Igual que {@link #publicarAutomatica}, con el dia de programa del autor. */
    public static Publicacion publicarAutomatica(PublicacionId id, UserId autorId, String texto,
                                                  List<MediaPublicacion> media, Instant ahora,
                                                  Integer diaPrograma) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(autorId, "autorId es obligatorio");
        requireTextoValido(texto);
        List<MediaPublicacion> mediaOrdenada = requireMediaValida(media);
        return new Publicacion(id, autorId, TipoPublicacion.HITO_AUTOMATICO, null, texto.trim(),
                mediaOrdenada, false, ahora, ahora, requireDiaValido(diaPrograma));
    }

    /** Solo para el adaptador de persistencia. */
    public static Publicacion rehydrate(PublicacionId id, UserId autorId, TipoPublicacion tipo,
                                         String categoriaClave, String texto, List<MediaPublicacion> media,
                                         boolean oculta, Instant creadoEn, Instant actualizadoEn) {
        return rehydrate(id, autorId, tipo, categoriaClave, texto, media, oculta, creadoEn, actualizadoEn, null);
    }

    /** Solo para el adaptador de persistencia, con el dia guardado en la fila. */
    public static Publicacion rehydrate(PublicacionId id, UserId autorId, TipoPublicacion tipo,
                                         String categoriaClave, String texto, List<MediaPublicacion> media,
                                         boolean oculta, Instant creadoEn, Instant actualizadoEn,
                                         Integer diaPrograma) {
        // Sin validar el dia: una fila vieja con un valor raro no debe impedir LEER el Muro. La
        // cota se aplica al escribir, que es donde se puede corregir.
        return new Publicacion(id, autorId, tipo, categoriaClave, texto, List.copyOf(media), oculta, creadoEn,
                actualizadoEn, diaPrograma);
    }

    /**
     * El dia va de 1 a 90 o no va. Un 0 —que es lo que el Muro mostraba en todas las
     * publicaciones— no es un dia del programa: es la ausencia del dato disfrazada de dato.
     */
    private static Integer requireDiaValido(Integer dia) {
        if (dia == null) {
            return null;
        }
        if (dia < 1 || dia > 90) {
            throw new IllegalArgumentException("diaPrograma va de 1 a 90, llego " + dia);
        }
        return dia;
    }

    /** Solo el autor edita (lo comprueba el caso de uso) — sin bypass de moderacion:
     * ocultar contenido ajeno es moderar, reescribirlo no (wall/service.ts:137-138). La
     * categoria no se toca al editar (wall/schema.ts:58-60). */
    public void editar(String texto, List<MediaPublicacion> media, Instant ahora) {
        requireNoOculta();
        requireTextoValido(texto);
        this.texto = texto.trim();
        this.media = requireMediaValida(media);
        this.actualizadoEn = ahora;
    }

    public void ocultar(Instant ahora) {
        requireNoOculta();
        this.oculta = true;
        this.actualizadoEn = ahora;
    }

    public void restaurar(Instant ahora) {
        if (!oculta) {
            throw new IllegalStateException("La publicacion no esta oculta");
        }
        this.oculta = false;
        this.actualizadoEn = ahora;
    }

    private void requireNoOculta() {
        if (oculta) {
            throw new IllegalStateException("No se puede modificar una publicacion oculta");
        }
    }

    private static void requireTextoValido(String texto) {
        if (texto == null || texto.trim().isBlank()) {
            throw new IllegalArgumentException("El texto de la publicacion es obligatorio");
        }
        if (texto.trim().length() > TEXTO_MAX) {
            throw new IllegalArgumentException("El texto no puede pasar de " + TEXTO_MAX + " caracteres");
        }
    }

    private static List<MediaPublicacion> requireMediaValida(List<MediaPublicacion> media) {
        if (media == null || media.size() < MEDIA_MIN) {
            throw new IllegalArgumentException("La publicacion debe llevar al menos una foto o video");
        }
        if (media.size() > MEDIA_MAX) {
            throw new IllegalArgumentException("Maximo " + MEDIA_MAX + " archivos por publicacion");
        }
        return List.copyOf(media);
    }

    @Override
    public String toString() {
        return "Publicacion[" + id + ", " + autorId + ", " + tipo + "]";
    }
}
