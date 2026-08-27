package com.renaser.os.habits.application.ports.out.horario;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadHorarioHabitoPort {

    List<HorarioHabito> porHabito(HabitoId habitoId);

    /** UNA sola consulta para N habitos — para proyecciones de lectura (hueco #10), nunca N+1. */
    List<HorarioHabito> porHabitos(Collection<HabitoId> habitoIds);

    /** Panel admin, hueco #11: editar/borrar un horario puntual por su id. */
    Optional<HorarioHabito> byId(HorarioHabitoId id);
}
