package com.renaser.os.rag.application.ports.out.analisis;

import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodo;

/** Escritura del análisis 80/20 (spec 031). */
public interface SaveAnalisisDelPeriodoPort {

    /**
     * Guarda el análisis solo si el periodo no tiene todavía un resultado: inserta la fila, o actualiza la que sigue {@code PENDIENTE}.
     * Nunca pisa un {@code LISTO}, un {@code SIN_DATOS} ni un {@code AGOTADO}: si dos instancias llegan a la vez, gana la primera.
     *
     * @return {@code true} si quedó guardado; {@code false} si el periodo ya tenía su resultado
     */
    boolean guardar(AnalisisDelPeriodo analisis);
}
