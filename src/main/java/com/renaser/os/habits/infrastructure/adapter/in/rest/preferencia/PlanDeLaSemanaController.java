package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.PlanDeLaSemanaUseCase;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * «Solo esta semana» (spec 028, D-296): el plan de UN habito en las fechas de una semana. Vive en su propio controlador, junto al de
 * {@link HabitPreferenceController}, para no mezclar lo que vale «todas las semanas» (que ya existe y no cambia) con lo que vale una
 * sola. Contrato en {@code specs/028-planificacion-semana-y-mes/contracts/habit-preferences-week.md}.
 */
@RestController
@RequestMapping("/api/v1/habit-preferences/{habitId}/week")
@Funcionalidad("habitos.ajustes")
public class PlanDeLaSemanaController {

    private final PlanDeLaSemanaUseCase useCase;

    public PlanDeLaSemanaController(PlanDeLaSemanaUseCase useCase) {
        this.useCase = useCase;
    }

    /** Los siete dias de la semana que empieza ese lunes ({@code 400} si no es lunes), con el plan de siempre y lo «solo esta semana». */
    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public WeekResponse consultar(@ActorAutenticado UserId actor, @PathVariable UUID habitId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        SemanaCalendaria semana = SemanaCalendaria.desde(weekStart);
        return WeekResponse.from(semana, useCase.consultar(actor, HabitoId.of(habitId), semana));
    }

    /**
     * Guarda solo lo tocado de varias fechas, todo o nada. {@code 204}; {@code 400} con una fecha fuera de la semana o que ya no se puede
     * cambiar, o un valor invalido; {@code 404} si el habito no le corresponde; {@code 409} al apagar un obligatorio. No consume cupo.
     */
    @RequiresPermission(Permission.USE_APP)
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardar(@ActorAutenticado UserId actor, @PathVariable UUID habitId, @RequestBody @Valid WeekChangesRequest request) {
        useCase.guardar(actor, HabitoId.of(habitId), SemanaCalendaria.desde(request.weekStart()), request.toChanges());
    }

    /** Vuelve al plan de siempre: borra lo «solo esta semana» de ese habito en esa semana (fechas posteriores a hoy). Idempotente. */
    @RequiresPermission(Permission.USE_APP)
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void volverAlPlanDeSiempre(@ActorAutenticado UserId actor, @PathVariable UUID habitId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        useCase.volverAlPlanDeSiempre(actor, HabitoId.of(habitId), SemanaCalendaria.desde(weekStart));
    }
}
