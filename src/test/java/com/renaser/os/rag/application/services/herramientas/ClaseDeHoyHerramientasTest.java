package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.ClaseDeHoy;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.EstadoClase;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

/**
 * {@code consultar_clase_de_hoy}, {@code proponer_entregar_clase_de_hoy} y su confirmable
 * (2026-09-23): leer sin disparar IA, proponer sin entregar, y no entregar sobre otro dia.
 *
 * <p>Sin {@code FixedClock}: ninguna de estas clases lee el reloj. El dia de programa lo decide
 * {@code academy}; el caso "paso la medianoche entre proponer y confirmar" se cubre con el dia que
 * devuelve el puerto ({@link #noEntregaSiCambioElDia}).
 */
class ClaseDeHoyHerramientasTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final String RESUMEN = "Entendi que la constancia pesa mas que la intensidad.";

    private final ClaseDiariaDelAprendizPort puerto = mock(ClaseDiariaDelAprendizPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);
    private final ConsultarClaseDeHoyHerramienta consultar = new ConsultarClaseDeHoyHerramienta(puerto);
    private final PropuestaDeEntregarClaseDeHoy proponer = new PropuestaDeEntregarClaseDeHoy(puerto, proponerAccion);
    private final EntregarClaseDeHoyConfirmable confirmable = new EntregarClaseDeHoyConfirmable(puerto);

    private static ClaseDeHoy disponible(int dia, String leccionId, boolean vista) {
        return new ClaseDeHoy(EstadoClase.DISPONIBLE, dia, "Fundamentos", leccionId, "Clase 12: Constancia", vista,
                15, 2000);
    }

    private static InvocacionHerramienta pedido(String resumen) {
        return new InvocacionHerramienta(PropuestaDeEntregarClaseDeHoy.NOMBRE,
                Map.of(PropuestaDeEntregarClaseDeHoy.ARGUMENTO_RESUMEN, resumen));
    }

    private static InvocacionHerramienta guardada(String leccionId, int dia) {
        return new InvocacionHerramienta(PropuestaDeEntregarClaseDeHoy.NOMBRE, Map.of(
                PropuestaDeEntregarClaseDeHoy.ARGUMENTO_RESUMEN, RESUMEN,
                PropuestaDeEntregarClaseDeHoy.ARGUMENTO_LECCION_ID, leccionId,
                PropuestaDeEntregarClaseDeHoy.ARGUMENTO_DIA_PROGRAMA, Integer.toString(dia)));
    }

    @Test
    @DisplayName("consultar: titulo, si esta vista, que hace falta y sin recomendacion inventada")
    void consultaLaClaseDeHoy() {
        when(puerto.claseDeHoy(APRENDIZ)).thenReturn(disponible(12, "lec-12", false));

        String texto = ((ResultadoHerramienta.Exito) consultar.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarClaseDeHoyHerramienta.NOMBRE))).contenido();

        assertThat(texto).contains("dia 12").contains("Clase 12: Constancia").contains("leccion_id=lec-12")
                .contains("Leccion vista: no").contains("de 15 a 2000 caracteres").contains("conviene que la vea")
                .contains("no inventes una");
    }

    @Test
    @DisplayName("consultar: dia 0 y cuenta suspendida vuelven como texto legible")
    void consultaSinClaseOSuspendida() {
        when(puerto.claseDeHoy(APRENDIZ)).thenReturn(new ClaseDeHoy(EstadoClase.NO_INICIADO, 0, null, null, null,
                false, 15, 2000));
        assertThat(((ResultadoHerramienta.Exito) consultar.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarClaseDeHoyHerramienta.NOMBRE))).contenido())
                .contains("dia 0");

        when(puerto.claseDeHoy(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        assertThat(consultar.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(ConsultarClaseDeHoyHerramienta.NOMBRE)))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
    }

    @Test
    @DisplayName("proponer: guarda leccion y dia de academy, muestra el texto y los puntos, y no entrega")
    void proponeSinEntregar() {
        when(puerto.claseDeHoy(APRENDIZ)).thenReturn(disponible(12, "lec-12", true));

        ResultadoHerramienta resultado = proponer.ejecutar(APRENDIZ, pedido("  " + RESUMEN + "  "));

        String resumen = PropuestaDeEntregarClaseDeHoy.resumenDe(
                new PropuestaDeEntregarClaseDeHoy.EntregaPedida(RESUMEN, "lec-12", 12), disponible(12, "lec-12", true));
        verify(proponerAccion).proponer(APRENDIZ, guardada("lec-12", 12), resumen);
        verify(puerto, never()).entregar(any(), anyString(), anyString());
        assertThat(resumen).contains("suma sus puntos").contains("\"" + RESUMEN + "\"");
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("TODAVIA NO esta hecho");
    }

    @Test
    @DisplayName("proponer: un resumen corto no se propone y se le pide a la persona, no al modelo")
    void noProponeResumenCorto() {
        when(puerto.claseDeHoy(APRENDIZ)).thenReturn(disponible(12, "lec-12", true));

        ResultadoHerramienta resultado = proponer.ejecutar(APRENDIZ, pedido("  muy corto  "));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("9 caracteres")
                .contains("no lo completes tu");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("proponer: sin clase disponible hoy no hay boton")
    void noProponeSinClase() {
        when(puerto.claseDeHoy(APRENDIZ)).thenReturn(new ClaseDeHoy(EstadoClase.PROXIMAMENTE, 40, null, null, null,
                false, 15, 2000));

        assertThat(proponer.ejecutar(APRENDIZ, pedido(RESUMEN))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("confirmar: entrega el resumen guardado y dice los puntos")
    void entrega() {
        when(puerto.claseDeHoy(APRENDIZ)).thenReturn(disponible(12, "lec-12", false));
        when(puerto.entregar(APRENDIZ, "lec-12", RESUMEN)).thenReturn(8);

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, guardada("lec-12", 12));

        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("Puntos otorgados: 8");
    }

    @Test
    @DisplayName("confirmar: si paso la medianoche y la misma leccion es la clase del dia siguiente, no entrega")
    void noEntregaSiCambioElDia() {
        when(puerto.claseDeHoy(APRENDIZ)).thenReturn(disponible(13, "lec-12", false));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, guardada("lec-12", 12));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("cambio el dia");
        verify(puerto, never()).entregar(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("confirmar: el rechazo de academy vuelve legible, sin el mensaje crudo")
    void traduceElRechazo() {
        when(puerto.claseDeHoy(APRENDIZ)).thenReturn(disponible(12, "lec-12", false));
        when(puerto.entregar(APRENDIZ, "lec-12", RESUMEN)).thenThrow(new IllegalStateException("detalle interno"));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, guardada("lec-12", 12));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).doesNotContain("detalle interno")
                .contains("No se pudo");
    }
}
