package com.renaser.os.habits.infrastructure.adapter.in.rest.acompanamiento;

import com.renaser.os.habits.application.ports.in.acompanamiento.ConsultaDeAcompanante;
import com.renaser.os.habits.application.ports.in.acompanamiento.ConsultarHabitosDeAlumnoUseCase;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitosaprendiz.TraineeHabitsResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/habits}
 *
 * <p>La configuracion de habitos del alumno para quien lo acompaña. Misma respuesta, campo por
 * campo, que {@code GET /api/v1/admin/trainees/{id}/habits}: es el mismo dato y no hay motivo para
 * que la app tenga que entender dos formas. Lo que cambia es el guard — acompañante vigente de ESE
 * grupo y alumno de ESE grupo, comprobado en el servicio.
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/habits")
public class HabitosDelAlumnoController {

    private final ConsultarHabitosDeAlumnoUseCase consultarHabitos;

    public HabitosDelAlumnoController(ConsultarHabitosDeAlumnoUseCase consultarHabitos) {
        this.consultarHabitos = consultarHabitos;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "AcompanamientoDeAlumnoService: acompanante vigente del grupo Y el alumno debe pertenecer a ese grupo")
    @GetMapping
    public TraineeHabitsResponse habitos(@ActorAutenticado UserId actorId,
                                          @PathVariable UUID groupId,
                                          @PathVariable UUID userId) {
        return TraineeHabitsResponse.from(
                consultarHabitos.habitosDeAlumno(new ConsultaDeAcompanante(actorId, groupId, UserId.of(userId))));
    }
}
