package com.renaser.os.calendar.application.ports.out.audiencia;

import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * El color del semáforo de cada persona para armar una audiencia (spec 044, FR-009; spec 053, FR-004 y FR-005), leído en lote (FR-012). Es el color
 * del último día CERRADO con datos de cada persona (ayer, o el último día antes de ayer que tuvo algo medido): nunca el día en curso. Quien no tiene
 * verde, amarillo o rojo no aparece.
 */
public interface ColoresDelSemaforoPort {

    Map<UserId, ColorDeAudiencia> colores(Collection<UserId> personas);

    /** Los colores y el día del que salen (el más reciente entre todos los leídos; {@code null} si nadie tiene datos). */
    record Lectura(Map<UserId, ColorDeAudiencia> colores, LocalDate fecha) {
    }

    default Lectura leer(Collection<UserId> personas) {
        return new Lectura(colores(personas), null);
    }
}
