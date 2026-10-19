package com.renaser.os.evidence.api;

import java.time.Instant;
import java.util.List;
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
 * @param evidencias       cada fila, de la más vieja a la más nueva: con eso la ficha de seguimiento ofrece abrir
 *                         cada una (foto, audio, video o texto) sin pedir el listado general (spec 005). Solo
 *                         identidad, tipo e instante: el archivo se firma al abrirlo, con la autorización de
 *                         {@code evidence}.
 */
public record EntregaDeEvidencia(UUID registroHabitoId, UUID evidenciaId, Instant primeraEntregaEn,
                                  EstadoValidacion estadoRevision, int archivos, List<ArchivoEntregado> evidencias) {

    public EntregaDeEvidencia {
        evidencias = evidencias == null ? List.of() : List.copyOf(evidencias);
    }

    /** Sin el detalle de cada archivo: la forma de los llamadores anteriores a la spec 005. */
    public EntregaDeEvidencia(UUID registroHabitoId, UUID evidenciaId, Instant primeraEntregaEn,
                              EstadoValidacion estadoRevision, int archivos) {
        this(registroHabitoId, evidenciaId, primeraEntregaEn, estadoRevision, archivos, List.of());
    }

    /** Aprobada. Se muestra aparte y no cambia el porcentaje de cumplimiento. */
    public boolean verificada() {
        return estadoRevision == EstadoValidacion.VALIDA;
    }

    /** Una fila de {@code evidencias} de esa obligación: cuál, de qué tipo y cuándo se subió. */
    public record ArchivoEntregado(UUID evidenciaId, TipoEvidencia tipo, Instant subidaEn) {
    }
}
