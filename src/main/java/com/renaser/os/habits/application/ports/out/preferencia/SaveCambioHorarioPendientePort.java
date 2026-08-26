package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.shared.domain.UserId;

public interface SaveCambioHorarioPendientePort {

    CambioHorarioPendiente save(CambioHorarioPendiente cambio);

    /** Idempotente: borrar lo inexistente no falla — se llama siempre que un cambio se aplica de inmediato. */
    void borrar(UserId participanteId, HabitoId habitoId);
}
