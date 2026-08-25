package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

/**
 * Espejo del tipo Postgres {@code tipo_pregunta_onboarding}. Nombres identicos al dominio a
 * proposito, pero se mantiene un tipo local (igual que {@code FaseProgramaJpa} en
 * `phasecontracts`): la entidad JPA nunca usa un tipo de dominio directo, siempre traduce
 * via el mapper.
 */
public enum TipoPreguntaOnboardingJpa {
    TEXTO,
    AREA_TEXTO,
    NUMERO,
    ESCALA,
    SELECCION_UNICA,
    SELECCION_MULTIPLE,
    AUDIO,
    FIRMA,
    CASILLA,
    FECHA,
    ARCHIVO
}
