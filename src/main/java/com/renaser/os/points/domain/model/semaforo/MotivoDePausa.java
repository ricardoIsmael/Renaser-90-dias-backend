package com.renaser.os.points.domain.model.semaforo;

/**
 * Por qué una {@link PausaDeMedicion} deja días sin medir. Las dos viven en {@code semaforo_pausas}
 * (columna {@code motivo}, V72) porque para el cálculo son lo mismo —días que no entran al
 * promedio— pero nacen, se muestran y terminan distinto.
 */
public enum MotivoDePausa {

    /**
     * La pidió la persona: staff con programa propio, con fecha de regreso
     * ({@code PUT /api/v1/me/semaforo/pausa}, respuesta del dueño del 2026-09-25). Es la única que se
     * muestra como «pausa» y la única que se puede cambiar o terminar a mano.
     */
    PEDIDA_POR_LA_PERSONA,

    /**
     * La cuenta estuvo suspendida (D-209, decisión del dueño del 2026-09-27: «Que no se midan»). No la
     * pide nadie: la anota {@code points} al enterarse del cambio de estado de la cuenta, no tiene
     * fecha de regreso y termina cuando la reactivan.
     */
    CUENTA_SUSPENDIDA
}
