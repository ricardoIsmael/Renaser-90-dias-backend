package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code proponer_pausar_habito} (fase 2, D-153): propone, nunca pausa, y no ofrece un boton que
 * {@code habits} va a rechazar.
 */
class PropuestaDePausarHabitoTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID LEER = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID DORMIR = UUID.fromString("66666666-6666-6666-6666-666666666666");
    /** De la base del programa: nunca tuvo fila en los desbloqueos y en Plan igual se pausa (E-245). */
    private static final UUID ESCRITURA = UUID.fromString("77777777-7777-7777-7777-777777777777");
    /** Miercoles: el "hoy" en la zona del aprendiz que devuelve {@code habits}. */
    private static final LocalDate HOY = LocalDate.of(2026, 9, 23);

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);
    private final PropuestaDePausarHabito herramienta = new PropuestaDePausarHabito(planPort, proponerAccion);

    private static InvocacionHerramienta invocacion(String habitoId, String accion, String hasta) {
        Map<String, String> argumentos = new HashMap<>();
        argumentos.put(PropuestaDePausarHabito.ARGUMENTO_HABITO_ID, habitoId);
        argumentos.put(PropuestaDePausarHabito.ARGUMENTO_ACCION, accion);
        if (hasta != null) {
            argumentos.put(PropuestaDePausarHabito.ARGUMENTO_HASTA, hasta);
        }
        return new InvocacionHerramienta(PropuestaDePausarHabito.NOMBRE, argumentos);
    }

    private void conPlan(HabitoDelPlan... habitos) {
        when(planPort.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(HOY, List.of(habitos), List.of()));
    }

    private static HabitoDelPlan leer(boolean pausadoHoy, LocalDate pausadoHasta) {
        return new HabitoDelPlan(LEER, "Leer", false, pausadoHoy, pausadoHasta);
    }

    private static HabitoDelPlan dormirObligatorio() {
        return new HabitoDelPlan(DORMIR, "Dormir", true, false, null);
    }

    private static HabitoDelPlan escrituraDeLaBase() {
        return new HabitoDelPlan(ESCRITURA, "Escritura libre nocturna", false, false, null);
    }

    @Test
    @DisplayName("propone la pausa con la invocacion normalizada y el resumen exacto, sin pausar")
    void proponePausa() {
        conPlan(leer(false, null), dormirObligatorio());

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion("  " + LEER + " ", " Pausar ", "2026-09-27"));

        String resumen = "Pausar 'Leer' desde hoy hasta el domingo 27/09 inclusive (si hoy lo tenias pendiente, "
                + "sale de tu dia)";
        verify(proponerAccion).proponer(APRENDIZ, invocacion(LEER.toString(), "pausar", "2026-09-27"), resumen);
        verify(planPort, never()).pausar(any(), any(), any());
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .contains(resumen).contains("TODAVIA NO esta hecho").contains("Confirmar");
    }

    @Test
    @DisplayName("sin fecha de fin, el resumen lo dice; al reactivar, la fecha que mande el modelo se descarta")
    void sinFechaYReactivar() {
        conPlan(leer(false, null));
        herramienta.ejecutar(APRENDIZ, invocacion(LEER.toString(), "pausar", null));
        verify(proponerAccion).proponer(APRENDIZ, invocacion(LEER.toString(), "pausar", null),
                "Pausar 'Leer' desde hoy sin fecha de fin (si hoy lo tenias pendiente, sale de tu dia)");

        conPlan(leer(true, LocalDate.of(2026, 9, 30)));
        herramienta.ejecutar(APRENDIZ, invocacion(LEER.toString(), "reactivar", "2026-09-30"));
        verify(proponerAccion).proponer(APRENDIZ, invocacion(LEER.toString(), "reactivar", null),
                "Reactivar 'Leer' en tu plan");
    }

    @Test
    @DisplayName("un habito obligatorio no genera propuesta")
    void obligatorioNoSePropone() {
        conPlan(leer(false, null), dormirObligatorio());

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, invocacion(DORMIR.toString(), "pausar", null));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("'Dormir' es obligatorio del programa")
                .contains("cambiarle la hora");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("E-245: un habito de la base, sin fila en los desbloqueos, se propone pausar como en Plan")
    void deLaBaseSePausa() {
        conPlan(leer(false, null), dormirObligatorio(), escrituraDeLaBase());

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion(ESCRITURA.toString(), "pausar", "2026-09-27"));

        verify(proponerAccion).proponer(APRENDIZ, invocacion(ESCRITURA.toString(), "pausar", "2026-09-27"),
                "Pausar 'Escritura libre nocturna' desde hoy hasta el domingo 27/09 inclusive (si hoy lo tenias "
                        + "pendiente, sale de tu dia)");
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
    }

    @Test
    @DisplayName("reactivar algo que no esta pausado lo dice y apunta a encender el dia, sin proponer")
    void reactivarLoQueNoEstaPausado() {
        conPlan(escrituraDeLaBase());

        String motivo = ((ResultadoHerramienta.Fallo) herramienta.ejecutar(APRENDIZ,
                invocacion(ESCRITURA.toString(), "reactivar", null))).motivo();

        assertThat(motivo).contains("no esta pausado").contains("volver a encender ese dia");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("una fecha de fin anterior a hoy (en su zona) no genera propuesta")
    void fechaPasadaNoSePropone() {
        conPlan(leer(false, null));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion(LEER.toString(), "pausar", "2026-09-22"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("ya paso");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("reactivar algo que no esta pausado, o pausar igual que ya esta, no genera propuesta")
    void cambiosQueNoCambianNada() {
        conPlan(leer(false, null));
        assertThat(herramienta.ejecutar(APRENDIZ, invocacion(LEER.toString(), "reactivar", null)))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);

        conPlan(leer(true, LocalDate.of(2026, 9, 27)));
        assertThat(herramienta.ejecutar(APRENDIZ, invocacion(LEER.toString(), "pausar", "2026-09-27")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);

        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("un habito_id que no es de sus habitos es Fallo y le lista al modelo los que si se pueden pausar")
    void fueraDelPlan() {
        conPlan(leer(false, null), dormirObligatorio());

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion(UUID.randomUUID().toString(), "pausar", null));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("no es de ninguno de sus habitos")
                .contains("habito_id=" + LEER).doesNotContain(DORMIR.toString());
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("argumentos invalidos se rechazan antes de mirar el plan")
    void argumentosInvalidos() {
        assertThat(herramienta.ejecutar(APRENDIZ, invocacion("el-de-leer", "pausar", null)))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(herramienta.ejecutar(APRENDIZ, invocacion(LEER.toString(), "borrar", null)))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(herramienta.ejecutar(APRENDIZ, invocacion(LEER.toString(), "pausar", "el domingo")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);

        verifyNoInteractions(planPort, proponerAccion);
    }

    @Test
    @DisplayName("una cuenta suspendida o una propuesta que no se puede guardar vuelven como Fallo legible")
    void fallasLegibles() {
        when(planPort.planDe(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        assertThat(((ResultadoHerramienta.Fallo) herramienta.ejecutar(APRENDIZ,
                invocacion(LEER.toString(), "pausar", null))).motivo()).contains("suspendida");

        doReturn(new PlanDelAprendiz(HOY, List.of(leer(false, null)), List.of())).when(planPort).planDe(APRENDIZ);
        when(proponerAccion.proponer(any(), any(), any())).thenThrow(new IllegalStateException("db caida"));
        assertThat(((ResultadoHerramienta.Fallo) herramienta.ejecutar(APRENDIZ,
                invocacion(LEER.toString(), "pausar", null))).motivo()).doesNotContain("db caida");
    }

    @Test
    @DisplayName("la descripcion le dice al modelo que propone y que nunca diga que ya quedo hecho")
    void descripcion() {
        assertThat(herramienta.definicion().nombre()).isEqualTo("proponer_pausar_habito");
        assertThat(herramienta.definicion().descripcion()).startsWith("Propone").contains("Confirmar")
                .contains("Nunca digas");
    }
}
