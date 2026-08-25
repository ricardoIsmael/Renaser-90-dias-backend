package com.renaser.os.evidence.application.ports.in.evidencia;

/**
 * Procesa un lote de la cola de validación IA ({@code evidencias_cola_ia_idx}: PENDIENTE,
 * ordenada por {@code subida_en}, lote de 25 con {@code FOR UPDATE SKIP LOCKED}). Interno
 * — lo llama {@code ProcesarColaValidacionScheduler}, no se expone por REST (mismo
 * criterio que {@code rocks.ResolverEventosIgnoradosUseCase}).
 *
 * <p><b>SIN IA en este alcance</b> (decisión explícita del encargo): {@code ValidacionIAPort}
 * está implementado por {@code NoOpValidacionIAAdapter}, que siempre devuelve
 * {@code NO_DISPONIBLE}. Cada corrida de este caso de uso simplemente incrementa
 * {@code intentosIa} de las pendientes hasta que caen a {@code REVISION_MANUAL} a los 3
 * intentos — el camino de fallback a revisión humana queda completo y probado; la
 * integración real de IA es una fase futura (ver {@code docs/MODULO_EVIDENCE.md}).
 */
public interface ProcesarColaValidacionUseCase {

    /** @return cuántas evidencias se procesaron en este lote (0 a 25). */
    int procesarLote();
}
