package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase.RankingDeGrupos;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.UUID;

/**
 * {@code GET /api/v1/ranking/groups?cohortId=…&month=YYYY-MM} — comparación entre grupos.
 *
 * <p>Ruta nueva y no una variante de {@code /api/v1/ranking/{tipo}}: el tipo CELL existente
 * significa otra cosa —puntos de personas dentro de su célula— y reinterpretarlo cambiaría en
 * silencio lo que ya ve la app (contracts.md).
 *
 * <p>Devuelve nombre de grupo, posición, porcentaje y muestra. Nunca datos de los miembros.
 */
@RestController
@RequestMapping("/api/v1/ranking/groups")
public class RankingDeGruposController {

    private final ConsultarRankingDeGruposUseCase consultarRanking;

    public RankingDeGruposController(ConsultarRankingDeGruposUseCase consultarRanking) {
        this.consultarRanking = consultarRanking;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "resumen de comunidad: solo nombre de grupo, posicion, porcentaje y muestra; ningun dato de sus miembros")
    @GetMapping
    public RankingDeGrupos ranking(@ActorAutenticado UserId actorId,
                                    @RequestParam UUID cohortId,
                                    @RequestParam String month) {
        return consultarRanking.ranking(actorId, cohortId, YearMonth.parse(month));
    }
}
