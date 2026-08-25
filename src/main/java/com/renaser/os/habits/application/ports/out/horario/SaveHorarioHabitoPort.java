package com.renaser.os.habits.application.ports.out.horario;

import com.renaser.os.habits.domain.model.horario.HorarioHabito;

public interface SaveHorarioHabitoPort {

    HorarioHabito save(HorarioHabito horario);
}
