package com.renaser.os.community.domain.model.publicacion;

import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Un comentario de una publicacion (tabla `comentarios_muro`). Mas corto que una
 * publicacion a proposito: "el muro es para publicar, los comentarios para responder"
 * (wall/schema.ts:81-82).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Comentario {

    public static final int TEXTO_MAX = 500;

    private final ComentarioId id;
    private final PublicacionId publicacionId;
    private final UserId autorId;
    private String texto;
    private boolean oculto;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static Comentario escribir(PublicacionId publicacionId, UserId autorId, String texto, Instant ahora) {
        Objects.requireNonNull(publicacionId, "publicacionId es obligatorio");
        Objects.requireNonNull(autorId, "autorId es obligatorio");
        requireTextoValido(texto);
        return new Comentario(ComentarioId.newId(), publicacionId, autorId, texto.trim(), false, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static Comentario rehydrate(ComentarioId id, PublicacionId publicacionId, UserId autorId, String texto,
                                        boolean oculto, Instant creadoEn, Instant actualizadoEn) {
        return new Comentario(id, publicacionId, autorId, texto, oculto, creadoEn, actualizadoEn);
    }

    /** Solo el autor edita, sin bypass de moderacion (wall/service.ts:389-391). */
    public void editar(String texto, Instant ahora) {
        requireNoOculto();
        requireTextoValido(texto);
        this.texto = texto.trim();
        this.actualizadoEn = ahora;
    }

    public void ocultar(Instant ahora) {
        requireNoOculto();
        this.oculto = true;
        this.actualizadoEn = ahora;
    }

    private void requireNoOculto() {
        if (oculto) {
            throw new IllegalStateException("El comentario ya esta oculto");
        }
    }

    private static void requireTextoValido(String texto) {
        if (texto == null || texto.trim().isBlank()) {
            throw new IllegalArgumentException("El comentario no puede estar vacio");
        }
        if (texto.trim().length() > TEXTO_MAX) {
            throw new IllegalArgumentException("El comentario no puede pasar de " + TEXTO_MAX + " caracteres");
        }
    }

    @Override
    public String toString() {
        return "Comentario[" + id + ", " + publicacionId + ", " + autorId + "]";
    }
}
