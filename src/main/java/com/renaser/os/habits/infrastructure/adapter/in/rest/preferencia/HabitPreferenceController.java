package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.CambiarEstadoHabitoEnFechaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Hueco #12 — horario personal de un habito. Ruta literal del contrato viejo (D-36):
 * {@code PATCH /api/v1/habit-preferences/{habitId}}, mas el {@code GET /api/v1/habit-preferences}
 * que faltaba para poder leer la configuracion vigente (E-55). Actor resuelto desde la sesion, con
 * respaldo por el header temporal {@code X-Actor-Id} (D-29).
 */
@RestController
@RequestMapping("/api/v1/habit-preferences")
public class HabitPreferenceController {

    private final EditarPreferenciaHorarioUseCase editarUseCase;
    private final ConsultarPreferenciasHorarioUseCase consultarUseCase;
    private final CambiarEstadoHabitoEnFechaUseCase cambiarEstadoEnFechaUseCase;
    private final EditarHorarioSemanalUseCase horarioSemanalUseCase;

    public HabitPreferenceController(EditarPreferenciaHorarioUseCase editarUseCase,
                                      ConsultarPreferenciasHorarioUseCase consultarUseCase,
                                      CambiarEstadoHabitoEnFechaUseCase cambiarEstadoEnFechaUseCase,
                                      EditarHorarioSemanalUseCase horarioSemanalUseCase) {
        this.editarUseCase = editarUseCase;
        this.consultarUseCase = consultarUseCase;
        this.cambiarEstadoEnFechaUseCase = cambiarEstadoEnFechaUseCase;
        this.horarioSemanalUseCase = horarioSemanalUseCase;
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public HabitPreferencesResponse consultar(@ActorAutenticado UserId actor,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {
        return HabitPreferencesResponse.from(consultarUseCase.consultar(actor, date), date);
    }

    @RequiresPermission(Permission.USE_APP)
    @PatchMapping("/{habitId}")
    public HabitPreferenceResponse editar(@ActorAutenticado UserId actor, @PathVariable UUID habitId,
                                           @RequestBody @Valid UpdateHabitPreferenceRequest request) {
        var resultado = editarUseCase.editar(new EditarPreferenciaHorarioCommand(actor,
                HabitoId.of(habitId), request.triggerTime(), request.limitTime(), request.reminderEnabled(),
                request.reminderMinutesBefore(), request.date()));
        return HabitPreferenceResponse.from(resultado);
    }

    /**
     * El interruptor de UN dia: {@code PATCH /api/v1/habit-preferences/{habitId}/days/{date}}.
     *
     * <p>Ruta aparte y no un campo del PATCH de arriba porque son dos operaciones con reglas
     * distintas — cuota, dia en curso y hora obligatoria (ver `CambiarEstadoHabitoEnFechaUseCase`).
     * Devuelve 204: no hay nada nuevo que el cliente no sepa ya.
     */
    @RequiresPermission(Permission.USE_APP)
    @PatchMapping("/{habitId}/days/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarEstadoDelDia(@ActorAutenticado UserId actor, @PathVariable UUID habitId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody @Valid UpdateHabitDayRequest request) {
        cambiarEstadoEnFechaUseCase.cambiarEstadoEnFecha(actor, HabitoId.of(habitId), date, request.active());
    }

    /**
     * "Los lunes a las 5 y los martes a las 4" (V39). Los SIETE dias, ya resueltos: la pantalla no
     * mezcla lo propio con lo general porque esa mezcla es la precedencia y vive acá.
     */
    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/{habitId}/weekdays")
    public WeekdayScheduleResponse consultarSemana(@ActorAutenticado UserId actor, @PathVariable UUID habitId) {
        return WeekdayScheduleResponse.from(horarioSemanalUseCase.consultar(actor, HabitoId.of(habitId)));
    }

    /** `{weekday}` es el nombre de `DayOfWeek`: MONDAY..SUNDAY, igual que `activeWeekdays`. */
    @RequiresPermission(Permission.USE_APP)
    @PutMapping("/{habitId}/weekdays/{weekday}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fijarDiaDeLaSemana(@ActorAutenticado UserId actor, @PathVariable UUID habitId,
            @PathVariable DayOfWeek weekday, @RequestBody @Valid WeekdayScheduleRequest request) {
        horarioSemanalUseCase.fijar(actor, HabitoId.of(habitId), weekday, request.triggerTime(),
                request.limitTime());
    }

    /**
     * Apaga el hábito ESE día de la semana, todas las semanas (V40). `409` si es obligatorio del
     * programa, con el mismo criterio que la pausa: no es un permiso, la operación no aplica.
     */
    @RequiresPermission(Permission.USE_APP)
    @DeleteMapping("/{habitId}/weekdays/{weekday}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarDiaDeLaSemana(@ActorAutenticado UserId actor, @PathVariable UUID habitId,
            @PathVariable DayOfWeek weekday) {
        horarioSemanalUseCase.apagar(actor, HabitoId.of(habitId), weekday);
    }

    /** Ese día vuelve a regirse por el horario general. Idempotente: borrar lo que no está es 204. */
    @RequiresPermission(Permission.USE_APP)
    @DeleteMapping("/{habitId}/weekdays/{weekday}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarDiaDeLaSemana(@ActorAutenticado UserId actor, @PathVariable UUID habitId,
            @PathVariable DayOfWeek weekday) {
        horarioSemanalUseCase.quitar(actor, HabitoId.of(habitId), weekday);
    }
}
