package com.renaser.os.users.domain.model.user;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * Lo que alguien escribió en un buscador de personas, normalizado como se compara contra la base (D-249):
 * sin tildes ni diéresis, «ñ» como «n», en minúsculas y con los espacios de más recortados. «María José»,
 * «maria jose» y «MARÍA  JOSÉ» buscan lo mismo.
 *
 * <p>Del lado de la base la columna se normaliza igual con {@code translate(lower(...))}, que es built-in e
 * IMMUTABLE (mismo criterio que V19, sin depender de la extensión {@code unaccent}).
 */
public final class TextoDeBusqueda {

    /** Más que un nombre completo largo no tiene sentido y acota lo que viaja a la consulta. */
    static final int LARGO_MAXIMO = 80;

    private final String normalizado;

    private TextoDeBusqueda(String normalizado) {
        this.normalizado = normalizado;
    }

    /** Vacío si no hay nada que buscar (null, vacío o solo espacios). */
    public static Optional<TextoDeBusqueda> de(String escrito) {
        if (escrito == null) {
            return Optional.empty();
        }
        String sinMarcas = Normalizer.normalize(escrito, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String limpio = sinMarcas.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TextoDeBusqueda(limpio.length() > LARGO_MAXIMO ? limpio.substring(0, LARGO_MAXIMO) : limpio));
    }

    public String normalizado() {
        return normalizado;
    }
}
