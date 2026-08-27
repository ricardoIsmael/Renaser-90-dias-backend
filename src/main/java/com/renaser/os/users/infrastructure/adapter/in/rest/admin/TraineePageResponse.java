package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.PaginaTrainees;

import java.util.List;

public record TraineePageResponse(List<TraineeSummaryResponse> content, long total, int page, int size) {

    public static TraineePageResponse from(PaginaTrainees pagina) {
        List<TraineeSummaryResponse> content = pagina.contenido().stream().map(TraineeSummaryResponse::from).toList();
        return new TraineePageResponse(content, pagina.total(), pagina.page(), pagina.size());
    }
}
