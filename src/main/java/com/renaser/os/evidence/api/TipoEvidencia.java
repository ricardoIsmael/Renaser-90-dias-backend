package com.renaser.os.evidence.api;

/**
 * Espejo del tipo Postgres {@code tipo_evidencia}. Vive en {@code evidence.api} (no en
 * {@code evidence.domain}) por la misma razón que {@code points.api.MotivoPuntos} —
 * ver su javadoc y la decisión RK-1 de {@code docs/MODULO_ROCKS.md}: es un parámetro
 * de {@link RegistrarEvidenciaPort#registrar}, la única puerta pública de {@code evidence}
 * para otros módulos ({@code rocks}, {@code habits}). Dejarlo en un paquete interno
 * obligaría a los llamadores a importar un tipo fuera de {@code @NamedInterface("api")}.
 * Un solo enum, sin copia paralela en {@code domain}: {@link com.renaser.os.evidence.domain.model.evidencia.Evidencia}
 * lo usa directo desde acá.
 */
public enum TipoEvidencia {
    FOTO,
    VIDEO,
    AUDIO,
    TEXTO,
    CAPTURA
}
