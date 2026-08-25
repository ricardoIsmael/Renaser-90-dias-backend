package com.renaser.os.onboarding.domain.model.cuestionario;

/** Espejo 1:1 del enum Postgres {@code tipo_pregunta_onboarding} (11 valores, mismos nombres). */
public enum TipoPreguntaOnboarding {
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
