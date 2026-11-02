package com.renaser.os.habits.domain.model.medicion;

/**
 * De dónde vino el número de una {@link MedicionDiaria} (columna {@code registros_habito.origen_medicion},
 * V84 y V161, con CHECK). Existe para que lo declarado y lo medido puedan convivir y distinguirse después.
 */
public enum OrigenMedicion {

    /** Lo escribió la persona junto a su captura. */
    MANUAL,

    /**
     * Lo leyó el teléfono de Health Connect (Android 14 o más; spec 037, D-310): pasos y distancia que el sistema o un
     * reloj registraron. Siempre lleva su {@link LecturaAutomatica}.
     */
    HEALTH_CONNECT
}
