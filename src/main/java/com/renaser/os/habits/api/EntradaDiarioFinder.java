package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrato publico de `habits` para leer entradas de diario desde otro modulo (D-50).
 * `entradas_diario` es tabla de `habits`; ningun modulo la consulta de frente (D-41).
 *
 * <p>Su primer consumidor es el Espejo Sombra de `rag`, que cada semana analiza lo que el
 * aprendiz escribio para devolverle un patron dominante y preguntas de confrontacion.
 */
public interface EntradaDiarioFinder {

    /**
     * Entradas del participante en el rango, ordenadas por fecha ascendente.
     *
     * @param inicio primer dia incluido
     * @param fin    ultimo dia incluido
     * @return lista vacia si no escribio nada en la semana — nunca null
     */
    List<EntradaDiarioSummary> entradasEntre(UserId participanteId, LocalDate inicio, LocalDate fin);
}
