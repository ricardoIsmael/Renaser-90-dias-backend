package com.renaser.os.habits.infrastructure.adapter.in.rest.eleccion;

import com.renaser.os.habits.application.ports.in.eleccion.ElegirDiaSemanalUseCase;
import com.renaser.os.habits.application.ports.in.eleccion.ElegirDiaSemanalUseCase.ElegirDiaSemanalCommand;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Ruta literal del contrato viejo (D-36): {@code PUT /api/v1/weekly-habit-days/{habitId}}. */
@RestController
@RequestMapping("/api/v1/weekly-habit-days")
public class WeeklyHabitDayController {

    private final ElegirDiaSemanalUseCase elegirUseCase;

    public WeeklyHabitDayController(ElegirDiaSemanalUseCase elegirUseCase) {
        this.elegirUseCase = elegirUseCase;
    }

    @PutMapping("/{habitId}")
    public WeeklyHabitDayResponse elegir(@ActorAutenticado UserId actor, @PathVariable UUID habitId,
                                          @RequestBody @Valid ChooseWeeklyHabitDayRequest request) {
        var eleccion = elegirUseCase.elegir(new ElegirDiaSemanalCommand(actor, HabitoId.of(habitId),
                request.date()));
        return WeeklyHabitDayResponse.from(eleccion);
    }
}
