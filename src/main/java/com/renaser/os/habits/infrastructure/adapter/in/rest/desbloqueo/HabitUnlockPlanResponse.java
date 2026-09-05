package com.renaser.os.habits.infrastructure.adapter.in.rest.desbloqueo;

import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase.ItemDesbloqueo;
import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase.PlanDesbloqueo;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HabitUnlockPlanResponse(boolean enabled, List<HabitUnlockItemResponse> items) {

    public static HabitUnlockPlanResponse from(PlanDesbloqueo plan) {
        return new HabitUnlockPlanResponse(plan.enabled(), plan.items().stream()
                .map(HabitUnlockItemResponse::from).toList());
    }

    /**
     * {@code paused}/{@code pausedUntil} se agregaron con V31. Antes NINGUNA respuesta de lectura
     * exponia el estado de pausa, asi que el interruptor del Plan no podia pintarse con el valor
     * real: se apagaba un habito, se recargaba la app y volvia a verse encendido. Es el mismo
     * sintoma que D-87 creia haber cerrado — se persistia, pero no se leia de vuelta.
     *
     * <p>{@code pausedUntil} es {@code null} cuando la pausa es indefinida. {@code paused} dice si
     * hay una pausa REGISTRADA; si ademas sigue vigente hoy lo decide el cliente comparando con la
     * fecha, o el dominio via {@code DesbloqueoHabito.estaPausadoEl}.
     */
    public record HabitUnlockItemResponse(UUID habitId, int unlockDay, Instant chosenAt, boolean paused,
                                            LocalDate pausedUntil) {

        public static HabitUnlockItemResponse from(ItemDesbloqueo item) {
            return new HabitUnlockItemResponse(item.habitoId().value(), item.diaDesbloqueo(), item.elegidoEn(),
                    item.pausado(), item.pausadoHasta());
        }

        /** Respuesta de {@code PUT .../{habitId}} — la fila recien asegurada por {@code ElegirHabitoUseCase}. */
        public static HabitUnlockItemResponse from(DesbloqueoHabito desbloqueo) {
            return new HabitUnlockItemResponse(desbloqueo.habitoId().value(), desbloqueo.diaDesbloqueo(),
                    desbloqueo.elegidoEn(), desbloqueo.estaPausado(), desbloqueo.pausadoHasta());
        }
    }
}
