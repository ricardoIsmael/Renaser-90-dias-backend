package com.renaser.os.rag.infrastructure.adapter.out.rocks;

import com.renaser.os.rag.application.ports.out.rocas.AgregarAccionAlPlanPort;
import com.renaser.os.rag.application.ports.out.rocas.EditarObjetivoSemanalPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.PlanificarRocasPort.AccionDelPlan;
import com.renaser.os.rocks.api.AgregarAccionAlDiaPort;
import com.renaser.os.rocks.api.AgregarAccionAlDiaPort.AccionNueva;
import com.renaser.os.rocks.api.AgregarAccionAlDiaPort.ResultadoAgregado;
import com.renaser.os.rocks.api.CierreDeSemanaPort;
import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort;
import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort.CambioDelObjetivo;
import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort.ResultadoEdicion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-177: los adaptadores de agregar una accion y editar un objetivo semanal solo traducen. */
class AjustesDeRocasAdaptersTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);

    private final AgregarAccionAlDiaPort agregar = mock(AgregarAccionAlDiaPort.class);
    private final EdicionDeObjetivoSemanalPort edicion = mock(EdicionDeObjetivoSemanalPort.class);
    private final AgregarAccionAlPlanAdapter agregarAdapter = new AgregarAccionAlPlanAdapter(agregar);
    private final EditarObjetivoSemanalAdapter editarAdapter = new EditarObjetivoSemanalAdapter(edicion);

    @Test
    @DisplayName("la accion va con impacto 5 y no delegable, como la app y como el plan del dia")
    void accionComoLaApp() {
        when(agregar.agregar(any(), any(), any())).thenReturn(new ResultadoAgregado.Agregada("CUERPO", 2, "AMARILLA"));

        AgregarAccionAlPlanPort.Resultado resultado = agregarAdapter.agregar(APRENDIZ, JUEVES,
                new AccionDelPlan("CUERPO", "Estirar", LocalTime.of(7, 0), null));

        assertThat(resultado).isEqualTo(new AgregarAccionAlPlanPort.Resultado.Agregada("CUERPO", 2, "AMARILLA"));
        verify(agregar).agregar(APRENDIZ, JUEVES, new AccionNueva("CUERPO", "Estirar",
                PlanificarRocasAdapter.PUNTAJE_IMPACTO_COMO_LA_APP, false, LocalTime.of(7, 0), null));
    }

    @ParameterizedTest
    @EnumSource(AgregarAccionAlDiaPort.MotivoRechazo.class)
    @DisplayName("cada motivo de agregar tiene su espejo en rag")
    void espejoDeAgregar(AgregarAccionAlDiaPort.MotivoRechazo motivo) {
        when(agregar.agregar(any(), any(), any())).thenReturn(new ResultadoAgregado.Rechazado(motivo));

        assertThat(agregarAdapter.agregar(APRENDIZ, JUEVES, new AccionDelPlan("CUERPO", "x", null, null)))
                .isEqualTo(new AgregarAccionAlPlanPort.Resultado.Rechazado(
                        AgregarAccionAlPlanPort.Motivo.valueOf(motivo.name())));
    }

    @ParameterizedTest
    @EnumSource(EdicionDeObjetivoSemanalPort.MotivoRechazo.class)
    @DisplayName("cada motivo de editar tiene su espejo en rag, y el cambio llega entero")
    void espejoDeEditar(EdicionDeObjetivoSemanalPort.MotivoRechazo motivo) {
        when(edicion.editar(any(), anyInt(), any(), any())).thenReturn(new ResultadoEdicion.Rechazado(motivo));

        assertThat(editarAdapter.editar(APRENDIZ, 4, "TRABAJO", new EditarObjetivoSemanalPort.Cambio("t", "o", "c", 6)))
                .isEqualTo(new EditarObjetivoSemanalPort.Resultado.Rechazado(
                        EditarObjetivoSemanalPort.Motivo.valueOf(motivo.name())));
        verify(edicion).editar(APRENDIZ, 4, "TRABAJO", new CambioDelObjetivo("t", "o", "c", 6));
    }

    @Test
    @DisplayName("la ventana, la escala y la ultima semana que usa rag para explicar son las de rocks")
    void reglasDeRocks() {
        assertThat(editarAdapter.ventanaDeEdicion()).isEqualTo(new EditarObjetivoSemanalPort.VentanaDeEdicion(
                EdicionDeObjetivoSemanalPort.VENTANA_ABRE_DOMINGO_HORA, EdicionDeObjetivoSemanalPort.VENTANA_CIERRA_LUNES_HORA,
                EdicionDeObjetivoSemanalPort.MARGEN_TARDIO_HORAS));
        assertThat(PlanificarRocasPort.AUTOEVALUACION_MINIMA).isEqualTo(CierreDeSemanaPort.AUTOEVALUACION_MINIMA);
        assertThat(PlanificarRocasPort.AUTOEVALUACION_MAXIMA).isEqualTo(CierreDeSemanaPort.AUTOEVALUACION_MAXIMA);
        assertThat(EditarObjetivoSemanalPort.ULTIMA_SEMANA).isEqualTo(EdicionDeObjetivoSemanalPort.ULTIMA_SEMANA);
    }
}
