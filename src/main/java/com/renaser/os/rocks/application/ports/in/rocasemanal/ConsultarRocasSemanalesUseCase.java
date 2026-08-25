package com.renaser.os.rocks.application.ports.in.rocasemanal;

import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ConsultarRocasSemanalesUseCase {

    /** {@code numeroSemana} null = la semana de programa en curso. */
    List<RocaSemanal> misRocasSemanales(UserId actorId, Integer numeroSemana);
}
