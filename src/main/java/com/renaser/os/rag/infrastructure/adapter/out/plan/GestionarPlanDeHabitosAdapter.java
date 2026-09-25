package com.renaser.os.rag.infrastructure.adapter.out.plan;

import com.renaser.os.habits.api.PlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Implementa {@link GestionarPlanDeHabitosPort} delegando en el contrato publico de {@code habits}
 * (D-41). Es una traduccion y nada mas: que es "hoy" para el aprendiz, que habito es obligatorio,
 * si esta pausado y que dias se pueden elegir lo resuelve {@code habits}. Mismo patron que
 * {@code ConsultarHorariosAdapter}.
 */
@Component
class GestionarPlanDeHabitosAdapter implements GestionarPlanDeHabitosPort {

    private final PlanDeHabitosPort planDeHabitos;

    GestionarPlanDeHabitosAdapter(PlanDeHabitosPort planDeHabitos) {
        this.planDeHabitos = planDeHabitos;
    }

    @Override
    public PlanDelAprendiz planDe(UserId participanteId) {
        PlanDeHabitosPort.PlanDeHabitos plan = planDeHabitos.planDe(participanteId);
        return new PlanDelAprendiz(plan.hoy(),
                plan.habitos().stream().map(GestionarPlanDeHabitosAdapter::aHabitoDelPlan).toList(),
                plan.semanales().stream().map(GestionarPlanDeHabitosAdapter::aHabitoSemanal).toList());
    }

    @Override
    public void pausar(UserId actorId, UUID habitoId, LocalDate hastaInclusive) {
        planDeHabitos.pausar(actorId, habitoId, hastaInclusive);
    }

    @Override
    public void reactivar(UserId actorId, UUID habitoId) {
        planDeHabitos.reactivar(actorId, habitoId);
    }

    @Override
    public void elegirDiaSemanal(UserId actorId, UUID habitoId, LocalDate fecha) {
        planDeHabitos.elegirDiaSemanal(actorId, habitoId, fecha);
    }

    private static HabitoDelPlan aHabitoDelPlan(PlanDeHabitosPort.HabitoDelPlan habito) {
        return new HabitoDelPlan(habito.habitoId(), habito.titulo(), habito.obligatorio(), habito.pausadoHoy(),
                habito.pausadoHasta());
    }

    private static HabitoSemanal aHabitoSemanal(PlanDeHabitosPort.HabitoSemanal habito) {
        return new HabitoSemanal(habito.habitoId(), habito.titulo(), habito.diaElegido(), habito.diasElegibles());
    }
}
