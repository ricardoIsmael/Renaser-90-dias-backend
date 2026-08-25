package com.renaser.os.points.infrastructure.adapter.in.rest.ranking;

import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.Clock;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.renaser.os.shared.domain.UserId;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ranking")
public class RankingController {

    private final ConsultarRankingUseCase consultarRankingUseCase;
    private final Clock clock;

    public RankingController(ConsultarRankingUseCase consultarRankingUseCase, Clock clock) {
        this.consultarRankingUseCase = consultarRankingUseCase;
        this.clock = clock;
    }

    @GetMapping("/{tipo}")
    public List<EntradaRankingResponse> consultar(@RequestHeader("X-Actor-Id") String actorId,
            @PathVariable TipoRanking tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        LocalDate fechaConsultada = fecha != null ? fecha : clock.today();
        return consultarRankingUseCase.consultar(UserId.of(actorId), tipo, fechaConsultada).stream()
                .map(EntradaRankingResponse::from)
                .toList();
    }
}
