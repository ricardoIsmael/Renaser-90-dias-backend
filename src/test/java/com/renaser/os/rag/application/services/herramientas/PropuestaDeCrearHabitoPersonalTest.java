package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.plan.CrearHabitoPersonalPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code proponer_crear_habito_personal} (D-229): propone, nunca crea; sin categoria no propone y
 * pide preguntarla; no ofrece un habito que ya tiene.
 */
class PropuestaDeCrearHabitoPersonalTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 9, 29);

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final CrearHabitoPersonalPort crearPort = mock(CrearHabitoPersonalPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);
    private final PropuestaDeCrearHabitoPersonal herramienta =
            new PropuestaDeCrearHabitoPersonal(planPort, crearPort, proponerAccion);

    @BeforeEach
    void ultimaHora() {
        when(crearPort.ultimaHoraDeInicio()).thenReturn(LocalTime.of(23, 40));
    }

    static InvocacionHerramienta invocacion(String... claveValor) {
        Map<String, String> argumentos = new HashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            argumentos.put(claveValor[i], claveValor[i + 1]);
        }
        return new InvocacionHerramienta(PropuestaDeCrearHabitoPersonal.NOMBRE, argumentos);
    }

    private void conPlan(HabitoDelPlan... habitos) {
        when(planPort.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(HOY, List.of(habitos), List.of()));
    }

    private static HabitoDelPlan habito(String titulo, boolean pausadoHoy) {
        return new HabitoDelPlan(UUID.randomUUID(), titulo, false, pausadoHoy, null);
    }

    @Test
    @DisplayName("propone con la invocacion normalizada y el resumen de la tarjeta, sin crear nada")
    void propone() {
        conPlan(habito("Caminar", false));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, invocacion("nombre", " Leer 20 minutos ",
                "categoria", "mente", "hora", "7:30", "dias", "lunes, miércoles y viernes", "meta", "20 páginas"));

        String resumen = "Nuevo hábito: Leer 20 minutos · Mente · 07:30 · lunes, miércoles y viernes · meta: 20 páginas";
        verify(proponerAccion).proponer(APRENDIZ, invocacion("nombre", "Leer 20 minutos", "categoria", "MENTE",
                "hora", "07:30", "dias", "MONDAY,WEDNESDAY,FRIDAY", "meta", "20 páginas"), resumen);
        verify(crearPort, never()).crear(any(), any());
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .contains(resumen).contains("TODAVIA NO esta hecho").doesNotContain("No dio hora");
    }

    @Test
    @DisplayName("sin hora: propone a las 06:00 de Training y se lo dice al modelo")
    void sinHora() {
        conPlan();

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion("nombre", "Tomar sol", "categoria", "Cuerpo"));

        verify(proponerAccion).proponer(APRENDIZ, invocacion("nombre", "Tomar sol", "categoria", "CUERPO",
                "hora", "06:00"), "Nuevo hábito: Tomar sol · Cuerpo · 06:00 · todos los días");
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("No dio hora").contains("06:00");
    }

    @Test
    @DisplayName("sin categoria no propone ni lee el plan: le pide al modelo que la pregunte")
    void sinCategoria() {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, invocacion("nombre", "Leer"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo())
                .contains("NO lo propongas todavia").contains("preguntale").contains("Cuerpo, Mente, Emociones o Espiritu");
        verifyNoInteractions(proponerAccion, planPort);
    }

    @Test
    @DisplayName("una categoria que no es de habitos (Vida y negocio) no se propone")
    void categoriaInvalida() {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion("nombre", "Vender", "categoria", "Vida y negocio"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("'Vida y negocio' no es una");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("duplicado: si ya tiene un habito con ese nombre (aunque este pausado), no propone")
    void yaLoTiene() {
        conPlan(habito("Lectura nocturna", true));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion("nombre", "lectura  NOCTURNA", "categoria", "mente"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo())
                .contains("Ya tiene un habito llamado 'Lectura nocturna'").contains("pausado");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("duplicado: la misma propuesta pendiente no se repite (D-176)")
    void mismaPropuestaPendiente() {
        conPlan();
        when(proponerAccion.proponer(eq(APRENDIZ), any(), anyString())).thenReturn(new PropuestaCreada(
                UUID.randomUUID(), "Nuevo hábito: Leer · Mente · 06:00 · todos los días", Instant.now(), true));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                invocacion("nombre", "Leer", "categoria", "mente"));

        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("no se creo otra");
    }

    @Test
    @DisplayName("limites: nombre de mas de 120, hora pasada de 23:40 o dia que no existe no se proponen")
    void limites() {
        assertThat(herramienta.ejecutar(APRENDIZ, invocacion("nombre", "x".repeat(121), "categoria", "mente")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) herramienta.ejecutar(APRENDIZ,
                invocacion("nombre", "Leer", "categoria", "mente", "hora", "23:55"))).motivo()).contains("23:40");
        assertThat(herramienta.ejecutar(APRENDIZ, invocacion("nombre", "Leer", "categoria", "mente", "hora", "7am")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(herramienta.ejecutar(APRENDIZ, invocacion("nombre", "Leer", "categoria", "mente", "dias", "finde")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("suspendido o sin programa (staff): no propone")
    void sinPlanNoPropone() {
        when(planPort.planDe(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        assertThat(((ResultadoHerramienta.Fallo) herramienta.ejecutar(APRENDIZ,
                invocacion("nombre", "Leer", "categoria", "mente"))).motivo()).contains("suspendida");

        UserId staff = UserId.of(UUID.randomUUID());
        when(planPort.planDe(staff)).thenThrow(new NoSuchElementException("sin participacion"));
        assertThat(((ResultadoHerramienta.Fallo) herramienta.ejecutar(staff,
                invocacion("nombre", "Leer", "categoria", "mente"))).motivo()).contains("programa activo");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("la definicion: categoria no es obligatoria para el modelo, y la descripcion pide preguntarla")
    void definicion() {
        var definicion = herramienta.definicion();

        assertThat(definicion.nombre()).isEqualTo("proponer_crear_habito_personal");
        assertThat(definicion.descripcion()).contains("si la persona no la dijo, NO llames todavia");
        assertThat(definicion.parametros()).filteredOn(parametro -> parametro.obligatorio())
                .extracting(parametro -> parametro.nombre()).containsExactly("nombre");
    }
}
