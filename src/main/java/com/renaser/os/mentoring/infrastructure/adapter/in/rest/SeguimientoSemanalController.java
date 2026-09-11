package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.mentoring.application.ports.in.ConsultarSeguimientoSemanalUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarSeguimientoSemanalUseCase.ConsultaSemana;
import com.renaser.os.mentoring.application.ports.in.ConsultarSeguimientoSemanalUseCase.SemanaDelAlumno;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/progress?weekStart=YYYY-MM-DD}
 *
 * <p>Una consulta semanal agregada en vez de un endpoint por hábito. El servicio comprueba dos
 * cosas antes de responder: que el actor acompañe VIGENTEMENTE ese grupo, y que el alumno pedido
 * pertenezca a él. Sin la segunda, un mentor legítimo podría leer a cualquiera pasando el id de
 * su propio grupo.
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/progress")
public class SeguimientoSemanalController {

    private final ConsultarSeguimientoSemanalUseCase consultarSemana;

    public SeguimientoSemanalController(ConsultarSeguimientoSemanalUseCase consultarSemana) {
        this.consultarSemana = consultarSemana;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "SeguimientoService: acompanante vigente del grupo Y el alumno debe pertenecer a ese grupo")
    @GetMapping
    public SemanaDelAlumno semana(@ActorAutenticado UserId actorId,
                                   @PathVariable UUID groupId,
                                   @PathVariable UUID userId,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        // weekStart null = la semana en curso. Cual es "en curso" lo decide el SERVICIO, en la
        // zona del alumno: el adaptador de entrada no puede resolver "hoy" (ArchitectureTest lo
        // prohibe, y con razon: la fecha del servidor no es la de nadie).
        return consultarSemana.semanaDe(new ConsultaSemana(actorId, groupId, userId, weekStart));
    }
}
