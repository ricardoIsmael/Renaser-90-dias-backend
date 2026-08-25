package com.renaser.os.habits.application.ports.out.habito;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadHabitoPort {

    Optional<Habito> byId(HabitoId id);

    List<Habito> catalogoActivo();

    List<Habito> personalesActivosDe(UserId participanteId);
}
