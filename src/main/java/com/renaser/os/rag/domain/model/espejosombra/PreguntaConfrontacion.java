package com.renaser.os.rag.domain.model.espejosombra;

/**
 * Una pregunta de confrontación hija de un {@link InformeEspejoSombra} (tabla
 * {@code preguntas_confrontacion}). {@code orden} espeja el CHECK
 * {@code orden BETWEEN 1 AND 10} de la tabla; la cardinalidad máxima de 10 preguntas
 * por informe y la unicidad de {@code orden} dentro de un mismo informe se validan en
 * el agregado ({@link InformeEspejoSombra}), no acá — este value object solo protege
 * su propio invariante local.
 *
 * @param orden    posición de la pregunta dentro del informe, 1..10
 * @param pregunta el texto de la pregunta de confrontación
 */
public record PreguntaConfrontacion(int orden, String pregunta) {

    public PreguntaConfrontacion {
        if (orden < 1 || orden > 10) {
            throw new IllegalArgumentException("orden debe estar entre 1 y 10: " + orden);
        }
        if (pregunta == null || pregunta.isBlank()) {
            throw new IllegalArgumentException("pregunta es obligatoria");
        }
    }
}
