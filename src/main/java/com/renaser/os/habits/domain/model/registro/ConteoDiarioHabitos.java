package com.renaser.os.habits.domain.model.registro;

import java.time.LocalDate;

/**
 * Tally cruda de un dia calendario (UTC) de un participante, tal como la trae
 * el puerto en lote ({@code ContarRegistrosDiariosHabitsPort}): pura
 * agregacion SQL — contar filas de {@code registros_habito} por condicion
 * sobre columnas ya almacenadas ({@code estado}, {@code es_opcional}), sin
 * ninguna decision de negocio.
 *
 * <p>La regla de negocio de que cuenta como "calificable" ese dia (D-43,
 * docs/MODULO_HABITS.md §9) vive en {@link #calificables()}/{@link
 * #puntajeDelDia()}, no en la consulta que arma este record.
 */
public record ConteoDiarioHabitos(LocalDate fecha, int totalRegistros, int completados, int opcionalesNoCompletados) {

    public ConteoDiarioHabitos {
        if (fecha == null) {
            throw new IllegalArgumentException("fecha es obligatoria");
        }
        if (totalRegistros < 0 || completados < 0 || opcionalesNoCompletados < 0) {
            throw new IllegalArgumentException("Los conteos no pueden ser negativos");
        }
        if (completados > totalRegistros) {
            throw new IllegalArgumentException("completados no puede superar totalRegistros");
        }
        if (opcionalesNoCompletados > totalRegistros) {
            throw new IllegalArgumentException("opcionalesNoCompletados no puede superar totalRegistros");
        }
    }

    /**
     * total - opcionales sin completar: un habito opcional sin completar no entra ni al
     * numerador ni al denominador (coherence.ts:61-68, "Fallar un opcional no es un fallo").
     */
    int calificables() {
        return totalRegistros - opcionalesNoCompletados;
    }

    /**
     * REDONDEO 1 — por dia, ANTES de promediar (coherence.ts:97,
     * {@code Math.round((completed/total)*100)}). Solo valido si {@link #calificables()} &gt; 0;
     * el llamador filtra los dias sin nada calificable antes de invocar esto.
     *
     * <p>Aritmetica en {@code double} a proposito, no {@link java.math.BigDecimal}: el sistema
     * viejo calcula esto con numeros IEEE754 de JavaScript (mismo formato que el {@code double}
     * de Java) — usar decimal exacto aca introduciria redondeos que el sistema viejo nunca tuvo.
     */
    int puntajeDelDia() {
        return (int) Math.round((completados * 100.0) / calificables());
    }
}
