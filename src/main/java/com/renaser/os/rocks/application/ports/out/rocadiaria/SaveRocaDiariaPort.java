package com.renaser.os.rocks.application.ports.out.rocadiaria;

import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;

import java.util.List;

public interface SaveRocaDiariaPort {

    RocaDiaria save(RocaDiaria rocaDiaria);

    List<RocaDiaria> saveAll(List<RocaDiaria> rocasDiarias);
}
