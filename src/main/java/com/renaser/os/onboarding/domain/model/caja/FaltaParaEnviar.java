package com.renaser.os.onboarding.domain.model.caja;

/** Lo que falta para marcar la caja enviada (spec §9: «enviar exige…»). Viaja tal cual por la API. */
public enum FaltaParaEnviar {
    /** El checklist no tiene marcados todos los elementos de la lista vigente. */
    CONTENIDO,
    /** La foto de la caja armada. */
    FOTO,
    /** El comprobante del envío. */
    COMPROBANTE
}
