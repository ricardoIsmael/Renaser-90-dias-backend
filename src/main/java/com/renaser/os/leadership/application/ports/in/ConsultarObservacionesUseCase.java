package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Las observaciones registradas sobre un mentor, las más recientes primero, de a páginas (D-241). */
public interface ConsultarObservacionesUseCase {

    PaginaDeObservaciones observaciones(UserId actorId, UserId mentorId, Instant antesDe);

    /**
     * @param siguiente           el cursor de la página siguiente; null si no hay más
     * @param nombresDePersonas   spec 005: el nombre de la persona de cada observación que trata de alguien; sin clave si
     *                            la observación no trata de nadie o la cuenta ya no existe
     */
    record PaginaDeObservaciones(List<ObservacionDeMentor> observaciones, Instant siguiente,
                                 Map<UserId, String> nombresDePersonas) {

        public PaginaDeObservaciones {
            nombresDePersonas = nombresDePersonas == null ? Map.of() : Map.copyOf(nombresDePersonas);
        }

        public PaginaDeObservaciones(List<ObservacionDeMentor> observaciones, Instant siguiente) {
            this(observaciones, siguiente, Map.of());
        }
    }
}
