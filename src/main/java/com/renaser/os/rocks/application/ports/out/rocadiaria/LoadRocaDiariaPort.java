package com.renaser.os.rocks.application.ports.out.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoadRocaDiariaPort {

    Optional<RocaDiaria> byId(RocaDiariaId id);

    List<RocaDiaria> deParticipanteYFecha(UserId participanteId, LocalDate fecha);

    int contarDeParticipanteYFecha(UserId participanteId, LocalDate fecha);
}
