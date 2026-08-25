package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface LoadPreferenciaHorarioPort {

    Optional<PreferenciaHorario> porParticipanteYHabito(UserId participanteId, HabitoId habitoId);
}
