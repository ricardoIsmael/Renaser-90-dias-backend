package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.PlanDeManana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDelDia;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDelDia;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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
 * {@code proponer_plan_del_dia} (R2): valida la forma, mira lo que {@code consultar_rocas} ya expone
 * y deja una propuesta con el resumen exacto. Nunca escribe: eso es de {@link CrearPlanDelDiaConfirmable}.
 */
class ProponerPlanDelDiaHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    /** Miercoles 2026-09-23 en la zona de la persona; manana es jueves. */
    private static final LocalDate HOY = LocalDate.of(2026, 9, 23);
    private static final LocalDate MANANA = HOY.plusDays(1);
    private static final PlanDeManana SIN_PLAN = new PlanDeManana(false, 0, true, LocalTime.of(18, 0), true);
    private static final PlanDeManana CON_DOS = new PlanDeManana(true, 2, true, LocalTime.of(18, 0), true);

    private final ConsultarRocasDelAprendizPort rocas = mock(ConsultarRocasDelAprendizPort.class);
    private final PlanificarRocasPort planificar = mock(PlanificarRocasPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final ProponerPlanDelDiaHerramienta herramienta =
            new ProponerPlanDelDiaHerramienta(rocas, planificar, proponer);

    @BeforeEach
    void ejes() {
        when(planificar.ejesValidos()).thenReturn(List.of("CUERPO", "TRABAJO", "RELACIONES"));
    }

    private void mananaCon(PlanDeManana plan) {
        when(rocas.deManana(APRENDIZ)).thenReturn(new RocasDelDia(MANANA, List.of(), plan));
    }

    private static InvocacionHerramienta con(String plan) {
        return new InvocacionHerramienta(ProponerPlanDelDiaHerramienta.NOMBRE,
                Map.of(ProponerPlanDelDiaHerramienta.ARGUMENTO_PLAN, plan));
    }

    @Test
    @DisplayName("un plan valido deja una propuesta con el resumen exacto y el JSON normalizado, y dice que NO esta hecho")
    void propone() {
        mananaCon(SIN_PLAN);

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, con("""
                {"fecha":"2026-09-24","acciones":[
                  {"eje":"cuerpo","titulo":"  Caminar 30 minutos ","inicio":"06:00","fin":"06:30"},
                  {"eje":"TRABAJO","titulo":"Llamar a 3 clientes","inicio":"09:00"},
                  {"eje":"CUERPO","titulo":"Pesarme en ayunas"}]}"""));

        String resumen = "Crear el plan del jueves 2026-09-24. Cuerpo: 1) Caminar 30 minutos (06:00 a 06:30); "
                + "2) Pesarme en ayunas (sin hora). Trabajo: 1) Llamar a 3 clientes (desde 09:00).";
        verify(proponer).proponer(APRENDIZ, con("{\"fecha\":\"2026-09-24\",\"acciones\":["
                + "{\"eje\":\"CUERPO\",\"titulo\":\"Caminar 30 minutos\",\"inicio\":\"06:00\",\"fin\":\"06:30\"},"
                + "{\"eje\":\"TRABAJO\",\"titulo\":\"Llamar a 3 clientes\",\"inicio\":\"09:00\"},"
                + "{\"eje\":\"CUERPO\",\"titulo\":\"Pesarme en ayunas\"}]}"), resumen);
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .startsWith("Propuesta creada: " + resumen).contains("TODAVIA NO esta guardado");
    }

    @Test
    @DisplayName("sin fecha es manana en la zona de la persona, y se guarda ESCRITA: confirmar despues de medianoche no la corre")
    void sinFechaEsMananaYQuedaEscrita() {
        mananaCon(CON_DOS);

        herramienta.ejecutar(APRENDIZ, con("{\"acciones\":[{\"eje\":\"RELACIONES\",\"titulo\":\"Cenar con mi hija\","
                + "\"inicio\":\"20:00\"}]}"));

        verify(proponer).proponer(APRENDIZ, con("{\"fecha\":\"2026-09-24\",\"acciones\":["
                        + "{\"eje\":\"RELACIONES\",\"titulo\":\"Cenar con mi hija\",\"inicio\":\"20:00\"}]}"),
                "Crear el plan del jueves 2026-09-24. Relaciones: 1) Cenar con mi hija (desde 20:00). "
                        + "Reemplaza las 2 accion(es) que ya tiene para ese dia.");
    }

    @Test
    @DisplayName("un dia posterior a manana avisa que, si ya tenia acciones, se reemplazan")
    void diaPosteriorAvisaElReemplazo() {
        mananaCon(SIN_PLAN);

        herramienta.ejecutar(APRENDIZ, con("{\"fecha\":\"2026-09-26\",\"acciones\":[{\"eje\":\"CUERPO\","
                + "\"titulo\":\"Nadar\",\"fin\":\"08:00\"}]}"));

        verify(proponer).proponer(any(), any(), eq("Crear el plan del sabado 2026-09-26. "
                + "Cuerpo: 1) Nadar (hasta 08:00). Si ese dia ya tiene acciones, se reemplazan."));
    }

    @Test
    @DisplayName("el dia en curso ya armado no se reacomoda: Fallo y ninguna propuesta")
    void hoyYaArmado() {
        mananaCon(SIN_PLAN);
        when(rocas.deHoy(APRENDIZ)).thenReturn(new RocasDelDia(HOY, List.of(new RocaDelDia("CUERPO", 1, "VERDE",
                "Caminar", null, null, false, false)), SIN_PLAN));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, con("{\"fecha\":\"2026-09-23\","
                + "\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Correr\"}]}"));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo(
                "El dia en curso ya esta armado y no se reacomoda. Puede cambiar los que vienen."));
        verify(proponer, never()).proponer(any(), any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no es json",
            "[]",
            "{\"acciones\":[]}",
            "{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Correr\",\"puntos\":\"10\"}]}",
            "{\"fecha\":\"2026-09-24\",\"color\":\"VERDE\",\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Correr\"}]}",
            "{\"acciones\":[{\"eje\":\"SALUD\",\"titulo\":\"Correr\"}]}",
            "{\"acciones\":[{\"eje\":\"CUERPO\"}]}",
            "{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Correr\",\"inicio\":\"7am\"}]}",
            "{\"fecha\":\"24/09/2026\",\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Correr\"}]}",
            "{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":3}]}",
            "{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Correr\"}]} basura",
            "{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"a\",\"titulo\":\"b\"}]}"
    })
    @DisplayName("un JSON invalido, con campos de mas o con un eje o una hora que no existen: Fallo sin propuesta")
    void formaInvalida(String plan) {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, con(plan));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponer, rocas);
    }

    @Test
    @DisplayName("el campo de mas se nombra en el motivo, para que el modelo lo corrija")
    void campoDeMasSeNombra() {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                con("{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Correr\",\"puntos\":\"10\"}]}"));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo("La accion 1 tiene un campo que no se usa: "
                + "'puntos'. Solo se aceptan: eje, fin, inicio, titulo."));
    }

    @Test
    @DisplayName("una cuenta sin acceso a rocas recibe un Fallo legible, no la excepcion")
    void sinAcceso() {
        when(rocas.deManana(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                con("{\"acciones\":[{\"eje\":\"CUERPO\",\"titulo\":\"Correr\"}]}"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponer);
    }
}
