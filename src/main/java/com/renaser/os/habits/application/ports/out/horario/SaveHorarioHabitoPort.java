package com.renaser.os.habits.application.ports.out.horario;

import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;

public interface SaveHorarioHabitoPort {

    HorarioHabito save(HorarioHabito horario);

    /** Panel admin, hueco #11. {@code ON DELETE CASCADE} desde {@code horarios_habito} al
     * habito (no al reves) — borrar un horario no arrastra nada mas, siempre es seguro. */
    void eliminar(HorarioHabitoId id);
}
