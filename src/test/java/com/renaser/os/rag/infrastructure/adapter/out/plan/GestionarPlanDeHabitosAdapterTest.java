package com.renaser.os.rag.infrastructure.adapter.out.plan;

import com.renaser.os.habits.api.PlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoSemanal;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La traduccion de {@code habits.api} al puerto de {@code rag}: si pierde la marca de obligatorio o
 * la pausa, el acompanante vuelve a ofrecer lo que no se puede (E-245) sin que ningun test de las
 * herramientas lo note, porque esos mockean el puerto.
 */
class GestionarPlanDeHabitosAdapterTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 9, 25);
    private static final UUID CLASE = UUID.randomUUID();
    private static final UUID DUCHA = UUID.randomUUID();
    private static final UUID CORRER = UUID.randomUUID();

    private final PlanDeHabitosPort habits = mock(PlanDeHabitosPort.class);
    private final GestionarPlanDeHabitosAdapter adaptador = new GestionarPlanDeHabitosAdapter(habits);

    @Test
    @DisplayName("copia hoy, los habitos con su marca de obligatorio y su pausa, y los semanales")
    void traduceElPlan() {
        LocalDate hasta = LocalDate.of(2026, 9, 27);
        when(habits.planDe(APRENDIZ)).thenReturn(new PlanDeHabitosPort.PlanDeHabitos(HOY, List.of(
                new PlanDeHabitosPort.HabitoDelPlan(CLASE, "Clase diaria", true, false, null),
                new PlanDeHabitosPort.HabitoDelPlan(DUCHA, "Ducha fria", false, true, hasta)),
                List.of(new PlanDeHabitosPort.HabitoSemanal(CORRER, "Correr", null, List.of(HOY)))));

        PlanDelAprendiz plan = adaptador.planDe(APRENDIZ);

        assertThat(plan.hoy()).isEqualTo(HOY);
        assertThat(plan.habitos()).containsExactly(new HabitoDelPlan(CLASE, "Clase diaria", true, false, null),
                new HabitoDelPlan(DUCHA, "Ducha fria", false, true, hasta));
        assertThat(plan.obligatorios()).extracting(HabitoDelPlan::titulo).containsExactly("Clase diaria");
        assertThat(plan.semanales()).containsExactly(new HabitoSemanal(CORRER, "Correr", null, List.of(HOY)));
    }

    @Test
    @DisplayName("las escrituras pasan tal cual a habits")
    void escriturasDelegan() {
        LocalDate hasta = LocalDate.of(2026, 9, 27);

        adaptador.pausar(APRENDIZ, DUCHA, hasta);
        adaptador.reactivar(APRENDIZ, DUCHA);
        adaptador.elegirDiaSemanal(APRENDIZ, CORRER, HOY);

        verify(habits).pausar(APRENDIZ, DUCHA, hasta);
        verify(habits).reactivar(APRENDIZ, DUCHA);
        verify(habits).elegirDiaSemanal(APRENDIZ, CORRER, HOY);
    }
}
