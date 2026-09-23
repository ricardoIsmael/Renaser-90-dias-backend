package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** La escritura que corre cuando la persona confirma una propuesta de {@code proponer_pausar_habito}. */
class PausarHabitoConfirmableTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID HABITO = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final PausarHabitoConfirmable confirmable = new PausarHabitoConfirmable(planPort);

    private static InvocacionHerramienta invocacion(String accion, String hasta) {
        Map<String, String> argumentos = new HashMap<>();
        argumentos.put(PropuestaDePausarHabito.ARGUMENTO_HABITO_ID, HABITO.toString());
        argumentos.put(PropuestaDePausarHabito.ARGUMENTO_ACCION, accion);
        if (hasta != null) {
            argumentos.put(PropuestaDePausarHabito.ARGUMENTO_HASTA, hasta);
        }
        return new InvocacionHerramienta(PropuestaDePausarHabito.NOMBRE, argumentos);
    }

    @Test
    @DisplayName("se registra con el nombre de la herramienta cuya propuesta ejecuta")
    void nombre() {
        assertThat(confirmable.herramienta()).isEqualTo(PropuestaDePausarHabito.NOMBRE);
    }

    @Test
    @DisplayName("al confirmar una pausa, delega en habits con la fecha de fin guardada")
    void pausa() {
        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, invocacion("pausar", "2026-09-27"));

        verify(planPort).pausar(APRENDIZ, HABITO, LocalDate.of(2026, 9, 27));
        assertThat(resultado).isEqualTo(ResultadoHerramienta.exito("Habito pausado hasta el domingo 27/09 inclusive."));
    }

    @Test
    @DisplayName("sin fecha guardada, pausa sin fecha de fin; reactivar delega en reactivar")
    void pausaIndefinidaYReactivar() {
        confirmable.aplicar(APRENDIZ, invocacion("pausar", null));
        verify(planPort).pausar(APRENDIZ, HABITO, null);

        assertThat(confirmable.aplicar(APRENDIZ, invocacion("reactivar", null)))
                .isEqualTo(ResultadoHerramienta.exito("Habito reactivado en su plan."));
        verify(planPort).reactivar(APRENDIZ, HABITO);
    }

    @Test
    @DisplayName("si habits lo rechaza al confirmar, vuelve un Fallo legible, no la excepcion")
    void rechazoDelNegocio() {
        doThrow(new IllegalStateException("Este habito es obligatorio y no se puede pausar"))
                .when(planPort).pausar(APRENDIZ, HABITO, null);
        ResultadoHerramienta obligatorio = confirmable.aplicar(APRENDIZ, invocacion("pausar", null));
        assertThat(((ResultadoHerramienta.Fallo) obligatorio).motivo()).isEqualTo(
                "Ese habito es obligatorio: no se puede pausar.");

        doThrow(new NoSuchElementException("Este habito no esta en tu plan: x"))
                .when(planPort).reactivar(APRENDIZ, HABITO);
        ResultadoHerramienta fueraDelPlan = confirmable.aplicar(APRENDIZ, invocacion("reactivar", null));
        assertThat(((ResultadoHerramienta.Fallo) fueraDelPlan).motivo()).contains("ya no esta en su plan")
                .doesNotContain("NoSuchElementException");
    }

    @Test
    @DisplayName("una invocacion guardada sin accion valida no llega a habits")
    void invocacionInvalida() {
        assertThat(confirmable.aplicar(APRENDIZ, invocacion("borrar", null)))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(planPort);
    }
}
