package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.application.ports.out.rocas.AgregarAccionAlPlanPort;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.ReglasDelCierre;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.PlanDeManana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDelDia;
import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-247 (E-496, produccion 2026-10-02): SER le propuso un plan a una persona sin su Mapa de Renacimiento y,
 * al confirmar, {@code rocks} lo rechazo con {@code ROCKS_LOCKED}. Ahora ninguna herramienta que escribe
 * rocas deja una tarjeta que va a fallar: no crea la propuesta y le dice al modelo por que y que ofrecer.
 *
 * <p>Contra el codigo viejo estas pruebas fallan: la propuesta se creaba igual ({@code proponer} se llamaba).
 */
class PropuestasConRocasBloqueadasTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    /** Viernes 2026-10-02 en Lima; manana sabado. */
    private static final LocalDate MANANA = LocalDate.of(2026, 10, 3);

    private final ConsultarRocasDelAprendizPort rocas = mock(ConsultarRocasDelAprendizPort.class);
    private final PlanificarRocasPort planificar = mock(PlanificarRocasPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final GestionarPlanDeHabitosPort planDeHabitos = mock(GestionarPlanDeHabitosPort.class);
    private final EditarObjetivoSemanalPort editar = mock(EditarObjetivoSemanalPort.class);
    private final CerrarSemanaDeRocasPort cierre = mock(CerrarSemanaDeRocasPort.class);

    /** El Mapa (D-233) no es lo que se prueba: si no se lee, la propuesta sale sin el. */
    private final MapaParaProponer sinMapaQueLeer = new MapaParaProponer(
            id -> { throw new IllegalStateException("no se lee en esta prueba"); }, id -> java.util.Optional.empty());

    @BeforeEach
    void reglas() {
        when(planificar.ejesValidos()).thenReturn(List.of("CUERPO", "TRABAJO", "RELACIONES"));
        when(rocas.deManana(APRENDIZ)).thenReturn(new RocasDelDia(MANANA, List.of(),
                new PlanDeManana(false, 0, true, LocalTime.of(18, 0), false)));
        when(planDeHabitos.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(MANANA.minusDays(1), List.of(), List.of()));
        when(cierre.reglas()).thenReturn(new ReglasDelCierre(List.of("CUERPO", "TRABAJO", "RELACIONES"), 1, 10));
        when(proponer.proponer(any(), any(), anyString())).thenReturn(new PropuestaCreada(UUID.randomUUID(), "r",
                Instant.parse("2026-10-03T05:00:00Z")));
    }

    private ResultadoHerramienta agregarAccion(CompuertaParaProponer compuerta) {
        return new ProponerAgregarAccionHerramienta(rocas, planificar, proponer, planDeHabitos, sinMapaQueLeer,
                compuerta).ejecutar(APRENDIZ, new InvocacionHerramienta(ProponerAgregarAccionHerramienta.NOMBRE,
                Map.of("eje", "CUERPO", "titulo", "Correr 5 km")));
    }

    private ResultadoHerramienta planDelDia(CompuertaParaProponer compuerta) {
        return new ProponerPlanDelDiaHerramienta(rocas, planificar, proponer, sinMapaQueLeer, compuerta)
                .ejecutar(APRENDIZ, new InvocacionHerramienta(ProponerPlanDelDiaHerramienta.NOMBRE,
                        Map.of("plan", "{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Correr 5 km\"},"
                                + "{\"eje\":\"TRABAJO\",\"titulo\":\"Llamar a 3 clientes\"}]}")));
    }

    private ResultadoHerramienta planDeLaSemana(CompuertaParaProponer compuerta) {
        return new ProponerPlanDeLaSemanaHerramienta(planificar, proponer, sinMapaQueLeer, compuerta)
                .ejecutar(APRENDIZ, new InvocacionHerramienta(ProponerPlanDeLaSemanaHerramienta.NOMBRE,
                        Map.of("plan", "{\"objetivos\":[{\"eje\":\"CUERPO\",\"titulo\":\"Bajar 1 kg\"}]}")));
    }

    private ResultadoHerramienta editarObjetivo(CompuertaParaProponer compuerta) {
        return new ProponerEditarObjetivoSemanalHerramienta(rocas, planificar, editar, proponer, sinMapaQueLeer,
                compuerta).ejecutar(APRENDIZ, new InvocacionHerramienta(ProponerEditarObjetivoSemanalHerramienta.NOMBRE,
                Map.of("eje", "CUERPO", "titulo", "Bajar 1 kg")));
    }

    private ResultadoHerramienta cerrarSemana(CompuertaParaProponer compuerta) {
        return new ProponerCerrarSemanaHerramienta(rocas, cierre, proponer, compuerta).ejecutar(APRENDIZ,
                new InvocacionHerramienta(ProponerCerrarSemanaHerramienta.NOMBRE, Map.of("cierre",
                        "{\"ejes\":[{\"eje\":\"CUERPO\",\"autoevaluacion\":5,\"bloqueoPrincipal\":\"x\","
                                + "\"correccion\":\"y\"}]}")));
    }

    @Test
    @DisplayName("sin Mapa ni Rocas Maestras: ninguna de las cinco herramientas crea tarjeta y todas dicen ir al Mapa")
    void sinMapaNingunaPropone() {
        CompuertaParaProponer bloqueada = CompuertasDePrueba.sinRocasMaestras(false);

        for (ResultadoHerramienta resultado : List.of(agregarAccion(bloqueada), planDelDia(bloqueada),
                planDeLaSemana(bloqueada), editarObjetivo(bloqueada), cerrarSemana(bloqueada))) {
            assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo(CompuertaParaProponer.SIN_MAPA));
        }
        verify(proponer, never()).proponer(any(), any(), anyString());
        assertThat(CompuertaParaProponer.SIN_MAPA)
                .startsWith("No se creó ninguna propuesta: todavía no completó su Mapa de Renacimiento")
                .contains("en la pestaña Plan, botón \"Ir al Mapa de Renacimiento\"")
                .contains("No le propongas otro plan ni otra acción de objetivos hasta que lo complete");
    }

    @Test
    @DisplayName("Mapa respondido pero sin Rocas Maestras (activacion fallida): bloqueada y lo dice distinto")
    void mapaSinActivar() {
        CompuertaParaProponer bloqueada = CompuertasDePrueba.sinRocasMaestras(true);

        assertThat(agregarAccion(bloqueada)).isEqualTo(ResultadoHerramienta.fallo(CompuertaParaProponer.MAPA_SIN_ACTIVAR));
        assertThat(planDeLaSemana(bloqueada)).isEqualTo(ResultadoHerramienta.fallo(
                CompuertaParaProponer.MAPA_SIN_ACTIVAR));
        verify(proponer, never()).proponer(any(), any(), anyString());
        assertThat(CompuertaParaProponer.MAPA_SIN_ACTIVAR).contains("su Mapa de Renacimiento está respondido, pero "
                + "sus tres objetivos de 90 días (Rocas Maestras) no quedaron creados");
    }

    @Test
    @DisplayName("sin objetivo semanal del eje: no se propone la accion del dia y se ofrece primero el de la semana")
    void sinObjetivoSemanal() {
        CompuertaParaProponer sinCuerpo = CompuertasDePrueba.sinObjetivoSemanal(List.of("CUERPO", "TRABAJO"));

        assertThat(agregarAccion(sinCuerpo)).isEqualTo(ResultadoHerramienta.fallo("No se creó ninguna propuesta: "
                + "Cuerpo no tiene objetivo semanal en la semana del sabado 2026-10-03, y las acciones del día "
                + "cuelgan de ese objetivo. Ofrécele primero definir el objetivo de la semana de ese eje "
                + "(proponer_plan_de_la_semana) y espera a que diga que sí; no propongas por tu cuenta otro plan."));
        assertThat(planDelDia(sinCuerpo)).isEqualTo(ResultadoHerramienta.fallo(
                CompuertaParaProponer.sinObjetivoSemanal(List.of("CUERPO", "TRABAJO"), MANANA)));
        verify(proponer, never()).proponer(any(), any(), anyString());

        // El plan de la semana es justo lo que destraba: no se frena por no tener objetivo semanal.
        assertThat(planDeLaSemana(sinCuerpo)).isInstanceOf(ResultadoHerramienta.Exito.class);
    }

    @Test
    @DisplayName("solo cuenta el eje pedido: sin objetivo de RELACIONES, una accion de CUERPO se propone")
    void otroEjeSinObjetivo() {
        assertThat(agregarAccion(CompuertasDePrueba.sinObjetivoSemanal(List.of("RELACIONES"))))
                .isInstanceOf(ResultadoHerramienta.Exito.class);
    }

    @Test
    @DisplayName("desbloqueada: las herramientas proponen como siempre")
    void desbloqueadaPropone() {
        assertThat(agregarAccion(CompuertasDePrueba.LIBRE)).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(planDelDia(CompuertasDePrueba.LIBRE)).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(planDeLaSemana(CompuertasDePrueba.LIBRE)).isInstanceOf(ResultadoHerramienta.Exito.class);
    }

    @Test
    @DisplayName("si rocks no responde la compuerta, no se bloquea: rocks sigue rechazando al confirmar")
    void compuertaCaidaNoBloquea() {
        CompuertaParaProponer caida = new CompuertaParaProponer(
                new com.renaser.os.rag.application.ports.out.rocas.ConsultarCompuertaDeRocasPort() {
                    @Override
                    public boolean rocasMaestrasCompletas(UserId aprendizId) {
                        throw new IllegalStateException("rocks caido");
                    }

                    @Override
                    public List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha) {
                        throw new IllegalStateException("rocks caido");
                    }
                }, id -> { throw new IllegalStateException("tampoco el Mapa"); });

        assertThat(agregarAccion(caida)).isInstanceOf(ResultadoHerramienta.Exito.class);
    }

    @Test
    @DisplayName("una tarjeta vieja confirmada sin Rocas Maestras: la persona lee en español que vaya a su Mapa")
    void confirmarTarjetaVieja() {
        AgregarAccionAlPlanPort agregar = mock(AgregarAccionAlPlanPort.class);
        when(agregar.agregar(any(), any(), any())).thenReturn(
                new AgregarAccionAlPlanPort.Resultado.Rechazado(AgregarAccionAlPlanPort.Motivo.ROCAS_BLOQUEADAS));
        when(planificar.crearPlanDelDia(any(), any(), any())).thenReturn(
                new PlanificarRocasPort.ResultadoPlan.Rechazado(PlanificarRocasPort.Motivo.ROCAS_BLOQUEADAS));
        when(planificar.crearPlanDeLaSemana(any(), any())).thenReturn(
                new PlanificarRocasPort.ResultadoPlan.Rechazado(PlanificarRocasPort.Motivo.ROCAS_BLOQUEADAS));
        String esperado = "No se pudo: primero completa tu Mapa de Renacimiento (en Plan, \"Ir al Mapa de "
                + "Renacimiento\"). De ahí salen tus tres objetivos de 90 días, y sin ellos todavía no se pueden "
                + "planificar acciones ni objetivos.";

        assertThat(new AgregarAccionConfirmable(agregar, planificar).aplicar(APRENDIZ, new InvocacionHerramienta(
                ProponerAgregarAccionHerramienta.NOMBRE, Map.of("eje", "CUERPO", "titulo", "Correr",
                "fecha", MANANA.toString())))).isEqualTo(ResultadoHerramienta.fallo(esperado));
        assertThat(new CrearPlanDelDiaConfirmable(planificar).aplicar(APRENDIZ, new InvocacionHerramienta(
                ProponerPlanDelDiaHerramienta.NOMBRE, Map.of("plan", "{\"fecha\":\"2026-10-03\",\"acciones\":"
                        + "[{\"eje\":\"CUERPO\",\"titulo\":\"Correr\"}]}"))))
                .isEqualTo(ResultadoHerramienta.fallo(esperado));
        assertThat(new CrearPlanDeLaSemanaConfirmable(planificar).aplicar(APRENDIZ, new InvocacionHerramienta(
                ProponerPlanDeLaSemanaHerramienta.NOMBRE, Map.of("plan",
                        "{\"objetivos\":[{\"eje\":\"CUERPO\",\"titulo\":\"Bajar 1 kg\"}]}"))))
                .isEqualTo(ResultadoHerramienta.fallo(esperado));
        assertThat(esperado).doesNotContain("ROCKS_LOCKED").doesNotContain("Exception").doesNotContain("onboarding");
    }
}
