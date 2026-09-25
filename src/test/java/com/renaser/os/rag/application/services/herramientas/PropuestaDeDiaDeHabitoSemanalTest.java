package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoSemanal;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code proponer_dia_de_habito_semanal} (fase 2, D-153): propone, nunca elige, y solo ofrece los
 * dias que {@code habits} dice que todavia se pueden elegir en la zona del aprendiz.
 */
class PropuestaDeDiaDeHabitoSemanalTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID CORRER = UUID.fromString("77777777-7777-7777-7777-777777777777");
    /** Miercoles 23/09 en la zona del aprendiz: quedan miercoles a domingo. */
    private static final LocalDate HOY = LocalDate.of(2026, 9, 23);
    private static final LocalDate MARTES = LocalDate.of(2026, 9, 22);
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);
    private static final List<LocalDate> ELEGIBLES = HOY.datesUntil(LocalDate.of(2026, 9, 28)).toList();

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);
    private final PropuestaDeDiaDeHabitoSemanal herramienta =
            new PropuestaDeDiaDeHabitoSemanal(planPort, proponerAccion);

    private static InvocacionHerramienta invocacion(String habitoId, String fecha) {
        return new InvocacionHerramienta(PropuestaDeDiaDeHabitoSemanal.NOMBRE,
                Map.of(PropuestaDeDiaDeHabitoSemanal.ARGUMENTO_HABITO_ID, habitoId,
                        PropuestaDeDiaDeHabitoSemanal.ARGUMENTO_FECHA, fecha));
    }

    private void conSemanal(LocalDate diaElegido, List<LocalDate> elegibles) {
        when(planPort.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(HOY, List.of(),
                List.of(new HabitoSemanal(CORRER, "Correr", diaElegido, elegibles))));
    }

    @Test
    @DisplayName("propone el dia con la invocacion normalizada y el resumen exacto, sin elegir")
    void proponeElDia() {
        conSemanal(MARTES, ELEGIBLES);

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, invocacion(" " + CORRER + " ", " 2026-09-24 "));

        String resumen = "Anotar el jueves 24/09 como tu dia de 'Correr' esta semana (en lugar del martes 22/09)";
        verify(proponerAccion).proponer(APRENDIZ, invocacion(CORRER.toString(), "2026-09-24"), resumen);
        verify(planPort, never()).elegirDiaSemanal(any(), any(), any());
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains(resumen)
                .contains("TODAVIA NO esta hecho").contains("Confirmar")
                .contains(PropuestaDeDiaDeHabitoSemanal.ADVERTENCIA_D_H3);
    }

    @Test
    @DisplayName("un dia que ya paso en su zona no genera propuesta y le dice al modelo cuales si")
    void diaPasadoNoSePropone() {
        conSemanal(null, ELEGIBLES);

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, invocacion(CORRER.toString(), "2026-09-22"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("no se puede elegir")
                .contains("jueves 24/09 = 2026-09-24");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("un dia de la semana que viene tampoco se propone")
    void otraSemanaNoSePropone() {
        conSemanal(null, ELEGIBLES);

        assertThat(herramienta.ejecutar(APRENDIZ, invocacion(CORRER.toString(), "2026-09-29")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("en el Dia 0 (sin dias elegibles) no hay propuesta")
    void diaCeroNoSePropone() {
        conSemanal(null, List.of());

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, invocacion(CORRER.toString(), "2026-09-24"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("Dia 1");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("elegir el mismo dia que ya tiene no genera propuesta")
    void mismoDiaNoSePropone() {
        conSemanal(JUEVES, ELEGIBLES);

        assertThat(herramienta.ejecutar(APRENDIZ, invocacion(CORRER.toString(), "2026-09-24")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("un habito que no es de eleccion semanal es Fallo y lista los que si lo son")
    void noEsSemanal() {
        conSemanal(null, ELEGIBLES);

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion(UUID.randomUUID().toString(), "2026-09-24"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("habito_id=" + CORRER);
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("una fecha que no es yyyy-MM-dd se rechaza antes de mirar el plan")
    void fechaInvalida() {
        assertThat(herramienta.ejecutar(APRENDIZ, invocacion(CORRER.toString(), "el jueves")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(planPort, proponerAccion);
    }

    @Test
    @DisplayName("la descripcion le dice al modelo que propone y que nunca diga que ya quedo hecho")
    void descripcion() {
        assertThat(herramienta.definicion().nombre()).isEqualTo("proponer_dia_de_habito_semanal");
        assertThat(herramienta.definicion().descripcion()).startsWith("Propone").contains("Confirmar")
                .contains("Nunca digas");
    }
}
