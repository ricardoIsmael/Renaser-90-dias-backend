package com.renaser.os.points.domain.model.semaforo.mensaje;

import java.util.Objects;
import java.util.Set;

/**
 * Un mensaje del catálogo (fila de {@code mensajes_semaforo}): texto con variables, el grupo al que pertenece y el
 * sticker que lo acompaña. Las variables NO se guardan: se derivan del texto, así no pueden desincronizarse si
 * alguien corrige la redacción (constitución IV).
 *
 * @param sticker nombre del sticker del búho (por ejemplo {@code dorado-orgulloso}); lo resuelve la app
 */
public record MensajeDelSemaforo(String id, GrupoDelMensaje grupo, String texto, String sticker, boolean activo) {

    /** Largo máximo del texto ya redactado que promete el contrato de {@code GET /api/v1/home}. */
    public static final int LARGO_MAXIMO = 300;

    public MensajeDelSemaforo {
        exigirContenido(id, "id");
        exigirContenido(texto, "texto");
        exigirContenido(sticker, "sticker");
        Objects.requireNonNull(grupo, "grupo es obligatorio");
        if (texto.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El texto de " + id + " pasa de " + LARGO_MAXIMO + " caracteres");
        }
        Variable.enTexto(texto);
    }

    /** Los datos que este texto necesita de la persona. */
    public Set<Variable> variables() {
        return Variable.enTexto(texto);
    }

    private static void exigirContenido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
