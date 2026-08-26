package com.renaser.os.rag.domain.model.espejosombra;

/**
 * Value object que encapsula el invariante {@code pcts_suman_100} de
 * {@code informes_espejo_sombra}: qué proporción del contenido de la semana del
 * aprendiz habla de su pasado, su presente o su futuro. Es el punto de mayor valor
 * del dominio de este agregado (docs/MODULO_RAG.md, instrucciones del encargo) — que
 * la suma dé exactamente 100 se valida ACÁ, en un solo lugar, no repetido en el
 * factory method de {@link InformeEspejoSombra}. El CHECK de la base de datos es la
 * última línea de defensa, no la primera.
 *
 * @param pctPasado   porcentaje del contenido orientado al pasado, 0..100
 * @param pctPresente porcentaje del contenido orientado al presente, 0..100
 * @param pctFuturo   porcentaje del contenido orientado al futuro, 0..100
 */
public record DistribucionTemporal(int pctPasado, int pctPresente, int pctFuturo) {

    public DistribucionTemporal {
        requireEnRango(pctPasado, "pctPasado");
        requireEnRango(pctPresente, "pctPresente");
        requireEnRango(pctFuturo, "pctFuturo");
        if (pctPasado + pctPresente + pctFuturo != 100) {
            throw new IllegalArgumentException(
                    "Los porcentajes deben sumar 100 (CHECK pcts_suman_100): pasado=" + pctPasado
                            + ", presente=" + pctPresente + ", futuro=" + pctFuturo
                            + " (suma=" + (pctPasado + pctPresente + pctFuturo) + ")");
        }
    }

    private static void requireEnRango(int pct, String nombre) {
        if (pct < 0 || pct > 100) {
            throw new IllegalArgumentException(nombre + " debe estar entre 0 y 100: " + pct);
        }
    }
}
