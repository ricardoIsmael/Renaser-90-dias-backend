package com.renaser.os.rag.domain.model.conversacion;

/**
 * Una fuente citada en una respuesta del asistente (tabla `fuentes_mensaje_renasia`,
 * N:M mensaje-leccion). {@code leccionId} es {@code text} en la base (ids estilo Skool),
 * NO uuid — ver docs/MODULO_RAG.md §2.
 */
public record FuenteMensaje(String leccionId) {

    public FuenteMensaje {
        if (leccionId == null || leccionId.isBlank()) {
            throw new IllegalArgumentException("leccionId es obligatorio en una fuente de mensaje");
        }
    }

    public static FuenteMensaje of(String leccionId) {
        return new FuenteMensaje(leccionId);
    }
}
