package com.renaser.os.rag.domain.model.analisis;

/**
 * Cómo está el análisis de un periodo que ya cerró (spec 031). Espejo de la columna {@code analisis_80_20.estado}.
 * <ul>
 *   <li>{@link #PENDIENTE}: cerró y falta generarlo; {@code intentos} cuenta los fallidos.</li>
 *   <li>{@link #LISTO}: el texto está y es el definitivo.</li>
 *   <li>{@link #SIN_DATOS}: el periodo no alcanzó el mínimo; no se llamó a la IA y no se vuelve a evaluar.</li>
 *   <li>{@link #AGOTADO}: se acabaron los intentos; el periodo se queda sin análisis, con sus cifras.</li>
 * </ul>
 */
public enum EstadoDelAnalisis {
    PENDIENTE,
    LISTO,
    SIN_DATOS,
    AGOTADO
}
