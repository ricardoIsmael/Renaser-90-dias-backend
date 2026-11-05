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
    HEALTH_CONNECT,

    /**
     * La suma de las carreras con GPS del día (spec 042, D-323): el teléfono grabó la carrera y la mandó al terminar. El detalle
     * (tramos, ruta, por revisar) vive en la tabla {@code carreras}; la medición no lleva {@link LecturaAutomatica}.
     */
    GPS
}
