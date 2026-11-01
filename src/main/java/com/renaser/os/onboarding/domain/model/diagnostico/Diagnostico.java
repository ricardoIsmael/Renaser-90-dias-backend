package com.renaser.os.onboarding.domain.model.diagnostico;

/**
 * El diagnóstico inicial (spec 026): la ficha que reemplaza al Mapa de Renacimiento. Es un {@code flujo} más del motor de
 * onboarding, como {@code ficha_inicial}: sus secciones, preguntas, opciones y respuestas viven en las mismas tablas.
 */
public final class Diagnostico {

    /** El {@code flujo} de sus secciones ({@code secciones_onboarding}) y de su marca de terminado ({@code etapas_onboarding_completadas}). */
    public static final String FLUJO = "diagnostico";

    private Diagnostico() {
    }
}
