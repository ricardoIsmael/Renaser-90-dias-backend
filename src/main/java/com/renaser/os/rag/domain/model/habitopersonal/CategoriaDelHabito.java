package com.renaser.os.rag.domain.model.habitopersonal;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Una categoria de habito (una dimension de Training) de la formacion de la persona (spec 038): su clave de {@code categorias_habito} y el nombre
 * con que la ve. Reemplaza al enum {@link DimensionDelHabito} como lo que SER propone y rotula, porque el Diplomado y la Certificacion tienen
 * categorias PROPIAS que Administracion crea y el enum solo conoce las cuatro de la Escuela.
 *
 * <p>{@link DimensionDelHabito} se conserva solo para entender como lo dicen las personas en la Escuela («emociones» es la clave
 * {@code CONSCIENCIA}); una persona de otra formacion que lo diga y no tenga esa categoria recibe la lista de las suyas.
 */
public record CategoriaDelHabito(String clave, String nombre) {

    public CategoriaDelHabito {
        Objects.requireNonNull(clave, "la clave de la categoria es obligatoria");
        Objects.requireNonNull(nombre, "el nombre de la categoria es obligatorio");
    }

    /** La categoria de la Escuela que corresponde a esa dimension (lo que habia antes de la spec 038). */
    public static CategoriaDelHabito de(DimensionDelHabito dimension) {
        Objects.requireNonNull(dimension, "la categoria es obligatoria");
        return new CategoriaDelHabito(dimension.claveCategoria(), dimension.etiqueta());
    }

    /**
     * La categoria de entre {@code disponibles} (las de SU formacion) que la persona o el modelo quisieron decir: por clave, por nombre (sin
     * distinguir tildes ni mayusculas) o, en la Escuela, por como se dice de siempre. Vacio si el texto falta o ninguna de las suyas lo es: nunca
     * se adivina ni se cae a otra formacion.
     */
    public static Optional<CategoriaDelHabito> leer(String texto, List<CategoriaDelHabito> disponibles) {
        if (texto == null || texto.isBlank() || disponibles == null) {
            return Optional.empty();
        }
        String buscado = comparable(texto);
        Optional<CategoriaDelHabito> porClaveONombre = disponibles.stream()
                .filter(categoria -> comparable(categoria.clave()).equals(buscado) || comparable(categoria.nombre()).equals(buscado))
                .findFirst();
        if (porClaveONombre.isPresent()) {
            return porClaveONombre;
        }
        return DimensionDelHabito.leer(texto)
                .flatMap(dimension -> disponibles.stream()
                        .filter(categoria -> categoria.clave().equals(dimension.claveCategoria())).findFirst());
    }

    private static String comparable(String texto) {
        return DimensionDelHabito.sinTildes(texto.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }
}
