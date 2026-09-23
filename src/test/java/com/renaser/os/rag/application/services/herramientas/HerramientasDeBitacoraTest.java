package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.BitacoraDeHoy;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
 * {@code consultar_bitacora_de_hoy}, {@code proponer_bitacora_de_hoy} y su confirmacion. "Hoy" lo
 * da {@code habits} en la zona del participante; aca se verifica que se respete y que nunca se
 * escriba la bitacora de un dia distinto al propuesto.
 */
class HerramientasDeBitacoraTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    /** Miercoles: el "hoy" en la zona del aprendiz que devuelve {@code habits}. */
    private static final LocalDate HOY = LocalDate.of(2026, 9, 23);

    private final DiarioYRadarDelAprendizPort diarioPort = mock(DiarioYRadarDelAprendizPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);
    private final ConsultarBitacoraDeHoyHerramienta consultar = new ConsultarBitacoraDeHoyHerramienta(diarioPort);
    private final PropuestaDeBitacoraDeHoy proponer = new PropuestaDeBitacoraDeHoy(diarioPort, proponerAccion);
    private final BitacoraDeHoyConfirmable confirmable = new BitacoraDeHoyConfirmable(diarioPort);

    private static InvocacionHerramienta pedido(String texto) {
        return new InvocacionHerramienta(PropuestaDeBitacoraDeHoy.NOMBRE, Map.of("texto", texto));
    }

    private static InvocacionHerramienta guardada(String texto, LocalDate fecha) {
        return new InvocacionHerramienta(PropuestaDeBitacoraDeHoy.NOMBRE, Map.of("texto", texto,
                "fecha", fecha.toString()));
    }

    @Test
    @DisplayName("consultar dice si existe y muestra el texto")
    void consultar() {
        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenReturn(new BitacoraDeHoy(HOY, false, null, false));
        assertThat(((ResultadoHerramienta.Exito) consultar.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarBitacoraDeHoyHerramienta.NOMBRE))).contenido())
                .contains("miércoles 23/09").contains("todavia no la escribio");

        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenReturn(new BitacoraDeHoy(HOY, true, "Hoy aprendi algo", true));
        assertThat(((ResultadoHerramienta.Exito) consultar.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarBitacoraDeHoyHerramienta.NOMBRE))).contenido())
                .contains("ya la escribio").contains("Hoy aprendi algo").contains("audio");
    }

    @Test
    @DisplayName("una cuenta suspendida vuelve como Fallo legible")
    void consultarSuspendida() {
        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        assertThat(((ResultadoHerramienta.Fallo) consultar.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarBitacoraDeHoyHerramienta.NOMBRE))).motivo())
                .contains("suspendida");
    }

    @Test
    @DisplayName("sin bitacora hoy, propone escribirla con el texto completo y la fecha de hoy, sin escribir")
    void proponeNueva() {
        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenReturn(new BitacoraDeHoy(HOY, false, null, false));

        ResultadoHerramienta resultado = proponer.ejecutar(APRENDIZ, pedido("  Hoy me costo levantarme.  "));

        String resumen = "Escribir tu Bitacora Nocturna de hoy (miércoles 23/09): «Hoy me costo levantarme.»";
        verify(proponerAccion).proponer(APRENDIZ, guardada("Hoy me costo levantarme.", HOY), resumen);
        verify(diarioPort, never()).escribirBitacoraDeHoy(any(), any());
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("TODAVIA NO esta hecho")
                .doesNotContain("REEMPLAZA");
    }

    @Test
    @DisplayName("si ya hay una, el resumen dice que la reemplaza y muestra lo que hay; el audio se conserva")
    void proponeReemplazo() {
        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenReturn(new BitacoraDeHoy(HOY, true, "texto viejo", true));

        ResultadoHerramienta resultado = proponer.ejecutar(APRENDIZ, pedido("texto nuevo"));

        verify(proponerAccion).proponer(APRENDIZ, guardada("texto nuevo", HOY),
                "Reemplazar tu Bitacora Nocturna de hoy (miércoles 23/09). Ahora dice: «texto viejo». "
                        + "Quedaria: «texto nuevo». Tu audio se conserva.");
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("REEMPLAZA");
    }

    @Test
    @DisplayName("el resumen recorta un texto largo, pero la propuesta guarda el texto completo")
    void textoLargo() {
        String largo = "a".repeat(1_000);
        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenReturn(new BitacoraDeHoy(HOY, false, null, false));

        proponer.ejecutar(APRENDIZ, pedido(largo));

        verify(proponerAccion).proponer(APRENDIZ, guardada(largo, HOY),
                "Escribir tu Bitacora Nocturna de hoy (miércoles 23/09): «" + "a".repeat(280) + "…»");
    }

    @Test
    @DisplayName("texto vacio, texto identico al de hoy o cuenta suspendida no generan propuesta")
    void noPropone() {
        assertThat(proponer.ejecutar(APRENDIZ, pedido("   "))).isInstanceOf(ResultadoHerramienta.Fallo.class);

        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenReturn(new BitacoraDeHoy(HOY, true, "igual", false));
        assertThat(proponer.ejecutar(APRENDIZ, pedido(" igual "))).isInstanceOf(ResultadoHerramienta.Fallo.class);

        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        assertThat(((ResultadoHerramienta.Fallo) proponer.ejecutar(APRENDIZ, pedido("algo"))).motivo())
                .contains("suspendida");

        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("al confirmar el mismo dia, escribe el texto completo guardado")
    void confirmaMismoDia() {
        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenReturn(new BitacoraDeHoy(HOY, false, null, false));
        when(diarioPort.escribirBitacoraDeHoy(APRENDIZ, "mi texto"))
                .thenReturn(new BitacoraDeHoy(HOY, true, "mi texto", false));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, guardada("mi texto", HOY));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.exito("Bitacora Nocturna del miércoles 23/09 guardada."));
        assertThat(confirmable.herramienta()).isEqualTo(PropuestaDeBitacoraDeHoy.NOMBRE);
    }

    @Test
    @DisplayName("si al confirmar ya paso la medianoche del participante, no escribe la del dia siguiente")
    void confirmaDespuesDeMedianoche() {
        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenReturn(new BitacoraDeHoy(HOY.plusDays(1), false, null, false));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, guardada("mi texto", HOY));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("Ya cambio el dia");
        verify(diarioPort, never()).escribirBitacoraDeHoy(any(), anyString());
    }

    @Test
    @DisplayName("una propuesta guardada sin fecha o un rechazo de habits vuelven como Fallo legible")
    void confirmaInvalidaORechazada() {
        assertThat(confirmable.aplicar(APRENDIZ, pedido("sin fecha"))).isInstanceOf(ResultadoHerramienta.Fallo.class);

        when(diarioPort.bitacoraDeHoy(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        assertThat(((ResultadoHerramienta.Fallo) confirmable.aplicar(APRENDIZ, guardada("x", HOY))).motivo())
                .contains("suspendida").doesNotContain("NotAuthorized");
    }

    @Test
    @DisplayName("la descripcion le pide usar las palabras de la persona y no decir que ya quedo guardada")
    void descripcion() {
        assertThat(proponer.definicion().nombre()).isEqualTo("proponer_bitacora_de_hoy");
        assertThat(proponer.definicion().descripcion()).startsWith("Propone").contains("Confirmar")
                .contains("Nunca digas").contains("SUS palabras").contains("REEMPLAZA");
    }
}
