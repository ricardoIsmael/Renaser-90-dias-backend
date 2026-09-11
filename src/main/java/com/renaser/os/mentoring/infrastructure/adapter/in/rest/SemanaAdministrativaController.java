package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.mentoring.application.ports.in.ConsultarSeguimientoSemanalUseCase.SemanaDelAlumno;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemanaAdministrativaUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemanaAdministrativaUseCase.ConsultaSemanaAdministrativa;
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
 * {@code GET /api/v1/admin/trainees/{traineeId}/weekly-progress?weekStart=YYYY-MM-DD}
 *
 * <p>La semana de cualquier aprendiz, para administracion. Contesta lo MISMO que
 * {@link SeguimientoSemanalController} —mismas obligaciones historicas, mismo motor— pero por otra
 * puerta y con otra autorizacion: aca el permiso es de rol y no exige acompañar al alumno; alla
 * exige relacion vigente y sigue negando a un exmentor. Son dos preguntas distintas y por eso son
 * dos rutas, no un parametro.
 *
 * <p>Cuelga de {@code /admin/trainees/{id}} porque es la misma ficha que ya sirve
 * {@code GET /admin/trainees/{id}/habits}: configuracion por un lado, cumplimiento por el otro. No
 * lleva {@code groupId}: el administrador mira a la persona, y un aprendiz sin grupo vigente igual
 * tiene semana.
 */
@RestController
@RequestMapping("/api/v1/admin/trainees/{traineeId}/weekly-progress")
public class SemanaAdministrativaController {

    private final ConsultarSemanaAdministrativaUseCase consultarSemana;

    public SemanaAdministrativaController(ConsultarSemanaAdministrativaUseCase consultarSemana) {
        this.consultarSemana = consultarSemana;
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES,
            scope = "SeguimientoService.requireAdminActivo: ADMIN/ALCHEMIST activo, sin exigir relacion con el alumno")
    @GetMapping
    public SemanaDelAlumno semana(@ActorAutenticado UserId actorId,
                                   @PathVariable UUID traineeId,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        // weekStart null = la semana en curso; cual es "en curso" lo decide el SERVICIO en la zona
        // del alumno. El adaptador no resuelve "hoy" (ArchitectureTest lo prohibe).
        return consultarSemana.semanaDe(new ConsultaSemanaAdministrativa(actorId, traineeId, weekStart));
    }
}
