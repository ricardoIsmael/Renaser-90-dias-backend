package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasAgendadasUseCase.RocasAgendadas;

import java.time.LocalDate;
import java.util.List;

/** {@code GET /api/v1/rocks/upcoming}: el rango que cubre la lista y las rocas de esos dias (D-217). */
public record RocasAgendadasResponse(LocalDate desde, LocalDate hasta, List<RocaDiariaResponse> rocas) {

    public static RocasAgendadasResponse from(RocasAgendadas agendadas) {
        return new RocasAgendadasResponse(agendadas.desde(), agendadas.hasta(),
                agendadas.rocas().stream().map(RocaDiariaResponse::from).toList());
    }
}
