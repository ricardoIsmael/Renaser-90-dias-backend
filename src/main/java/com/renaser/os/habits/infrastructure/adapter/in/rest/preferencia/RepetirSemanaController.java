package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.PlanDeLaSemanaSinRedUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia.WeekRepeatResponses.Offer;
import com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia.WeekRepeatResponses.Result;
import com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia.WeekRepeatResponses.Undone;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Repetir la semana (spec 028, D-296): la oferta de dejar la semana siguiente igual a la en curso, aplicarla y deshacerla. Solo hábitos.
 * Contrato en {@code specs/028-planificacion-semana-y-mes/contracts/habit-preferences-week-repeat.md}.
 */
@RestController
@RequestMapping("/api/v1/habit-preferences")
@Funcionalidad("habitos.ajustes")
public class RepetirSemanaController {

    private final RepetirSemanaUseCase useCase;
    private final PlanDeLaSemanaSinRedUseCase sinRedUseCase;

    public RepetirSemanaController(RepetirSemanaUseCase useCase, PlanDeLaSemanaSinRedUseCase sinRedUseCase) {
        this.useCase = useCase;
        this.sinRedUseCase = sinRedUseCase;
    }

    /** La oferta, o {@code 204} si no hay nada que ofrecer. */
    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/week-repeat")
    public ResponseEntity<Offer> ofrecer(@ActorAutenticado UserId actor) {
        return useCase.ofrecer(actor).map(Offer::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Aplica la repeticion. {@code 409} si reemplazaria cambios propios y {@code replace} es {@code false}. Idempotente. */
    @RequiresPermission(Permission.USE_APP)
    @PostMapping("/week-repeat")
    public Result repetir(@ActorAutenticado UserId actor, HechoSinRed sinRed, @RequestBody @Valid WeekRepeatRequest request) {
        var modelo = SemanaCalendaria.desde(request.sourceWeekStart());
        var destino = SemanaCalendaria.desde(request.targetWeekStart());
        // Spec 048 (FR-019): con X-Id-Cliente y X-Hecho-En es lo hecho sin red, con su hora real; sin ellas, lo de siempre.
        return Result.from(sinRed == null ? useCase.repetir(actor, modelo, destino, request.replace())
                : sinRedUseCase.repetir(actor, modelo, destino, request.replace(), sinRed));
    }

    /** «Deshacer»: toda esa semana vuelve al plan de siempre (fechas posteriores a hoy). Idempotente. */
    @RequiresPermission(Permission.USE_APP)
    @DeleteMapping("/weeks/{weekStart}")
    public Undone deshacer(@ActorAutenticado UserId actor, HechoSinRed sinRed,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        var semana = SemanaCalendaria.desde(weekStart);
        return new Undone(sinRed == null ? useCase.deshacerSemana(actor, semana) : sinRedUseCase.deshacerSemana(actor, semana, sinRed));
    }
}
