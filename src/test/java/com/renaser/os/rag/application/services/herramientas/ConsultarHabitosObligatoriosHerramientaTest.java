package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoObligatorio;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code consultar_habitos_obligatorios} (D-165): cuales son obligatorios, cuales se pausan y que si
 * se puede, para que el acompanante nunca conteste un "no es posible" a secas (E-245).
 */
class ConsultarHabitosObligatoriosHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 9, 25);
    private static final InvocacionHerramienta SIN_ARGUMENTOS =
            new InvocacionHerramienta(ConsultarHabitosObligatoriosHerramienta.NOMBRE, Map.of());

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final ConsultarHabitosObligatoriosHerramienta herramienta =
            new ConsultarHabitosObligatoriosHerramienta(planPort);

    private static HabitoObligatorio obligatorio(String titulo) {
        return new HabitoObligatorio(UUID.randomUUID(), titulo);
    }

    private static HabitoDelPlan delPlan(String titulo, boolean obligatorio, boolean pausadoHoy) {
        return new HabitoDelPlan(UUID.randomUUID(), titulo, obligatorio, pausadoHoy, null);
    }

    private String contenido() {
        return ((ResultadoHerramienta.Exito) herramienta.ejecutar(APRENDIZ, SIN_ARGUMENTOS)).contenido();
    }

    @Test
    @DisplayName("nombra los obligatorios, dice que con ellos solo se mueve la hora y lista los que se pausan")
    void obligatoriosYPausables() {
        when(planPort.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(HOY,
                List.of(delPlan("Ducha fria", false, false), delPlan("Dia sin celular", false, true)), List.of(),
                List.of(obligatorio("Clase diaria"), obligatorio("Pastilla Renacer"))));

        assertThat(contenido())
                .contains("Obligatorios del programa (no se apagan ningun dia ni se pausan): Clase diaria, "
                        + "Pastilla Renacer.")
                .contains("cambiarle la hora: desde manana o para un dia futuro")
                .contains("solo los que se suman a su plan: Ducha fria, Dia sin celular (hoy esta pausado).")
                .contains("apagarlo un dia puntual");
    }

    @Test
    @DisplayName("un obligatorio que aparece entre los desbloqueos no se ofrece como pausable")
    void obligatorioNuncaEsPausable() {
        when(planPort.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(HOY,
                List.of(delPlan("Dormir", true, false)), List.of(), List.of(obligatorio("Dormir"))));

        assertThat(contenido()).contains("No tiene habitos que se puedan pausar").doesNotContain("plan: Dormir");
    }

    @Test
    @DisplayName("sin obligatorios lo dice, en vez de dejar la lista vacia")
    void sinObligatorios() {
        when(planPort.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(HOY, List.of(), List.of(), List.of()));

        assertThat(contenido()).contains("No tiene habitos obligatorios del programa.");
    }

    @Test
    @DisplayName("una cuenta suspendida vuelve como Fallo legible, sin lanzar")
    void suspendida() {
        when(planPort.planDe(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThat(((ResultadoHerramienta.Fallo) herramienta.ejecutar(APRENDIZ, SIN_ARGUMENTOS)).motivo())
                .contains("suspendida");
    }

    @Test
    @DisplayName("la descripcion le dice al modelo cuando usarla")
    void descripcion() {
        assertThat(herramienta.definicion().nombre()).isEqualTo("consultar_habitos_obligatorios");
        assertThat(herramienta.definicion().descripcion()).contains("obligatorios del programa")
                .contains("apagar, pausar, quitar o saltarse");
        assertThat(herramienta.definicion().parametros()).isEmpty();
    }
}
