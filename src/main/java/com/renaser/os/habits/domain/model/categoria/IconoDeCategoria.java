package com.renaser.os.habits.domain.model.categoria;

import java.util.List;

/**
 * Los iconos de linea entre los que Administracion elige el de una categoria de habito (spec 023, FR-003). Son nombres de iconos que la app YA
 * dibuja (Lucide, caja de 24): ampliar la lista pide una app nueva, y una categoria sin icono usa uno generico. El valor guardado es el nombre
 * tal cual lo conoce la app ({@code activity}, {@code brain}…).
 */
public final class IconoDeCategoria {

    /** Los que ya usaban las cuatro categorias de la Escuela (activity, brain, heart, sparkles) mas otros que la app dibuja de sobra. */
    public static final List<String> PERMITIDOS = List.of(
            "activity", "brain", "heart", "sparkles", "briefcase", "bookOpen", "flame", "target", "compass", "users",
            "star", "moon", "sunrise", "handHeart", "award", "bulb");

    private IconoDeCategoria() {
    }

    /**
     * @return el icono sin espacios, o {@code null} si no se eligio ninguno
     * @throws IllegalArgumentException si no es uno de {@link #PERMITIDOS}
     */
    public static String validar(String icono) {
        if (icono == null || icono.isBlank()) {
            return null;
        }
        String limpio = icono.trim();
        if (!PERMITIDOS.contains(limpio)) {
            throw new IllegalArgumentException("Ese ícono no está disponible para una categoría.");
        }
        return limpio;
    }
}
