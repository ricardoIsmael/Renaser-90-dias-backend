package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

/**
 * Espejo de {@code HabitCategory} del cliente. El dominio guarda {@code categoriaClave}
 * como texto libre (FK a la tabla-catalogo {@code categorias_habito}, P-23) — este enum
 * fija el mapeo confirmado por el propio baseline SQL (comentario de la siembra:
 * "BODY→CUERPO, MIND→MENTE, CONSCIENCE→CONSCIENCIA, SPIRIT→ESPIRITU").
 */
public enum HabitCategoryDto {
    BODY,
    MIND,
    CONSCIENCE,
    SPIRIT;

    public static HabitCategoryDto fromClave(String clave) {
        return switch (clave) {
            case "CUERPO" -> BODY;
            case "MENTE" -> MIND;
            case "CONSCIENCIA" -> CONSCIENCE;
            case "ESPIRITU" -> SPIRIT;
            default -> throw new IllegalStateException("categoriaClave desconocida: " + clave);
        };
    }

    public String toClave() {
        return switch (this) {
            case BODY -> "CUERPO";
            case MIND -> "MENTE";
            case CONSCIENCE -> "CONSCIENCIA";
            case SPIRIT -> "ESPIRITU";
        };
    }
}
