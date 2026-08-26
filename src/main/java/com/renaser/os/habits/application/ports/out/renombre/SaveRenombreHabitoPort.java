package com.renaser.os.habits.application.ports.out.renombre;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.renombre.RenombreHabito;
import com.renaser.os.shared.domain.UserId;

public interface SaveRenombreHabitoPort {

    RenombreHabito save(RenombreHabito renombre);

    /** Idempotente: borrar lo inexistente no falla. Vuelve al nombre del catalogo. */
    void borrar(UserId participanteId, HabitoId habitoId);
}
