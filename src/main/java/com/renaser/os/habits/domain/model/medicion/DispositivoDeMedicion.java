package com.renaser.os.habits.domain.model.medicion;

/**
 * De qué tipo de dispositivo salió una {@link LecturaAutomatica} (columna {@code dispositivo_medicion}, V161). Es lo que la
 * ficha del mentor dice: «medido por el reloj» o «medido por el teléfono». No decide nada: la clasificación la hace la app
 * con los metadatos de Health Connect, y el servidor solo la guarda.
 */
public enum DispositivoDeMedicion {

    /** El propio teléfono (el conteo nativo de Android 14). */
    TELEFONO,

    /** Un reloj o una pulsera. */
    RELOJ,

    /** Una app u otro aparato que no se puede clasificar. */
    OTRO
}
