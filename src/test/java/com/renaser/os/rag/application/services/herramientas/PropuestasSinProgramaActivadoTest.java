package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.PlanDelAprendiz;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarCompuertaDeRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.PlanDeManana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDelDia;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 036 (D-309) en SER (tarea T020 de la spec 038): sin Dia 1 activado {@code rocks} rechaza cualquier fecha con {@code INVALID_DATE}, asi que
 * {@code proponer_plan_del_dia} (y agregar una accion a un dia) no dejan una tarjeta que va a fallar: dicen que active su programa con «Empezar manana».
 * Quien ya lo activo (su programa empieza manana) SI puede planificar: es lo que deja el servidor.
 */
class PropuestasSinProgramaActivadoTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 11, 2);
    private static final LocalDate MANANA = HOY.plusDays(1);

    private final ConsultarRocasDelAprendizPort rocas = mock(ConsultarRocasDelAprendizPort.class);
    private final PlanificarRocasPort planificar = mock(PlanificarRocasPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final GestionarPlanDeHabitosPort planDeHabitos = mock(GestionarPlanDeHabitosPort.class);
    private final MapaParaProponer sinMapaQueLeer = new MapaParaProponer(
            id -> { throw new IllegalStateException("no se lee en esta prueba"); }, id -> Optional.empty());

    @BeforeEach
    void reglas() {
        when(planificar.ejesValidos()).thenReturn(List.of("CUERPO", "TRABAJO", "RELACIONES"));
        when(rocas.deManana(APRENDIZ)).thenReturn(new RocasDelDia(MANANA, List.of(),
                new PlanDeManana(false, 0, true, LocalTime.of(18, 0), true)));
        when(planDeHabitos.planDe(APRENDIZ)).thenReturn(new PlanDelAprendiz(HOY, List.of(), List.of()));
        when(proponer.proponer(any(), any(), anyString())).thenReturn(new PropuestaCreada(UUID.randomUUID(), "r",
                Instant.parse("2026-11-03T05:00:00Z")));
    }

    private static CompuertaParaProponer conSituacion(Optional<SituacionDelAprendiz> situacion) {
        ConsultarCompuertaDeRocasPort libre = new ConsultarCompuertaDeRocasPort() {
            @Override
            public boolean rocasMaestrasCompletas(UserId aprendizId) {
                return true;
            }

            @Override
            public List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha) {
                return List.of();
            }
        };
        ConsultarSituacionDelAprendizPort puerto = participanteId -> situacion;
        return new CompuertaParaProponer(libre, puerto);
    }

    private ResultadoHerramienta planDelDia(CompuertaParaProponer compuerta) {
        return new ProponerPlanDelDiaHerramienta(rocas, planificar, proponer, sinMapaQueLeer, compuerta).ejecutar(APRENDIZ,
                new InvocacionHerramienta(ProponerPlanDelDiaHerramienta.NOMBRE,
                        Map.of("plan", "{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Caminar 30 minutos\"}]}")));
    }

    private ResultadoHerramienta agregarAccion(CompuertaParaProponer compuerta) {
        return new ProponerAgregarAccionHerramienta(rocas, planificar, proponer, planDeHabitos, sinMapaQueLeer, compuerta)
                .ejecutar(APRENDIZ, new InvocacionHerramienta(ProponerAgregarAccionHerramienta.NOMBRE,
                        Map.of("eje", "CUERPO", "titulo", "Correr 5 km")));
    }

    private static SituacionDelAprendiz diaCero(LocalDate diaUno) {
        return new SituacionDelAprendiz(0, 1, HOY).conDiaUno(diaUno);
    }

    @Test
    @DisplayName("spec 036: sin Dia 1 activado, proponer_plan_del_dia y agregar una accion no dejan tarjeta y mandan a «Empezar manana»")
    void sinActivarNoSePropone() {
        CompuertaParaProponer sinActivar = conSituacion(Optional.of(diaCero(null)));

        for (ResultadoHerramienta resultado : List.of(planDelDia(sinActivar), agregarAccion(sinActivar))) {
            assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo(CompuertaParaProponer.PROGRAMA_SIN_ACTIVAR));
        }
        verify(proponer, never()).proponer(any(), any(), anyString());
        assertThat(CompuertaParaProponer.PROGRAMA_SIN_ACTIVAR).startsWith("No se creó ninguna propuesta: todavía no activó su programa")
                .contains("«Empezar mañana»").doesNotContain("Mapa");
    }

    @Test
    @DisplayName("spec 036: quien ya activo su programa (empieza manana) si puede planificar: la propuesta sale")
    void conElProgramaActivadoSePropone() {
        ResultadoHerramienta resultado = planDelDia(conSituacion(Optional.of(diaCero(MANANA))));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        verify(proponer).proponer(any(), any(), anyString());
    }

    @Test
    @DisplayName("spec 036: sin poder leer la situacion no se bloquea (sigue decidiendo el servidor), y con el programa en curso tampoco")
    void sinSituacionOEnCursoNoBloquea() {
        assertThat(conSituacion(Optional.empty()).bloqueoPorProgramaSinActivar(APRENDIZ)).isEmpty();
        assertThat(conSituacion(Optional.of(new SituacionDelAprendiz(7, 1, HOY))).bloqueoPorProgramaSinActivar(APRENDIZ)).isEmpty();
        ConsultarCompuertaDeRocasPort libre = mock(ConsultarCompuertaDeRocasPort.class);
        ConsultarSituacionDelAprendizPort quePeta = participanteId -> { throw new IllegalStateException("base caida"); };
        assertThat(new CompuertaParaProponer(libre, quePeta).bloqueoPorProgramaSinActivar(APRENDIZ)).isEmpty();
    }
}
