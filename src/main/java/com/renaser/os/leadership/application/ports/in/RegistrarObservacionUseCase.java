package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * El líder deja registrada una observación sobre un mentor (SDD 002, RL-15/RL-16/RL-18; D-241).
 *
 * <p>Si además se la mandó por el chat, la app ya envió el mensaje por el endpoint del chat que existe
 * y acá llega su id: este caso de uso no manda nada solo (RL-14). Repetir la misma
 * {@code claveOperacion} devuelve la observación ya creada, sin duplicarla.
 */
public interface RegistrarObservacionUseCase {

    ObservacionDeMentor registrar(RegistrarObservacionCommand comando);

    /**
     * @param mensajeId   el mensaje del chat en el que se la mandó; null si no se mandó o si falló
     * @param personaId   spec 005 (R-7): la persona de la que trata, que tiene que estar hoy en un grupo de ESE mentor;
     *                    null si no trata de nadie en particular
     * @param semanaDesde el primer día de la semana que el líder miraba al escribirla; solo con {@code personaId}
     */
    record RegistrarObservacionCommand(UserId actorId, UserId mentorId, String tipo, String texto,
                                       boolean enviadaPorChat, UUID mensajeId, String claveOperacion,
                                       UserId personaId, LocalDate semanaDesde) {

        public RegistrarObservacionCommand {
            if (semanaDesde != null && personaId == null) {
                throw new IllegalArgumentException("La semana de una observacion necesita la persona de la que trata");
            }
        }

        /** Sin persona: la forma de las observaciones de antes de la spec 005. */
        public RegistrarObservacionCommand(UserId actorId, UserId mentorId, String tipo, String texto,
                                           boolean enviadaPorChat, UUID mensajeId, String claveOperacion) {
            this(actorId, mentorId, tipo, texto, enviadaPorChat, mensajeId, claveOperacion, null, null);
        }
    }
}
