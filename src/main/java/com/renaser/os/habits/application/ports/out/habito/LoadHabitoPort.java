package com.renaser.os.habits.application.ports.out.habito;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadHabitoPort {

    Optional<Habito> byId(HabitoId id);

    /** UNA sola consulta para N ids — para proyecciones de lectura (hueco #10), nunca N+1. */
    List<Habito> porIds(Collection<HabitoId> ids);

    List<Habito> catalogoActivo();

    List<Habito> personalesActivosDe(UserId participanteId);
}
