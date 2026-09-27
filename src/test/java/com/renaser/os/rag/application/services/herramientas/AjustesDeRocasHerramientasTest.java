package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.rocas.AgregarAccionAlPlanPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.PlanDeManana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDelDia;
import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort;
import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort.Cambio;
import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort.VentanaDeEdicion;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-177: {@code proponer_agregar_accion} y {@code proponer_editar_objetivo_semanal}, con sus
 * confirmables. "Manana" y "editable" los resuelve {@code rocks}; aca se prueba lo que la herramienta
 * decide: forma, el dia en curso, la ventana cerrada con su motivo, el resumen y lo que se guarda.
 */
class AjustesDeRocasHerramientasTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 9, 23);
    private static final LocalDate MANANA = HOY.plusDays(1);
    private static final VentanaDeEdicion VENTANA = new VentanaDeEdicion(12, 9, 2);

    private final ConsultarRocasDelAprendizPort rocas = mock(ConsultarRocasDelAprendizPort.class);
    private final PlanificarRocasPort planificar = mock(PlanificarRocasPort.class);
    private final AgregarAccionAlPlanPort agregar = mock(AgregarAccionAlPlanPort.class);
    private final EditarObjetivoSemanalPort editar = mock(EditarObjetivoSemanalPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);

    private final ProponerAgregarAccionHerramienta agregarAccion =
            new ProponerAgregarAccionHerramienta(rocas, planificar, proponer);
    private final AgregarAccionConfirmable agregarConfirmable = new AgregarAccionConfirmable(agregar, planificar);
    private final ProponerEditarObjetivoSemanalHerramienta editarObjetivo =
            new ProponerEditarObjetivoSemanalHerramienta(rocas, planificar, editar, proponer);
    private final EditarObjetivoSemanalConfirmable editarConfirmable = new EditarObjetivoSemanalConfirmable(editar,
            planificar);

    @BeforeEach
    void reglas() {
        when(planificar.ejesValidos()).thenReturn(List.of("CUERPO", "TRABAJO", "RELACIONES"));
        when(editar.ventanaDeEdicion()).thenReturn(VENTANA);
        when(rocas.deManana(APRENDIZ)).thenReturn(new RocasDelDia(MANANA, List.of(),
                new PlanDeManana(true, 2, true, LocalTime.of(18, 0), true)));
    }

    // ---- proponer_agregar_accion ----

    @Test
    @DisplayName("sin fecha es manana: el resumen dice que va detras y lo guardado lleva la fecha resuelta")
    void agregaAManana() {
        ResultadoHerramienta resultado = agregarAccion.ejecutar(APRENDIZ, invocacion(
                ProponerAgregarAccionHerramienta.NOMBRE, "eje", "cuerpo", "titulo", " Estirar 10 minutos ",
                "inicio", "07:00", "fin", "07:10"));

        String resumen = "Agregar al plan del jueves 2026-09-24, en Cuerpo: Estirar 10 minutos (07:00 a 07:10). Va "
                + "detras de las acciones que ese eje ya tiene ese dia; las demas no se tocan.";
        verify(proponer).proponer(APRENDIZ, invocacion(ProponerAgregarAccionHerramienta.NOMBRE, "fecha",
                "2026-09-24", "eje", "CUERPO", "titulo", "Estirar 10 minutos", "inicio", "07:00", "fin", "07:10"), resumen);
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).startsWith("Propuesta creada: " + resumen)
                .contains("TODAVIA NO esta guardado");
    }

    @Test
    @DisplayName("hoy (manana menos un dia en su zona) se rechaza sin propuesta: el dia en curso no se reacomoda")
    void hoyNo() {
        ResultadoHerramienta resultado = agregarAccion.ejecutar(APRENDIZ, invocacion(
                ProponerAgregarAccionHerramienta.NOMBRE, "eje", "CUERPO", "titulo", "Estirar", "fecha", "2026-09-23"));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo(TextoDeAjustesDeRocas.DIA_EN_CURSO));
        verifyNoInteractions(proponer);
    }

    @Test
    @DisplayName("un dia que ya paso, un eje inventado o una hora mal escrita: Fallo sin propuesta")
    void formaInvalida() {
        assertThat(agregarAccion.ejecutar(APRENDIZ, invocacion(ProponerAgregarAccionHerramienta.NOMBRE,
                "eje", "CUERPO", "titulo", "Estirar", "fecha", "2026-09-20"))).isEqualTo(ResultadoHerramienta.fallo(
                "Ese dia ya paso. Se puede agregar desde manana hasta el ultimo dia de esta semana de programa."));
        assertThat(agregarAccion.ejecutar(APRENDIZ, invocacion(ProponerAgregarAccionHerramienta.NOMBRE,
                "eje", "NEGOCIO", "titulo", "Vender"))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(agregarAccion.ejecutar(APRENDIZ, invocacion(ProponerAgregarAccionHerramienta.NOMBRE,
                "eje", "CUERPO", "titulo", "Estirar", "inicio", "7am"))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponer);
    }

    @Test
    @DisplayName("confirmar agrega con lo guardado y dice la posicion y el color que decidio rocks")
    void confirmarAgrega() {
        AccionDelPlan accion = new AccionDelPlan("CUERPO", "Estirar", LocalTime.of(7, 0), null);
        when(agregar.agregar(APRENDIZ, MANANA, accion))
                .thenReturn(new AgregarAccionAlPlanPort.Resultado.Agregada("CUERPO", 3, "ROJA"));

        ResultadoHerramienta resultado = agregarConfirmable.aplicar(APRENDIZ, invocacion(
                ProponerAgregarAccionHerramienta.NOMBRE, "fecha", "2026-09-24", "eje", "CUERPO", "titulo", "Estirar",
                "inicio", "07:00"));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.exito(
                "Accion agregada al jueves 2026-09-24: Cuerpo #3 ROJA."));
    }

    @ParameterizedTest
    @EnumSource(AgregarAccionAlPlanPort.Motivo.class)
    @DisplayName("cada rechazo de rocks al confirmar tiene su texto, y ninguno dice que quedo hecho")
    void confirmarRechazado(AgregarAccionAlPlanPort.Motivo motivo) {
        when(agregar.agregar(any(), any(), any())).thenReturn(new AgregarAccionAlPlanPort.Resultado.Rechazado(motivo));

        ResultadoHerramienta resultado = agregarConfirmable.aplicar(APRENDIZ, invocacion(
                ProponerAgregarAccionHerramienta.NOMBRE, "fecha", "2026-09-24", "eje", "CUERPO", "titulo", "Estirar"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).isNotBlank().doesNotContain("agregada");
    }

    @Test
    @DisplayName("una propuesta guardada sin fecha no se ejecuta: no se adivina manana al confirmar")
    void confirmarSinFecha() {
        assertThat(agregarConfirmable.aplicar(APRENDIZ, invocacion(ProponerAgregarAccionHerramienta.NOMBRE,
                "eje", "CUERPO", "titulo", "Estirar"))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(agregar, never()).agregar(any(), any(), any());
    }

    // ---- proponer_editar_objetivo_semanal ----

    @Test
    @DisplayName("dentro de la ventana: propone solo lo que cambia, con la semana que vio escrita")
    void editaEnVentana() {
        when(rocas.deLaSemana(APRENDIZ)).thenReturn(semana(true));

        editarObjetivo.ejecutar(APRENDIZ, invocacion(ProponerEditarObjetivoSemanalHerramienta.NOMBRE,
                "eje", "trabajo", "obstaculo", "Las reuniones", "autoevaluacionInicio", "6.0"));

        verify(proponer).proponer(APRENDIZ, invocacion(ProponerEditarObjetivoSemanalHerramienta.NOMBRE, "semana", "4",
                "eje", "TRABAJO", "obstaculo", "Las reuniones", "autoevaluacionInicio", "6"),
                "Cambiar el objetivo de Trabajo de la semana 4. obstaculo: Las reuniones; como arranca: 6/10. Lo que "
                        + "no se nombra queda como esta.");
    }

    @Test
    @DisplayName("fuera de la ventana: el motivo real con la regla de rocks y lo que si se puede, sin propuesta")
    void ventanaCerrada() {
        when(rocas.deLaSemana(APRENDIZ)).thenReturn(semana(false));

        ResultadoHerramienta resultado = editarObjetivo.ejecutar(APRENDIZ, invocacion(
                ProponerEditarObjetivoSemanalHerramienta.NOMBRE, "eje", "TRABAJO", "titulo", "Vender 3"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo())
                .contains("domingo desde las 12:00 hasta el lunes a las 09:00")
                .contains("hasta 2 horas despues de crearlo")
                .contains("Lo que si se puede: ajustar las acciones de los dias que vienen");
        verifyNoInteractions(proponer);
    }

    @Test
    @DisplayName("un eje sin objetivo, nada que cambiar o una escala fuera de 1-10: Fallo sin propuesta")
    void edicionInvalida() {
        when(rocas.deLaSemana(APRENDIZ)).thenReturn(semana(true));

        assertThat(editarObjetivo.ejecutar(APRENDIZ, invocacion(ProponerEditarObjetivoSemanalHerramienta.NOMBRE,
                "eje", "CUERPO", "titulo", "Correr"))).isEqualTo(ResultadoHerramienta.fallo("No tiene objetivo de "
                + "Cuerpo esta semana: se crea con el plan de la semana, no se edita."));
        assertThat(editarObjetivo.ejecutar(APRENDIZ, invocacion(ProponerEditarObjetivoSemanalHerramienta.NOMBRE,
                "eje", "TRABAJO"))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(editarObjetivo.ejecutar(APRENDIZ, invocacion(ProponerEditarObjetivoSemanalHerramienta.NOMBRE,
                "eje", "TRABAJO", "autoevaluacionInicio", "11"))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(editarObjetivo.ejecutar(APRENDIZ, invocacion(ProponerEditarObjetivoSemanalHerramienta.NOMBRE,
                "eje", "TRABAJO", "titulo", "x", "semana", "pasada"))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(proponer, never()).proponer(any(), any(), anyString());
    }

    @Test
    @DisplayName("la semana siguiente (la del domingo) se propone con su numero; la ventana la decide rocks al confirmar")
    void semanaSiguiente() {
        when(rocas.deLaSemana(APRENDIZ)).thenReturn(semana(false));
        when(proponer.proponer(any(), any(), anyString())).thenReturn(new PropuestaCreada(UUID.randomUUID(), "r",
                Instant.parse("2026-09-27T20:00:00Z")));

        ResultadoHerramienta resultado = editarObjetivo.ejecutar(APRENDIZ, invocacion(
                ProponerEditarObjetivoSemanalHerramienta.NOMBRE, "eje", "TRABAJO", "titulo", "Vender 3", "semana",
                "siguiente"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        verify(proponer).proponer(APRENDIZ, invocacion(ProponerEditarObjetivoSemanalHerramienta.NOMBRE, "semana", "5",
                "eje", "TRABAJO", "titulo", "Vender 3"), "Cambiar el objetivo de Trabajo de la semana 5. objetivo: "
                + "Vender 3. Lo que no se nombra queda como esta.");
    }

    @Test
    @DisplayName("confirmar edita la semana guardada; si la ventana cerro entretanto, el motivo real")
    void confirmarEdicion() {
        Cambio cambio = new Cambio("Vender 3", null, null, null);
        when(editar.editar(APRENDIZ, 4, "TRABAJO", cambio)).thenReturn(new EditarObjetivoSemanalPort.Resultado.Editado(
                "TRABAJO"));
        InvocacionHerramienta guardada = invocacion(ProponerEditarObjetivoSemanalHerramienta.NOMBRE, "semana", "4",
                "eje", "TRABAJO", "titulo", "Vender 3");

        assertThat(editarConfirmable.aplicar(APRENDIZ, guardada))
                .isEqualTo(ResultadoHerramienta.exito("Objetivo de Trabajo de la semana 4 actualizado."));

        when(editar.editar(APRENDIZ, 4, "TRABAJO", cambio)).thenReturn(new EditarObjetivoSemanalPort.Resultado.Rechazado(
                EditarObjetivoSemanalPort.Motivo.VENTANA_CERRADA));
        assertThat(((ResultadoHerramienta.Fallo) editarConfirmable.aplicar(APRENDIZ, guardada)).motivo())
                .isEqualTo(TextoDeAjustesDeRocas.ventanaCerrada(VENTANA));
    }

    @Test
    @DisplayName("una edicion guardada sin semana no se ejecuta")
    void confirmarSinSemana() {
        assertThat(editarConfirmable.aplicar(APRENDIZ, invocacion(ProponerEditarObjetivoSemanalHerramienta.NOMBRE,
                "eje", "TRABAJO", "titulo", "x"))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(editar, never()).editar(any(), anyInt(), any(), any());
    }

    private static RocasDeLaSemana semana(boolean editable) {
        return new RocasDeLaSemana(4, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27), List.of(
                new RocaDeLaSemana("TRABAJO", "Vender 2", null, null, editable, false, null)));
    }

    private static InvocacionHerramienta invocacion(String nombre, String... claveValor) {
        Map<String, String> argumentos = new LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            argumentos.put(claveValor[i], claveValor[i + 1]);
        }
        return new InvocacionHerramienta(nombre, argumentos);
    }
}
