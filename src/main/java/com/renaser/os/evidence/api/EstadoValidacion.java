package com.renaser.os.evidence.api;

/**
 * Espejo del tipo Postgres {@code estado_validacion} — la máquina de estados de
 * validación de una evidencia. Vive en {@code evidence.api} por el mismo motivo que
 * {@link TipoEvidencia}: es parte del contrato público de {@code evidence} (aparece en
 * {@link RegistrarEvidenciaPort.EvidenciaRegistrada}).
 *
 * <pre>
 *   PENDIENTE --IA aprueba--&gt;        VALIDA
 *   PENDIENTE --IA rechaza--&gt;        RECHAZADA
 *   PENDIENTE --IA falla/no disponible, intentosIa &lt; 3--&gt;  PENDIENTE (incrementa intentosIa)
 *   PENDIENTE --IA falla/no disponible, intentosIa == 3--&gt; REVISION_MANUAL   (fallback humano)
 *   REVISION_MANUAL --admin aprueba--&gt; VALIDA
 *   REVISION_MANUAL --admin rechaza--&gt; RECHAZADA
 *   VALIDA/RECHAZADA --admin anula--&gt;  ANULADA_ADMIN
 * </pre>
 */
public enum EstadoValidacion {
    PENDIENTE,
    VALIDA,
    RECHAZADA,
    REVISION_MANUAL,
    ANULADA_ADMIN
}
