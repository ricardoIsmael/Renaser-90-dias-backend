package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.mapa.ConsultarMapaDeRenacimientoPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.PlanDeManana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDelDia;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona.Objetivo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-233 (pedido del dueno, 30-09): una propuesta de rocas dice a que objetivo de SU Mapa de Renacimiento
 * empuja, segun el eje (CUERPO = salud, TRABAJO = negocio y dinero, RELACIONES = relaciones). Sin Mapa se
 * propone igual y el modelo recibe la orden de invitarla a completarlo.
 */
class PropuestasConObjetivoDelMapaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate MANANA = LocalDate.of(2026, 10, 3);

    private static final MapaDeLaPersona MAPA = new MapaDeLaPersona(true, true, "salud",
            List.of(new Objetivo("salud", "peso", "92", "82", "kg", null, null, null, "mis hijos",
                            "Bajar de 92 a 82 kg al dia 90"),
                    new Objetivo("negocio_dinero", "ventas", "3000", "6000", "soles", "mensual", null, null, null,
                            null)),
            List.of(new MapaDeLaPersona.Hito("salud", 30, "88 kg"), new MapaDeLaPersona.Hito("salud", 60, "85 kg")),
            null, List.of(), List.of());

    /** Su dia 45: el proximo hito es el del dia 60. */
    private static final ConsultarSituacionDelAprendizPort EN_DIA_45 =
            id -> Optional.of(new SituacionDelAprendiz(45, 3, LocalDate.of(2026, 10, 2)));

    private final ConsultarRocasDelAprendizPort rocas = mock(ConsultarRocasDelAprendizPort.class);
    private final PlanificarRocasPort planificar = mock(PlanificarRocasPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final GestionarPlanDeHabitosPort planDeHabitos = mock(GestionarPlanDeHabitosPort.class);

    @BeforeEach
    void reglas() {
        when(planificar.ejesValidos()).thenReturn(List.of("CUERPO", "TRABAJO", "RELACIONES"));
        when(rocas.deManana(APRENDIZ)).thenReturn(new RocasDelDia(MANANA, List.of(),
                new PlanDeManana(true, 0, true, LocalTime.of(18, 0), true)));
    }

    private String resumenPropuesto() {
        ArgumentCaptor<String> resumen = ArgumentCaptor.forClass(String.class);
        verify(proponer).proponer(eq(APRENDIZ), any(), resumen.capture());
        return resumen.getValue();
    }

    private ResultadoHerramienta agregarCorrer(ConsultarMapaDeRenacimientoPort mapaPort) {
        return new ProponerAgregarAccionHerramienta(rocas, planificar, proponer, planDeHabitos,
                new MapaParaProponer(mapaPort, EN_DIA_45), CompuertasDePrueba.LIBRE).ejecutar(APRENDIZ, new InvocacionHerramienta(
                ProponerAgregarAccionHerramienta.NOMBRE, Map.of("eje", "CUERPO", "titulo", "Correr 5 km")));
    }

    @Test
    @DisplayName("agregar una accion de CUERPO: la tarjeta dice 'para tu objetivo de salud' con su meta")
    void accionConSuObjetivo() {
        ResultadoHerramienta resultado = agregarCorrer(id -> MAPA);

        assertThat(resumenPropuesto()).endsWith(" Para tu objetivo de salud: Bajar de 92 a 82 kg al dia 90. "
                + "Proximo hito, dia 60: 85 kg.");
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .contains("Si lo propuesto no apunta a ese objetivo de su Mapa, diselo en una linea");
    }

    @Test
    @DisplayName("objetivo semanal de TRABAJO: sin meta redactada, la meta sale de sus numeros")
    void objetivoSemanalConSuObjetivo() {
        new ProponerPlanDeLaSemanaHerramienta(planificar, proponer, new MapaParaProponer(id -> MAPA, EN_DIA_45),
                CompuertasDePrueba.LIBRE)
                .ejecutar(APRENDIZ, new InvocacionHerramienta(ProponerPlanDeLaSemanaHerramienta.NOMBRE,
                        Map.of("plan", "{\"objetivos\":[{\"eje\":\"TRABAJO\",\"titulo\":\"Llamar a 10 clientes\"}]}")));

        assertThat(resumenPropuesto()).endsWith(" Para tu objetivo de negocio y dinero: de 3000 soles a 6000 soles.");
    }

    @Test
    @DisplayName("un eje sin objetivo en su Mapa: la tarjeta no inventa uno y el modelo lo dice en una linea")
    void ejeSinObjetivo() {
        ResultadoHerramienta resultado = new ProponerAgregarAccionHerramienta(rocas, planificar, proponer,
                planDeHabitos, new MapaParaProponer(id -> MAPA, EN_DIA_45), CompuertasDePrueba.LIBRE).ejecutar(APRENDIZ, new InvocacionHerramienta(
                ProponerAgregarAccionHerramienta.NOMBRE, Map.of("eje", "RELACIONES", "titulo", "Llamar a mama")));

        assertThat(resumenPropuesto()).doesNotContain("Para tu objetivo");
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .contains("Su Mapa no tiene objetivo de Relaciones: diselo en una linea y sugiere como conectarlo");
    }

    @Test
    @DisplayName("sin Mapa se propone igual y el modelo la invita a completarlo; si no se lee, sale como antes")
    void sinMapa() {
        ResultadoHerramienta resultado = agregarCorrer(id -> MapaDeLaPersona.sinMapa());

        assertThat(resumenPropuesto()).doesNotContain("Para tu objetivo");
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .contains("No tiene Mapa de Renacimiento: la propuesta va igual; invitala en una linea");

        assertThat(new MapaParaProponer(id -> {
            throw new IllegalStateException("caida");
        }, EN_DIA_45).para(APRENDIZ, List.of("CUERPO"))).isEqualTo(new MapaParaProponer.Vinculo("", ""));
    }

    @Test
    @DisplayName("varios ejes: un objetivo por eje, sin repetir")
    void variosEjes() {
        MapaParaProponer.Vinculo vinculo = MapaParaProponer.vinculo(MAPA, List.of("CUERPO", "TRABAJO"), null);

        assertThat(vinculo.enElResumen()).isEqualTo(" Para tu objetivo de salud: Bajar de 92 a 82 kg al dia 90. "
                + "Para tu objetivo de negocio y dinero: de 3000 soles a 6000 soles.");
        assertThat(new MapaParaProponer(id -> MAPA, EN_DIA_45).para(APRENDIZ, List.of("CUERPO", "CUERPO"))
                .enElResumen()).isEqualTo(" Para tu objetivo de salud: Bajar de 92 a 82 kg al dia 90. Proximo hito, "
                + "dia 60: 85 kg.");
    }
}
