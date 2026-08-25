package com.renaser.os.habits.application.ports.out.horario;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;

import java.util.List;

public interface LoadHorarioHabitoPort {

    List<HorarioHabito> porHabito(HabitoId habitoId);
}
