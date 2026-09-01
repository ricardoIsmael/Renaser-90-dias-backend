package com.renaser.os.points.infrastructure.adapter.in.rest.ranking;

import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingAgregadoUseCase;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ranking")
public class RankingController {

    private final ConsultarRankingUseCase consultarRankingUseCase;
    private final ConsultarRankingAgregadoUseCase consultarRankingAgregadoUseCase;
    private final Clock clock;

    public RankingController(ConsultarRankingUseCase consultarRankingUseCase,
            ConsultarRankingAgregadoUseCase consultarRankingAgregadoUseCase, Clock clock) {
        this.consultarRankingUseCase = consultarRankingUseCase;
        this.consultarRankingAgregadoUseCase = consultarRankingAgregadoUseCase;
        this.clock = clock;
    }

    /** Agregador de un solo llamado (gap #24) — ver javadoc de {@link ConsultarRankingAgregadoUseCase}. */
    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public RankingAgregadoResponse consultarAgregado(@ActorAutenticado UserId actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        LocalDate fechaConsultada = fecha != null ? fecha : clock.today();
        return RankingAgregadoResponse.from(
                consultarRankingAgregadoUseCase.agregado(actor, fechaConsultada));
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/{tipo}")
    public List<EntradaRankingResponse> consultar(@ActorAutenticado UserId actor,
            @PathVariable TipoRanking tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        LocalDate fechaConsultada = fecha != null ? fecha : clock.today();
        return consultarRankingUseCase.consultar(actor, tipo, fechaConsultada).stream()
                .map(EntradaRankingResponse::from)
                .toList();
    }
}
