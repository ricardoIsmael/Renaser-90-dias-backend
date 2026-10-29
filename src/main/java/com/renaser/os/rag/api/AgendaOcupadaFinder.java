package com.renaser.os.rag.api;

import com.renaser.os.shared.domain.TramoDelDia;
import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

/**
 * Las horas en que la persona dijo estar ocupada cada día de la semana (D-161): trabajo, clases, lo que le contó al
 * acompañante y confirmó guardar. Sin etiqueta: el dato es solo el tramo. Lo lee {@code mentoring} para calcular los
 * huecos de horario (spec 015); la agenda es de {@code rag} y nadie más lee la tabla.
 *
 * <p><b>Solo lectura y sin autorizar:</b> quien llama ya estableció que el actor puede ver a esa persona. Responde por
 * el id que le dan. Un tramo nocturno ya viene partido en el final de un día y el principio del siguiente.
 */
public interface AgendaOcupadaFinder {

    /**
     * @return los tramos de cada día de la semana, en orden y sin pisarse; un día sin horas ocupadas no aparece.
     *         Vacío si la persona nunca guardó nada, nunca null
     */
    Map<DayOfWeek, List<TramoDelDia>> semanaDe(UserId participanteId);
}
