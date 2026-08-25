package com.renaser.os.academy.domain.model.curso;

/**
 * Identidad de un curso. NUNCA generada por el dominio: es la clave natural
 * del import de Skool (`cursos.id text`, ver V1__baseline_renaser.sql linea
 * ~953) y viaja estable entre entornos. Distinto del resto de los agregados
 * del backend (que usan UUID generado): acá el dominio nunca crea un id
 * nuevo, solo reconstruye uno ya existente.
 */
public record CursoId(String value) {

    public CursoId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CursoId no puede ser vacio");
        }
    }

    public static CursoId of(String value) {
        return new CursoId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
