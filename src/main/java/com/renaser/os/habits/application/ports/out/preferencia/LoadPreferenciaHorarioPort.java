package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadPreferenciaHorarioPort {

    Optional<PreferenciaHorario> porParticipanteYHabito(UserId participanteId, HabitoId habitoId);

    /** UNA sola consulta para N habitos de un mismo participante — proyecciones de lectura (hueco #10). */
    List<PreferenciaHorario> porParticipanteYHabitos(UserId participanteId, Collection<HabitoId> habitoIds);
}
