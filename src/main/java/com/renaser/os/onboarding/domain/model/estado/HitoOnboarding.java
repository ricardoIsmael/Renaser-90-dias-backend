package com.renaser.os.onboarding.domain.model.estado;

/**
 * Los cuatro hitos de aceptacion de {@code estado_onboarding}, cada uno con su propia
 * columna timestamp ({@code terminos_aceptados_en}, {@code pacto_aceptado_en},
 * {@code pacto_firmado_en}, {@code rocas_sync_aceptado_en}). {@code PACTO_FIRMADO} es la
 * firma del Pacto de Fase I DENTRO del onboarding — no confundir con {@code contratos_fase}
 * de `phasecontracts`, que firma las fases II-IV (ver javadoc de
 * {@code ContratoFase.requireFirmable}: "La Fase 1 se firma en el Pacto del onboarding").
 */
public enum HitoOnboarding {
    TERMINOS,
    PACTO,
    PACTO_FIRMADO,
    ROCAS_SYNC
}
