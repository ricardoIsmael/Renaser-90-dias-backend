package com.renaser.os.rocks.application.ports.out.rocamaestra;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadRocaMaestraPort {

    List<RocaMaestra> deParticipante(UserId participanteId);

    Optional<RocaMaestra> deParticipanteYEje(UserId participanteId, EjeObjetivo eje);
}
