package com.renaser.os.habits.application.ports.out.desbloqueo;

import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadDesbloqueoHabitoPort {

    List<DesbloqueoHabito> deParticipante(UserId participanteId);

    /** Relectura puntual tras {@link SaveDesbloqueoHabitoPort#elegirSiFalta} — devuelve el estado
     * canonico sin importar si esta llamada gano o perdio la carrera de creacion. */
    Optional<DesbloqueoHabito> deParticipanteYHabito(UserId participanteId, HabitoId habitoId);
}
