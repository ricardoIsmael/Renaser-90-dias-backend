package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.Motivo;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.ObjetivoSemanal;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.ResultadoPlan;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las escrituras que corren cuando la persona toca "Confirmar" (D-153): delegan en {@code rocks}
 * via {@link PlanificarRocasPort} y traducen el rechazo a un {@code Fallo} legible.
 */
class CrearPlanDeRocasConfirmableTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);
    private static final String PLAN_DEL_DIA = "{\"fecha\":\"2026-09-24\",\"acciones\":["
            + "{\"eje\":\"CUERPO\",\"titulo\":\"Caminar\",\"inicio\":\"06:00\",\"fin\":\"06:30\"},"
            + "{\"eje\":\"TRABAJO\",\"titulo\":\"Llamar\"}]}";
    private static final String PLAN_DE_LA_SEMANA = "{\"objetivos\":[{\"eje\":\"CUERPO\",\"titulo\":\"Bajar 1 kg\"},"
            + "{\"eje\":\"TRABAJO\",\"titulo\":\"Vender\",\"obstaculo\":\"Poco tiempo\"}]}";

    private final PlanificarRocasPort planificar = mock(PlanificarRocasPort.class);
    private final CrearPlanDelDiaConfirmable delDia = new CrearPlanDelDiaConfirmable(planificar);
    private final CrearPlanDeLaSemanaConfirmable deLaSemana = new CrearPlanDeLaSemanaConfirmable(planificar);

    @BeforeEach
    void ejes() {
        when(planificar.ejesValidos()).thenReturn(List.of("CUERPO", "TRABAJO", "RELACIONES"));
    }

    private static InvocacionHerramienta invocacionDelDia(String plan) {
        return new InvocacionHerramienta(ProponerPlanDelDiaHerramienta.NOMBRE,
                Map.of(ProponerPlanDelDiaHerramienta.ARGUMENTO_PLAN, plan));
    }

    private static InvocacionHerramienta invocacionDeLaSemana(String plan) {
        return new InvocacionHerramienta(ProponerPlanDeLaSemanaHerramienta.NOMBRE,
                Map.of(ProponerPlanDeLaSemanaHerramienta.ARGUMENTO_PLAN, plan));
    }

    @Test
    @DisplayName("cada una se registra con el nombre de la herramienta cuya propuesta ejecuta")
    void nombres() {
        assertThat(delDia.herramienta()).isEqualTo("proponer_plan_del_dia");
        assertThat(deLaSemana.herramienta()).isEqualTo("proponer_plan_de_la_semana");
    }

    @Test
    @DisplayName("el plan del dia se crea con la fecha y las acciones guardadas, en su orden")
    void creaElPlanDelDia() {
        List<AccionDelPlan> acciones = List.of(
                new AccionDelPlan("CUERPO", "Caminar", LocalTime.of(6, 0), LocalTime.of(6, 30)),
                new AccionDelPlan("TRABAJO", "Llamar", null, null));
        when(planificar.crearPlanDelDia(APRENDIZ, JUEVES, acciones)).thenReturn(new ResultadoPlan.Creado(2));

        ResultadoHerramienta resultado = delDia.aplicar(APRENDIZ, invocacionDelDia(PLAN_DEL_DIA));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.exito("Plan del jueves 2026-09-24 guardado con 2 accion(es)."));
        verify(planificar).crearPlanDelDia(APRENDIZ, JUEVES, acciones);
    }

    @Test
    @DisplayName("si entre proponer y confirmar la fecha dejo de ser planificable, Fallo legible")
    void rechazoDelDia() {
        when(planificar.crearPlanDelDia(any(), any(), any()))
                .thenReturn(new ResultadoPlan.Rechazado(Motivo.FECHA_NO_PLANIFICABLE));

        assertThat(delDia.aplicar(APRENDIZ, invocacionDelDia(PLAN_DEL_DIA))).isEqualTo(ResultadoHerramienta.fallo(
                "Ese dia ya no se puede planificar. Elige uno que quede de la semana."));
    }

    @Test
    @DisplayName("un error que no es rechazo tampoco sube: Fallo legible")
    void errorInesperado() {
        when(planificar.crearPlanDelDia(any(), any(), any())).thenThrow(new IllegalStateException("base caida"));

        assertThat(delDia.aplicar(APRENDIZ, invocacionDelDia(PLAN_DEL_DIA)))
                .isEqualTo(ResultadoHerramienta.fallo("No pude guardar el plan en este momento."));
    }

    @Test
    @DisplayName("una propuesta sin fecha no se ejecuta: no se adivina 'manana' al confirmar")
    void sinFechaNoSeEjecuta() {
        ResultadoHerramienta resultado = delDia.aplicar(APRENDIZ,
                invocacionDelDia("{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Caminar\"}]}"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(planificar, never()).crearPlanDelDia(any(), any(), any());
    }

    @Test
    @DisplayName("la semana se crea con los objetivos guardados, y avisa si algun eje ya tenia el suyo")
    void creaLaSemana() {
        List<ObjetivoSemanal> objetivos = List.of(new ObjetivoSemanal("CUERPO", "Bajar 1 kg", null, null),
                new ObjetivoSemanal("TRABAJO", "Vender", "Poco tiempo", null));
        when(planificar.crearPlanDeLaSemana(APRENDIZ, objetivos)).thenReturn(new ResultadoPlan.Creado(1));

        ResultadoHerramienta resultado = deLaSemana.aplicar(APRENDIZ, invocacionDeLaSemana(PLAN_DE_LA_SEMANA));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.exito("Objetivos de la semana guardados: 1. Los otros "
                + "ejes ya tenian objetivo esa semana y se dejaron como estaban."));
    }

    @Test
    @DisplayName("la semana con todos los ejes pedidos ya armados vuelve un Fallo legible")
    void semanaYaArmada() {
        when(planificar.crearPlanDeLaSemana(any(), any())).thenReturn(new ResultadoPlan.Rechazado(Motivo.YA_PLANIFICADO));

        assertThat(deLaSemana.aplicar(APRENDIZ, invocacionDeLaSemana(PLAN_DE_LA_SEMANA))).isEqualTo(ResultadoHerramienta.fallo(
                "Esa semana ya tiene objetivo en todos los ejes que pidio. Un objetivo ya guardado se cambia "
                        + "editandolo desde la app."));
    }
}
