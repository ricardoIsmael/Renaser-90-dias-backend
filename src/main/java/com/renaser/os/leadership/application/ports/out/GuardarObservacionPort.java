package com.renaser.os.leadership.application.ports.out;

import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;

/**
 * Inserta una observación. Solo INSERT: {@code observaciones_mentor} es append-only (V88).
 *
 * <p>Si la misma {@code (autor, claveOperacion)} se inserta dos veces a la vez, la segunda falla con
 * {@link IllegalStateException} (409): el reintento la encuentra ya guardada.
 */
public interface GuardarObservacionPort {

    ObservacionDeMentor insertar(ObservacionDeMentor observacion);
}
