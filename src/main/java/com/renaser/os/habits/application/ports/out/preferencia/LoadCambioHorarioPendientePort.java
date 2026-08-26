package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface LoadCambioHorarioPendientePort {

    Optional<CambioHorarioPendiente> porParticipanteYHabito(UserId participanteId, HabitoId habitoId);
}
