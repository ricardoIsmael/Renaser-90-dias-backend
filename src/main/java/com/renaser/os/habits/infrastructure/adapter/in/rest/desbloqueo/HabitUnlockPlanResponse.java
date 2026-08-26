package com.renaser.os.habits.infrastructure.adapter.in.rest.desbloqueo;

import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase.ItemDesbloqueo;
import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase.PlanDesbloqueo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HabitUnlockPlanResponse(boolean enabled, List<HabitUnlockItemResponse> items) {

    public static HabitUnlockPlanResponse from(PlanDesbloqueo plan) {
        return new HabitUnlockPlanResponse(plan.enabled(), plan.items().stream()
                .map(HabitUnlockItemResponse::from).toList());
    }

    public record HabitUnlockItemResponse(UUID habitId, int unlockDay, Instant chosenAt) {

        public static HabitUnlockItemResponse from(ItemDesbloqueo item) {
            return new HabitUnlockItemResponse(item.habitoId().value(), item.diaDesbloqueo(), item.elegidoEn());
        }
    }
}
