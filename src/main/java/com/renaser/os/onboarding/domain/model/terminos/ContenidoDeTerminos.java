package com.renaser.os.onboarding.domain.model.terminos;

import java.util.List;
import java.util.Objects;

/**
 * El texto de unos términos (spec 027, FR-002): título, introducción opcional, cláusulas y el texto de la casilla de aceptación. Texto plano, con saltos de
 * línea. Las cláusulas no llevan número propio: la numeración sale de su posición, así que mover, quitar o insertar una renumera sola (FR-003).
 *
 * @param clausulas en orden; sin espacios de más en los bordes
 */
public record ContenidoDeTerminos(String titulo, String introduccion, List<String> clausulas, String textoCasilla) {

    public static final int MAX_TITULO = 120;
    public static final int MAX_INTRODUCCION = 2000;
    public static final int MAX_CLAUSULA = 2000;
    public static final int MAX_CLAUSULAS = 100;
    public static final int MAX_CASILLA = 200;

    public ContenidoDeTerminos {
        titulo = titulo == null ? "" : titulo.strip();
        introduccion = introduccion == null ? "" : introduccion.strip();
        textoCasilla = textoCasilla == null ? "" : textoCasilla.strip();
        clausulas = clausulas == null ? List.of() : clausulas.stream().map(c -> c == null ? "" : c.strip()).toList();
    }

    /**
     * Lo que impide publicar (FR-007), en una frase para quien edita; vacío si se puede. Se guarda un borrador a medias, pero no se publica uno así.
     */
    public List<String> loQueFaltaParaPublicar() {
        java.util.ArrayList<String> faltas = new java.util.ArrayList<>();
        if (titulo.isEmpty()) {
            faltas.add("Falta el título.");
        }
        if (clausulas.isEmpty()) {
            faltas.add("Agrega al menos una cláusula.");
        }
        for (int i = 0; i < clausulas.size(); i++) {
            if (clausulas.get(i).isEmpty()) {
                faltas.add("La cláusula " + (i + 1) + " está vacía.");
            }
        }
        if (textoCasilla.isEmpty()) {
            faltas.add("Falta el texto de la casilla de aceptación.");
        }
        return List.copyOf(faltas);
    }

    /**
     * Lo que impide GUARDAR un borrador: solo los límites de tamaño, que la base también impone. Un borrador con cláusulas vacías se guarda (es trabajo en curso).
     *
     * @throws IllegalArgumentException (400) si algo pasa de su límite o no hay ninguna cláusula
     */
    public void exigirQueSePuedaGuardar() {
        if (titulo.length() > MAX_TITULO) {
            throw new IllegalArgumentException("El título puede tener hasta " + MAX_TITULO + " caracteres.");
        }
        if (introduccion.length() > MAX_INTRODUCCION) {
            throw new IllegalArgumentException("La introducción puede tener hasta " + MAX_INTRODUCCION + " caracteres.");
        }
        if (textoCasilla.length() > MAX_CASILLA) {
            throw new IllegalArgumentException("El texto de la casilla puede tener hasta " + MAX_CASILLA + " caracteres.");
        }
        if (clausulas.isEmpty() || clausulas.size() > MAX_CLAUSULAS) {
            throw new IllegalArgumentException("Los términos llevan entre 1 y " + MAX_CLAUSULAS + " cláusulas.");
        }
        for (int i = 0; i < clausulas.size(); i++) {
            if (clausulas.get(i).length() > MAX_CLAUSULA) {
                throw new IllegalArgumentException("La cláusula " + (i + 1) + " puede tener hasta " + MAX_CLAUSULA + " caracteres.");
            }
        }
        Objects.requireNonNull(clausulas);
    }

    /** Si dice lo mismo que {@code otro} (ya sin los espacios de los bordes). Publicar sin cambios no crea una versión vacía (FR-007). */
    public boolean dicenLoMismo(ContenidoDeTerminos otro) {
        return equals(otro);
    }
}
