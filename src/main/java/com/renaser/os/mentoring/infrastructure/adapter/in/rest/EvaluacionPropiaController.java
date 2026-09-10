package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.mentoring.application.ports.in.ConsultarEvaluacionPropiaUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarEvaluacionPropiaUseCase.EvaluacionPropia;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * {@code GET /api/v1/mentor/me/evaluation?month=YYYY-MM} — la nota propia del mes.
 *
 * <p>Autoconsulta pura: se arma desde los intervalos del actor, no hay id ajeno que pasar. La
 * respuesta es agregada y no lleva nombres de alumnos, ni siquiera de los que sigue acompañando:
 * el mentor saliente conserva su resumen, no el expediente (P-07).
 */
@RestController
@RequestMapping("/api/v1/mentor/me/evaluation")
public class EvaluacionPropiaController {

    private final ConsultarEvaluacionPropiaUseCase consultarEvaluacion;

    public EvaluacionPropiaController(ConsultarEvaluacionPropiaUseCase consultarEvaluacion) {
        this.consultarEvaluacion = consultarEvaluacion;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "autoconsulta: se arma desde los intervalos del actor, sin id ajeno en la peticion")
    @GetMapping
    public EvaluacionPropia evaluacion(@ActorAutenticado UserId actorId, @RequestParam String month) {
        return consultarEvaluacion.evaluacionDe(actorId, YearMonth.parse(month));
    }
}
