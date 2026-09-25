package com.renaser.os.rag.infrastructure.adapter.out.rocks;

import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.Motivo;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.ResultadoPlan;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.AccionDelDia;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.MotivoRechazo;
import com.renaser.os.rocks.api.PlanificacionDeRocasPort.ResultadoPlanificacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El adaptador arma cada accion como la app y traduce el resultado de {@code rocks.api}. */
class PlanificarRocasAdapterTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);

    private final PlanificacionDeRocasPort rocks = mock(PlanificacionDeRocasPort.class);
    private final PlanificarRocasAdapter adapter = new PlanificarRocasAdapter(rocks);

    @Test
    @DisplayName("posicion 1, 2, 3 dentro de cada eje en el orden recibido, impacto 5 y no delegable, como la app")
    void posicionaPorEje() {
        when(rocks.crearPlanDelDia(any(), any(), any())).thenReturn(new ResultadoPlanificacion.Creado(3));

        ResultadoPlan resultado = adapter.crearPlanDelDia(APRENDIZ, JUEVES, List.of(
                new AccionDelPlan("CUERPO", "Caminar", LocalTime.of(6, 0), LocalTime.of(6, 30)),
                new AccionDelPlan("TRABAJO", "Llamar", null, null),
                new AccionDelPlan("CUERPO", "Pesarme", null, null)));

        assertThat(resultado).isEqualTo(new ResultadoPlan.Creado(3));
        verify(rocks).crearPlanDelDia(APRENDIZ, JUEVES, List.of(
                new AccionDelDia("CUERPO", 1, "Caminar", 5, false, LocalTime.of(6, 0), LocalTime.of(6, 30)),
                new AccionDelDia("TRABAJO", 1, "Llamar", 5, false, null, null),
                new AccionDelDia("CUERPO", 2, "Pesarme", 5, false, null, null)));
    }

    @ParameterizedTest
    @EnumSource(MotivoRechazo.class)
    @DisplayName("cada motivo de rocks tiene su espejo en rag")
    void traduceCadaMotivo(MotivoRechazo motivo) {
        when(rocks.crearPlanDeLaSemana(any(), any())).thenReturn(new ResultadoPlanificacion.Rechazado(motivo));

        assertThat(adapter.crearPlanDeLaSemana(APRENDIZ, List.of()))
                .isEqualTo(new ResultadoPlan.Rechazado(Motivo.valueOf(motivo.name())));
    }

    @Test
    @DisplayName("los ejes validos son los que expone rocks")
    void ejes() {
        assertThat(adapter.ejesValidos()).isEqualTo(PlanificacionDeRocasPort.EJES);
    }
}
