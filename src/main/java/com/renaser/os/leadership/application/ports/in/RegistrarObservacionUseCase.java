package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.shared.domain.UserId;

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
     * @param mensajeId el mensaje del chat en el que se la mandó; null si no se mandó o si falló
     */
    record RegistrarObservacionCommand(UserId actorId, UserId mentorId, String tipo, String texto,
                                       boolean enviadaPorChat, UUID mensajeId, String claveOperacion) {
    }
}
