package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.CrearHabitoPersonalPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.domain.model.habitopersonal.DimensionDelHabito;
import com.renaser.os.rag.domain.model.habitopersonal.HabitoPersonalPedido;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.rag.application.services.herramientas.PropuestaDeCrearHabitoPersonalTest.invocacion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La escritura de {@code proponer_crear_habito_personal} (D-229), solo al confirmar. */
class CrearHabitoPersonalConfirmableTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final CrearHabitoPersonalPort crearPort = mock(CrearHabitoPersonalPort.class);
    private final CrearHabitoPersonalConfirmable confirmable = new CrearHabitoPersonalConfirmable(planPort, crearPort);

    @BeforeEach
    void preparar() {
        when(crearPort.ultimaHoraDeInicio()).thenReturn(LocalTime.of(23, 40));
    }

    private void conPlan(HabitoDelPlan... habitos) {
        when(planPort.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(LocalDate.of(2026, 9, 29), List.of(habitos),
                List.of()));
    }

    @Test
    @DisplayName("crea con lo que se guardo en la propuesta")
    void crea() {
        conPlan();

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, invocacion("nombre", "Correr",
                "categoria", "CUERPO", "hora", "07:30", "dias", "MONDAY,FRIDAY", "meta", "5 km"));

        verify(crearPort).crear(APRENDIZ, new HabitoPersonalPedido("Correr", DimensionDelHabito.CUERPO,
                LocalTime.of(7, 30), true, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), "5 km"));
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .isEqualTo("Hábito 'Correr' creado en Cuerpo, a las 07:30, lunes y viernes. Ya lo ves en Training.");
        assertThat(confirmable.herramienta()).isEqualTo(PropuestaDeCrearHabitoPersonal.NOMBRE);
    }

    @Test
    @DisplayName("si entre proponer y confirmar ya lo creo, no crea otro")
    void yaLoCreo() {
        conPlan(new HabitoDelPlan(UUID.randomUUID(), "correr", false, false, null));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ,
                invocacion("nombre", "Correr", "categoria", "CUERPO", "hora", "07:30"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("no se creó otro");
        verify(crearPort, never()).crear(any(), any());
    }

    @Test
    @DisplayName("un rechazo de habits vuelve como Fallo legible, nunca como excepcion")
    void traduceRechazos() {
        conPlan();
        when(crearPort.crear(eq(APRENDIZ), any())).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ,
                invocacion("nombre", "Correr", "categoria", "CUERPO", "hora", "07:30"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).isEqualTo("La cuenta está suspendida: no se creó nada.");
    }

    @Test
    @DisplayName("argumentos guardados sin categoria: no crea nada")
    void sinCategoriaNoCrea() {
        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, invocacion("nombre", "Correr"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(crearPort, never()).crear(any(), any());
    }
}
