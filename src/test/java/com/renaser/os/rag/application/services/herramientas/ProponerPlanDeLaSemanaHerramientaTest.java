package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@code proponer_plan_de_la_semana} (R2): valida la forma y deja una propuesta; nunca escribe. */
class ProponerPlanDeLaSemanaHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private final PlanificarRocasPort planificar = mock(PlanificarRocasPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final ProponerPlanDeLaSemanaHerramienta herramienta =
            new ProponerPlanDeLaSemanaHerramienta(planificar, proponer);

    @BeforeEach
    void ejes() {
        when(planificar.ejesValidos()).thenReturn(List.of("CUERPO", "TRABAJO", "RELACIONES"));
    }

    private static InvocacionHerramienta con(String plan) {
        return new InvocacionHerramienta(ProponerPlanDeLaSemanaHerramienta.NOMBRE,
                Map.of(ProponerPlanDeLaSemanaHerramienta.ARGUMENTO_PLAN, plan));
    }

    @Test
    @DisplayName("un plan valido deja una propuesta con el resumen exacto y el JSON normalizado")
    void propone() {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, con("""
                {"objetivos":[
                  {"eje":"trabajo","titulo":" Cerrar 2 ventas ","obstaculo":"Poco tiempo",
                   "contingencia":"Llamar en la hora de almuerzo"},
                  {"eje":"CUERPO","titulo":"Bajar 1 kg","obstaculo":"   ","contingencia":null}]}"""));

        String resumen = "Crear los objetivos de la semana. Trabajo: Cerrar 2 ventas (obstaculo: Poco tiempo) "
                + "(contingencia: Llamar en la hora de almuerzo). Cuerpo: Bajar 1 kg. Si algun eje ya tiene "
                + "objetivo esa semana, se deja como esta.";
        verify(proponer).proponer(APRENDIZ, con("{\"objetivos\":["
                + "{\"eje\":\"TRABAJO\",\"titulo\":\"Cerrar 2 ventas\",\"obstaculo\":\"Poco tiempo\","
                + "\"contingencia\":\"Llamar en la hora de almuerzo\"},"
                + "{\"eje\":\"CUERPO\",\"titulo\":\"Bajar 1 kg\"}]}"), resumen);
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .startsWith("Propuesta creada: " + resumen).contains("TODAVIA NO esta guardado");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"objetivos\":[{\"eje\":\"TRABAJO\",\"titulo\":\"Vender\",\"acciones\":[\"a\"]}]}",
            "{\"objetivos\":[{\"eje\":\"TRABAJO\",\"titulo\":\"Vender\"}],\"semana\":\"3\"}",
            "{\"objetivos\":[{\"eje\":\"NEGOCIO\",\"titulo\":\"Vender\"}]}",
            "{\"objetivos\":[{\"eje\":\"TRABAJO\"}]}"
    })
    @DisplayName("campos de mas, un eje inventado o sin titulo: Fallo sin propuesta")
    void formaInvalida(String plan) {
        assertThat(herramienta.ejecutar(APRENDIZ, con(plan))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponer);
    }

    @Test
    @DisplayName("si no se puede guardar la propuesta, Fallo legible y no se dice 'propuesta creada'")
    void falloAlGuardarLaPropuesta() {
        when(proponer.proponer(any(), any(), anyString())).thenThrow(new IllegalStateException("base caida"));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                con("{\"objetivos\":[{\"eje\":\"CUERPO\",\"titulo\":\"Bajar 1 kg\"}]}"));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo("No pude preparar la confirmacion en este momento."));
        verify(planificar, never()).crearPlanDeLaSemana(any(), any());
    }
}
