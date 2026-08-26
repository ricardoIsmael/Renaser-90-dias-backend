package com.renaser.os.habits.application.ports.out.renombre;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.renombre.RenombreHabito;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface LoadRenombreHabitoPort {

    Optional<RenombreHabito> porParticipanteYHabito(UserId participanteId, HabitoId habitoId);
}
