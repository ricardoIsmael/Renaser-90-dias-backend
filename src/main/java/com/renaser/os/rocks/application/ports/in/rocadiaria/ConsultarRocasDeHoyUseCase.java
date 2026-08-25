package com.renaser.os.rocks.application.ports.in.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ConsultarRocasDeHoyUseCase {

    List<RocaDiariaVista> hoy(UserId actorId);

    /** Proyección de lectura: {@code bloqueada} es Ley IV (Pareto), calculada, nunca persistida. */
    record RocaDiariaVista(RocaDiaria roca, boolean bloqueada) {
    }
}
