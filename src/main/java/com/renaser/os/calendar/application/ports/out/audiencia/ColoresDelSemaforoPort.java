package com.renaser.os.calendar.application.ports.out.audiencia;

import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;

/** El color del semáforo vigente de cada persona (spec 044, FR-009), leído en lote (FR-012). Quien no tiene verde, amarillo o rojo no aparece. */
public interface ColoresDelSemaforoPort {

    Map<UserId, ColorDeAudiencia> colores(Collection<UserId> personas);
}
