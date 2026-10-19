package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * El cumplimiento de UNA persona hábito por hábito en un rango de fechas, para el mensaje del semáforo (D-277).
 * Lo implementa {@code habits}.
 *
 * <p><b>Vive en {@code points.api} y no en {@code habits.api}</b> por el mismo motivo que
 * {@link ConteoDiarioHabitosFinder}: {@code habits} ya depende de {@code points} para otorgar puntos, así que
 * {@code points} no puede importar {@code habits} sin crear un ciclo que Spring Modulith rechaza. El consumidor
 * declara, el proveedor implementa. (El plan de la spec 006 lo ponía en {@code habits/api/}; imposible por el ciclo,
 * E-630.)
 */
public interface CumplimientoPorHabitoFinder {

    /**
     * @param desde primer día (fecha local de la persona, inclusive)
     * @param hasta último día (inclusive)
     * @return un elemento por hábito con al menos un registro en el rango; vacío si no hubo ninguno
     */
    List<CumplimientoDeHabito> enRango(UserId participante, LocalDate desde, LocalDate hasta);
}
