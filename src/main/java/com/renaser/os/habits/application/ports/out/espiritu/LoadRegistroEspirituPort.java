package com.renaser.os.habits.application.ports.out.espiritu;

import com.renaser.os.habits.domain.model.espiritu.RegistroEspiritu;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadRegistroEspirituPort {

    Optional<RegistroEspiritu> porParticipanteYDia(UserId participanteId, int dia);

    /** El de mayor {@code dia} — el "puntero" que mueve el state machine lazy (ensureAdvanced). */
    Optional<RegistroEspiritu> ultimoDe(UserId participanteId);

    /** Todos los tracks del participante, para construir la vista dia-por-dia del estado. */
    List<RegistroEspiritu> todosDe(UserId participanteId);
}
