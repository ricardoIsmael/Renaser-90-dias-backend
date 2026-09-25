package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
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
 * {@code consultar_habitos_obligatorios} (D-165): cuales son obligatorios, que se puede con los
 * demas y cuales estan pausados, para que el acompanante nunca conteste un "no es posible" a secas
 * (E-245). Los obligatorios llegan dentro de {@code habitos}, marcados, como los entrega {@code habits}.
 */
class ConsultarHabitosObligatoriosHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 9, 25);
    private static final InvocacionHerramienta SIN_ARGUMENTOS =
            new InvocacionHerramienta(ConsultarHabitosObligatoriosHerramienta.NOMBRE, Map.of());

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final ConsultarHabitosObligatoriosHerramienta herramienta =
            new ConsultarHabitosObligatoriosHerramienta(planPort);

    private static HabitoDelPlan obligatorio(String titulo) {
        return new HabitoDelPlan(UUID.randomUUID(), titulo, true, false, null);
    }

    private static HabitoDelPlan comun(String titulo) {
        return new HabitoDelPlan(UUID.randomUUID(), titulo, false, false, null);
    }

    private static HabitoDelPlan pausado(String titulo, LocalDate hasta) {
        return new HabitoDelPlan(UUID.randomUUID(), titulo, false, true, hasta);
    }

    private void conHabitos(HabitoDelPlan... habitos) {
        when(planPort.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(HOY, List.of(habitos), List.of()));
    }

    private String contenido() {
        return ((ResultadoHerramienta.Exito) herramienta.ejecutar(APRENDIZ, SIN_ARGUMENTOS)).contenido();
    }

    @Test
    @DisplayName("nombra los obligatorios y dice que con ellos solo se mueve la hora, con cupo y nunca hoy")
    void obligatorios() {
        conHabitos(obligatorio("Clase diaria"), comun("Ducha fria"), obligatorio("Pastilla Renacer"));

        assertThat(contenido())
                .contains("Obligatorios del programa (no se apagan ningun dia ni se pausan): Clase diaria, "
                        + "Pastilla Renacer.")
                .contains("cambiarle la hora, si le quedan cambios esta semana")
                .contains("solo para un dia futuro").contains("El dia de hoy no se reacomoda")
                .doesNotContain("Ducha fria,");
    }

    @Test
    @DisplayName("E-245: cualquier habito que no sea obligatorio se pausa, se apaga un dia o cambia de hora")
    void losDemasSePausan() {
        conHabitos(obligatorio("Clase diaria"), comun("Escritura libre nocturna"));

        assertThat(contenido()).contains("Cualquier habito que no sea obligatorio se puede pausar")
                .contains("apagar un dia puntual (hoy o uno futuro)").doesNotContain("se suman a su plan");
    }

    @Test
    @DisplayName("dice cuales estan pausados hoy, con o sin fecha de fin")
    void pausados() {
        conHabitos(pausado("Dia sin celular", null), pausado("Ducha fria", LocalDate.of(2026, 9, 27)),
                comun("Leer"));

        assertThat(contenido()).contains("Pausados hoy: Dia sin celular (sin fecha de fin), Ducha fria (hasta el "
                + "domingo 27/09).");
    }

    @Test
    @DisplayName("sin obligatorios ni pausados lo dice, en vez de dejar listas vacias")
    void vacio() {
        conHabitos(comun("Leer"));

        assertThat(contenido()).contains("No tiene habitos obligatorios del programa.")
                .contains("Hoy no tiene habitos pausados.");
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
