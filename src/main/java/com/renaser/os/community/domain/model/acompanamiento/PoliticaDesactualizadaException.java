package com.renaser.os.community.domain.model.acompanamiento;

/**
 * Dos administradores editaron la misma política. El segundo no pisa al primero en silencio:
 * recibe el rechazo y vuelve a cargar (contracts.md, "Control de versión para edición concurrente").
 */
public class PoliticaDesactualizadaException extends RuntimeException {

    public PoliticaDesactualizadaException(int versionActual, int versionEnviada) {
        super("La politica cambio mientras editabas: version actual " + versionActual
                + ", enviaste " + versionEnviada);
    }
}
