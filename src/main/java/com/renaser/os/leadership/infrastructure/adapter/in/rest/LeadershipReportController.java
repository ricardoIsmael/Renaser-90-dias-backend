package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/leadership/report?month=YYYY-MM}: el reporte del cuerpo de mentores de un mes (SDD
 * 002, RL-19..RL-23; D-241). Sin {@code month}, el mes en curso.
 */
@RestController
@RequestMapping("/api/v1/leadership/report")
public class LeadershipReportController {

    private final ConsultarReporteDeMentoresUseCase reporte;

    public LeadershipReportController(ConsultarReporteDeMentoresUseCase reporte) {
        this.reporte = reporte;
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_REPORT,
            scope = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1)")
    @GetMapping
    public MentorReportResponse reporte(@ActorAutenticado UserId actorId,
                                        @RequestParam(required = false) String month) {
        return MentorReportResponse.from(reporte.reporte(actorId, month));
    }
}
