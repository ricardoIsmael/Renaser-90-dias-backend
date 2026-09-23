package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** La escritura que corre cuando la persona confirma una propuesta de {@code proponer_dia_de_habito_semanal}. */
class DiaDeHabitoSemanalConfirmableTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID HABITO = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final DiaDeHabitoSemanalConfirmable confirmable = new DiaDeHabitoSemanalConfirmable(planPort);

    private static InvocacionHerramienta invocacion(String fecha) {
        return new InvocacionHerramienta(PropuestaDeDiaDeHabitoSemanal.NOMBRE,
                Map.of(PropuestaDeDiaDeHabitoSemanal.ARGUMENTO_HABITO_ID, HABITO.toString(),
                        PropuestaDeDiaDeHabitoSemanal.ARGUMENTO_FECHA, fecha));
    }

    @Test
    @DisplayName("se registra con el nombre de la herramienta cuya propuesta ejecuta")
    void nombre() {
        assertThat(confirmable.herramienta()).isEqualTo(PropuestaDeDiaDeHabitoSemanal.NOMBRE);
    }

    @Test
    @DisplayName("al confirmar, delega en habits con el dia guardado")
    void eligeElDia() {
        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, invocacion("2026-09-24"));

        verify(planPort).elegirDiaSemanal(APRENDIZ, HABITO, JUEVES);
        assertThat(resultado).isEqualTo(ResultadoHerramienta.exito("Dia anotado: jueves 24/09."));
    }

    @Test
    @DisplayName("si al confirmar el dia ya paso, habits lo rechaza y vuelve un Fallo legible")
    void diaYaPasado() {
        doThrow(new IllegalArgumentException("Elige un dia de esta semana que no haya pasado todavia"))
                .when(planPort).elegirDiaSemanal(APRENDIZ, HABITO, JUEVES);

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, invocacion("2026-09-24"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo())
                .isEqualTo("Ese dia ya no se puede elegir: solo dias de esta semana que no hayan pasado.");
    }

    @Test
    @DisplayName("el Dia 0 o una cuenta suspendida vuelven como Fallo legible")
    void noPermitido() {
        doThrow(new NotAuthorizedException("El Dia 0 es una vista previa"))
                .when(planPort).elegirDiaSemanal(APRENDIZ, HABITO, JUEVES);

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, invocacion("2026-09-24"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("no se cambio nada");
    }

    @Test
    @DisplayName("una invocacion guardada con una fecha invalida no llega a habits")
    void invocacionInvalida() {
        assertThat(confirmable.aplicar(APRENDIZ, invocacion("jueves")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(planPort);
    }
}
