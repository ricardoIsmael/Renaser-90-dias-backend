package com.renaser.os.rag.domain.model.memoria;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Algo estable que el acompanante aprendio de la persona (D-167): "trabaja de noche", "le sirve
 * caminar temprano", "prefiere respuestas cortas". Lo escribe solo la compactacion; la persona lo ve
 * y lo borra desde su perfil.
 *
 * @param texto de 1 a 300 caracteres, ya recortado (el mismo limite que el CHECK de V67)
 */
public record Recuerdo(UUID id, CategoriaDeRecuerdo categoria, String texto, Instant creadoEn) {

    public static final int LARGO_MAXIMO = 300;

    public Recuerdo {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(categoria, "categoria es obligatoria");
        Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
        texto = Objects.requireNonNull(texto, "texto es obligatorio").strip();
        if (texto.isEmpty() || texto.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("Un recuerdo tiene de 1 a " + LARGO_MAXIMO + " caracteres");
        }
    }
}
