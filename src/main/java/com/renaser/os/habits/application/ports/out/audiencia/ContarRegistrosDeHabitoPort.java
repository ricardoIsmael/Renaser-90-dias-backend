package com.renaser.os.habits.application.ports.out.audiencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;

/** Si un hábito ya generó registros: entonces es historia y no se puede eliminar (spec 001, FR-020). */
public interface ContarRegistrosDeHabitoPort {

    boolean tieneRegistros(HabitoId habito);
}
