package com.renaser.os.rocks.application.ports.out.rocasemanal;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;

import java.util.List;
import java.util.Optional;

public interface LoadRocaSemanalPort {

    Optional<RocaSemanal> byId(RocaSemanalId id);

    /** Las 0-3 rocas semanales de un participante para una semana de programa (una por eje maestro). */
    List<RocaSemanal> deParticipanteYSemana(List<RocaMaestraId> rocasMaestrasIds, int numeroSemana);

    Optional<RocaSemanal> deMaestraYSemana(RocaMaestraId rocaMaestraId, int numeroSemana);
}
