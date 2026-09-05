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
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ranking")
public class RankingController {

    /** Zona del padron. Igual que `ParticipacionPrograma.ZONA_POR_DEFECTO`: hoy todos en Lima. */
    private static final ZoneId ZONA_PADRON = ZoneId.of("America/Lima");

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
    /**
     * "Hoy" del padron, no del servidor.
     *
     * <p>Antes era {@code clock.today()}, la fecha del proceso. Con el backend en UTC y el padron
     * en Lima (UTC-5), entre las 19:00 y la medianoche hora local se pedia el ranking de MANANA —
     * cuyo snapshot todavia no existe, porque {@code SnapshotRankingScheduler} corre a las 05:05
     * UTC (00:05 de Lima). Resultado: el ranking se veia vacio todas las noches, en la misma
     * franja de cinco horas que dejaba en blanco la pantalla de habitos (E-105).
     *
     * <p>La zona es la del padron y no la del actor porque el ranking es una tabla comun: si cada
     * uno lo pidiera en su huso, dos personas de la misma celula verian rankings de dias distintos.
     * El dia del ranking lo define el snapshot, y el snapshot se genera con el calendario de Lima.
     */
    private LocalDate hoyDelPadron() {
        return clock.now().atZone(ZONA_PADRON).toLocalDate();
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public RankingAgregadoResponse consultarAgregado(@ActorAutenticado UserId actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        LocalDate fechaConsultada = fecha != null ? fecha : hoyDelPadron();
        return RankingAgregadoResponse.from(
                consultarRankingAgregadoUseCase.agregado(actor, fechaConsultada));
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/{tipo}")
    public List<EntradaRankingResponse> consultar(@ActorAutenticado UserId actor,
            @PathVariable TipoRanking tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        LocalDate fechaConsultada = fecha != null ? fecha : hoyDelPadron();
        return consultarRankingUseCase.consultar(actor, tipo, fechaConsultada).stream()
                .map(EntradaRankingResponse::from)
                .toList();
    }
}
