package com.renaser.os.rocks.application.ports.in.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ConsultarRocasDeMananaUseCase {

    List<RocaDiaria> manana(UserId actorId);
}
