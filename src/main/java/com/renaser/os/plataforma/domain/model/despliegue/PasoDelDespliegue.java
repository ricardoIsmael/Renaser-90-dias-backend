package com.renaser.os.plataforma.domain.model.despliegue;

/** Qué hace un {@code PUT} del porcentaje sobre el despliegue que había (contrato fase 2 §3.4). */
public enum PasoDelDespliegue {
    /** No había despliegue y se pide menos de 100 %. */
    EMPIEZA,
    /** Había despliegue y se pide un porcentaje mayor, sin llegar a 100. */
    SUBE,
    /** Había despliegue y se pide un porcentaje menor. */
    BAJA,
    /** Había despliegue y se pide 100 %: «prender para todos», se borra la fila. */
    TERMINA,
    /** Lo pedido es lo que ya había (o 100 % sin despliegue): no se guarda, no se audita, no se avisa. */
    NINGUNO
}
