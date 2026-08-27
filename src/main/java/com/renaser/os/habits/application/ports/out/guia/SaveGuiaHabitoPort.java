package com.renaser.os.habits.application.ports.out.guia;

import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;

public interface SaveGuiaHabitoPort {

    GuiaHabito save(GuiaHabito guia);

    /** {@code ON DELETE CASCADE} hacia {@code adjuntos_guia} — borrar la guia se lleva sus adjuntos. */
    void eliminar(GuiaHabitoId id);
}
