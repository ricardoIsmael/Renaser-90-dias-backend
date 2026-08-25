package com.renaser.os.community.domain.model.categoria;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Catalogo de categorias del Muro (tabla `categorias_muro`). Traduccion 1:1 de
 * `features/wall-categories/service.ts` — dejo de ser un enum compilado (comentario de la
 * migracion `20260810150000_wall_categories_tabla`) para que el administrador lo gestione
 * en caliente.
 *
 * <p>`clave` es la identidad Y lo que se guarda en `publicaciones_muro.categoria_clave`
 * (P-09 del baseline): renombrarla arrastraria publicaciones, por eso nunca se edita —
 * solo `etiqueta` y `emoji` lo son (wall-categories/schema.ts:54-58).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "clave")
public final class CategoriaMuro {

    /** ASCII mayusculas por herencia del enum viejo: viaja en el enlace del tutorial de
     * bienvenida (`comunidad?publicar=PRESENTACION`) — wall-categories/schema.ts:13-29. */
    private static final Pattern CLAVE_VALIDA = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");

    private final String clave;
    private String etiqueta;
    private String emoji;
    private int orden;
    private boolean activa;
    private final boolean esSistema;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static CategoriaMuro crear(String clave, String etiqueta, String emoji, int orden, Instant ahora) {
        requireClaveValida(clave);
        requireEtiquetaValida(etiqueta);
        requireEmojiValido(emoji);
        return new CategoriaMuro(clave, etiqueta, emoji, orden, true, false, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static CategoriaMuro rehydrate(String clave, String etiqueta, String emoji, int orden, boolean activa,
                                           boolean esSistema, Instant creadoEn, Instant actualizadoEn) {
        return new CategoriaMuro(clave, etiqueta, emoji, orden, activa, esSistema, creadoEn, actualizadoEn);
    }

    /** Retirar una de sistema equivale a borrarla vista desde el movil — la secuencia de
     * bienvenida se queda sin destino (wall-categories/service.ts:155-168). */
    public void actualizar(String etiqueta, String emoji, Boolean nuevaActiva, Instant ahora) {
        String etiquetaEfectiva = etiqueta != null ? etiqueta : this.etiqueta;
        String emojiEfectivo = emoji != null ? emoji : this.emoji;
        requireEtiquetaValida(etiquetaEfectiva);
        requireEmojiValido(emojiEfectivo);
        if (esSistema && Boolean.FALSE.equals(nuevaActiva)) {
            throw new IllegalArgumentException(
                    "\"" + this.etiqueta + "\" es una categoria del sistema: la secuencia de bienvenida "
                            + "la necesita para la primera publicacion. Puedes cambiarle el nombre y el emoji, "
                            + "pero no retirarla.");
        }
        this.etiqueta = etiquetaEfectiva;
        this.emoji = emojiEfectivo;
        if (nuevaActiva != null) {
            this.activa = nuevaActiva;
        }
        this.actualizadoEn = ahora;
    }

    public void reordenar(int nuevoOrden, Instant ahora) {
        this.orden = nuevoOrden;
        this.actualizadoEn = ahora;
    }

    /** Reglas de {@code eliminar()} las decide el caso de uso: necesita saber cuantas
     * publicaciones usan la clave, dato que el dominio no tiene (wall-categories/service.ts:207-246). */
    public void requireEliminable() {
        if (esSistema) {
            throw new IllegalArgumentException("\"" + etiqueta + "\" es una categoria del sistema y no se puede "
                    + "eliminar: la secuencia de bienvenida la necesita para la primera publicacion.");
        }
    }

    public static void requireClaveValida(String clave) {
        if (clave == null || !CLAVE_VALIDA.matcher(clave.trim()).matches()) {
            throw new IllegalArgumentException(
                    "La clave debe ir en MAYUSCULAS ASCII, sin espacios ni acentos (ej. REVELACIONES)");
        }
    }

    private static void requireEtiquetaValida(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new IllegalArgumentException("La etiqueta es obligatoria");
        }
        if (etiqueta.length() > 40) {
            throw new IllegalArgumentException("La etiqueta no puede pasar de 40 caracteres");
        }
    }

    private static void requireEmojiValido(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            throw new IllegalArgumentException("El emoji es obligatorio");
        }
        if (emoji.length() > 8) {
            throw new IllegalArgumentException("Eso no parece un emoji suelto");
        }
    }

    @Override
    public String toString() {
        return "CategoriaMuro[" + clave + ", " + etiqueta + "]";
    }
}
