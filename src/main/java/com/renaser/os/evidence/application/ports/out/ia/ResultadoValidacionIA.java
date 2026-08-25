package com.renaser.os.evidence.application.ports.out.ia;

/** Resultado (cerrado a estos 3 valores) de pedirle a la IA que valide una evidencia. */
public enum ResultadoValidacionIA {
    APROBADA,
    RECHAZADA,
    /** Sin credenciales de IA todavía (este alcance), o error transitorio del proveedor. */
    NO_DISPONIBLE
}
