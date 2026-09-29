package com.renaser.os.habits.domain.model.medicion;

/**
 * De dónde vino el número de una {@link MedicionDiaria} (columna {@code registros_habito.origen_medicion},
 * V84, con CHECK). Existe para que lo declarado y lo medido puedan convivir y distinguirse después.
 *
 * <p>Hoy solo {@link #MANUAL}. La fase 2 (leer la captura, o los pasos del teléfono) agrega su valor
 * aquí y en el CHECK de la base, en la misma migración.
 */
public enum OrigenMedicion {

    /** Lo escribió la persona junto a su captura. */
    MANUAL
}
