package com.renaser.os.evidence.api;

import java.time.Instant;
import java.util.UUID;

/**
 * La entrega de UNA obligación, no un archivo.
 *
 * <p>Un aprendiz puede subir tres veces la misma evidencia porque la primera salió movida; eso
 * sigue siendo una entrega. Por eso este record se devuelve uno por {@code registroHabitoId} y
 * no uno por fila de {@code evidencias} (plan.md §8).
 *
 * @param primeraEntregaEn la más temprana. Es la que acredita el cumplimiento: quedarse con la
 *                         última castigaría a quien volvió a subir por un problema técnico.
 * @param estadoRevision   veredicto. Va SEPARADO de la entrega: entregada y aprobada son dos
 *                         preguntas distintas, y la calificación del mentor mide la primera (D-03).
 * @param archivos         cuántas filas hay para esta obligación. Informativo; no multiplica nada.
 */
public record EntregaDeEvidencia(UUID registroHabitoId, UUID evidenciaId, Instant primeraEntregaEn,
                                  EstadoValidacion estadoRevision, int archivos) {

    /** Aprobada. Se muestra aparte y no cambia el porcentaje de cumplimiento. */
    public boolean verificada() {
        return estadoRevision == EstadoValidacion.VALIDA;
    }
}
