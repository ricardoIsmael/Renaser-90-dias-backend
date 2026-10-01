package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/** Las observaciones registradas sobre un mentor, las más recientes primero, de a páginas (D-241). */
public interface ConsultarObservacionesUseCase {

    PaginaDeObservaciones observaciones(UserId actorId, UserId mentorId, Instant antesDe);

    /** @param siguiente el cursor de la página siguiente; null si no hay más */
    record PaginaDeObservaciones(List<ObservacionDeMentor> observaciones, Instant siguiente) {
    }
}
