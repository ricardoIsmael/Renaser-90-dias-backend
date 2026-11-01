package com.renaser.os.onboarding.domain.model.mapa;

/**
 * El Mapa de Renacimiento se retiró (spec 026, FR-021 a FR-025): ya no se puede continuar ni terminar. Sus datos se conservan y se pueden leer; lo que cambia es
 * que nadie escribe en él. Los objetivos de 90 días se escriben en Plan o nacen del diagnóstico. 409 con su código, para que una app anterior lo distinga.
 */
public class MapaRetiradoException extends IllegalStateException {

    public static final String CODIGO = "MAPA_RETIRADO";
    public static final String MENSAJE = "El Mapa de Renacimiento ya no está disponible. Tus objetivos los escribes en Plan.";

    public MapaRetiradoException() {
        super(MENSAJE);
    }
}
