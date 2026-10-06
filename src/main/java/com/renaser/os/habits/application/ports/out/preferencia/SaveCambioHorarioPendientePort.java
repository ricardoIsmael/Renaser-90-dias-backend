package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.shared.domain.UserId;

public interface SaveCambioHorarioPendientePort {

    CambioHorarioPendiente save(CambioHorarioPendiente cambio);

    /**
     * Idempotente: borrar lo inexistente no falla — se llama siempre que un cambio se aplica de inmediato.
     *
     * @return {@code true} si habia un pendiente y esta llamada lo borro. Es lo que deja a UNA sola de dos promociones
     * simultaneas del mismo pendiente cobrarlo en el historial (E-557): la segunda espera el bloqueo de la fila y
     * encuentra que ya no esta.
     */
    boolean borrar(UserId participanteId, HabitoId habitoId);
}
