package com.renaser.os.rag.domain.model.habitopersonal;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Las cuatro dimensiones de Training en las que una persona puede crear un habito propio (D-229).
 *
 * <p>No es una lista nueva: es la misma {@code CATEGORIA_POR_DIMENSION} de la app
 * ({@code PlanificarDimensionModal}) y las mismas claves de {@code renaser.categorias_habito}.
 * VIDA Y NEGOCIO no esta a proposito: no es una categoria de habito sino de rocas, y en Training
 * tampoco se puede crear un habito ahi.
 *
 * <p>{@link #leer} acepta como lo diga la persona (con o sin tilde, "emociones" o "consciencia")
 * porque el nombre visible de la dimension (Emociones) no coincide con su clave (CONSCIENCIA).
 */
public enum DimensionDelHabito {

    CUERPO("CUERPO", "Cuerpo", List.of("cuerpo", "body")),
    MENTE("MENTE", "Mente", List.of("mente", "mind")),
    EMOCIONES("CONSCIENCIA", "Emociones", List.of("emociones", "emocion", "emocional", "consciencia",
            "conciencia", "conscience")),
    ESPIRITU("ESPIRITU", "Espíritu", List.of("espiritu", "espiritual", "spirit"));

    private final String claveCategoria;
    private final String etiqueta;
    private final List<String> formasDeDecirlo;

    DimensionDelHabito(String claveCategoria, String etiqueta, List<String> formasDeDecirlo) {
        this.claveCategoria = claveCategoria;
        this.etiqueta = etiqueta;
        this.formasDeDecirlo = formasDeDecirlo;
    }

    /** La clave de {@code categorias_habito} con que la guarda {@code habits}. */
    public String claveCategoria() {
        return claveCategoria;
    }

    /** Como la ve la persona en Training. */
    public String etiqueta() {
        return etiqueta;
    }

    /** Vacio si no es ninguna de las cuatro, o si no vino. */
    public static Optional<DimensionDelHabito> leer(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String normalizado = sinTildes(texto.trim().toLowerCase(Locale.ROOT));
        return Arrays.stream(values())
                .filter(dimension -> dimension.name().equalsIgnoreCase(normalizado)
                        || dimension.formasDeDecirlo.contains(normalizado))
                .findFirst();
    }

    /** E-455: la dimension de una clave de {@code categorias_habito}; vacio si no es ninguna de las cuatro. */
    public static Optional<DimensionDelHabito> deClave(String claveCategoria) {
        return Arrays.stream(values()).filter(dimension -> dimension.claveCategoria.equals(claveCategoria)).findFirst();
    }

    static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
