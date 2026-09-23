package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.CheckInRadar;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.CheckInRadarRegistrado;
import com.renaser.os.rag.application.ports.out.diario.DiarioYRadarDelAprendizPort.RespuestasRadar;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code consultar_ultimo_radar}, {@code proponer_check_in_radar} y su confirmacion. La hora local
 * y el "uno por hora" los resuelve {@code habits}; aca se verifica que se muestren y se respeten.
 */
class HerramientasDeRadarTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    /** Las 22:10 del 23 en Lima: son las 03:10 UTC del 24, y habits ya lo entrega en hora local. */
    private static final LocalDateTime ANOCHE_EN_LIMA = LocalDateTime.of(2026, 9, 23, 22, 10);
    private static final RespuestasRadar RESPUESTAS = new RespuestasRadar("Estudio", "Que no llego", "Cansancio", 4,
            "Llamar a mi jefe");

    private final DiarioYRadarDelAprendizPort radarPort = mock(DiarioYRadarDelAprendizPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);
    private final ConsultarUltimoRadarHerramienta consultar = new ConsultarUltimoRadarHerramienta(radarPort);
    private final PropuestaDeCheckInRadar proponer = new PropuestaDeCheckInRadar(radarPort, proponerAccion);
    private final CheckInRadarConfirmable confirmable = new CheckInRadarConfirmable(radarPort);

    private static Map<String, String> argumentos(String hago, String energia) {
        Map<String, String> argumentos = new HashMap<>();
        argumentos.put("que_hago", hago);
        argumentos.put("que_pienso", "Que no llego");
        argumentos.put("que_siento", "Cansancio");
        argumentos.put("nivel_energia", energia);
        argumentos.put("que_evito", "Llamar a mi jefe");
        return argumentos;
    }

    private static InvocacionHerramienta invocacion(String hago, String energia) {
        return new InvocacionHerramienta(PropuestaDeCheckInRadar.NOMBRE, argumentos(hago, energia));
    }

    @Test
    @DisplayName("consultar muestra la hora local, las respuestas y si ya ocupa la hora en curso")
    void consultar() {
        when(radarPort.ultimoCheckInRadar(APRENDIZ))
                .thenReturn(Optional.of(new CheckInRadar(ANOCHE_EN_LIMA, true, RESPUESTAS)));

        String texto = ((ResultadoHerramienta.Exito) consultar.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarUltimoRadarHerramienta.NOMBRE))).contenido();

        assertThat(texto).contains("miércoles 23/09 a las 22:10").contains("Ya registro el de esta hora")
                .contains("Energia: 4/10").contains("Llamar a mi jefe");
    }

    @Test
    @DisplayName("sin ninguno lo dice; sin acceso, Fallo legible")
    void consultarVacioOSinAcceso() {
        when(radarPort.ultimoCheckInRadar(APRENDIZ)).thenReturn(Optional.empty());
        assertThat(consultar.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(ConsultarUltimoRadarHerramienta.NOMBRE)))
                .isEqualTo(ResultadoHerramienta.exito("Todavia no registro ningun Codigo Renaser."));

        when(radarPort.ultimoCheckInRadar(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        assertThat(((ResultadoHerramienta.Fallo) consultar.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarUltimoRadarHerramienta.NOMBRE))).motivo())
                .contains("suspendida");
    }

    @Test
    @DisplayName("propone con las respuestas normalizadas y el aviso de uno por hora, sin registrar")
    void propone() {
        when(radarPort.ultimoCheckInRadar(APRENDIZ))
                .thenReturn(Optional.of(new CheckInRadar(ANOCHE_EN_LIMA, false, RESPUESTAS)));

        ResultadoHerramienta resultado = proponer.ejecutar(APRENDIZ, invocacion("  Estudio ", " 4 "));

        verify(proponerAccion).proponer(APRENDIZ, invocacion("Estudio", "4"),
                "Registrar tu Codigo Renaser de ahora. Hago: «Estudio» · Pienso: «Que no llego» · Siento: "
                        + "«Cansancio» · Energia: 4/10 · Evito: «Llamar a mi jefe». Es uno por hora: si antes de "
                        + "confirmar ya registraste otro en esta misma hora, se conserva ese y este no se guarda.");
        verify(radarPort, never()).registrarCheckInRadar(any(), any());
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido()).contains("TODAVIA NO esta hecho");
    }

    @Test
    @DisplayName("una energia '4.0' (como suele mandar los enteros un modelo) se guarda como 4; '4.5' no")
    void energiaComoDecimal() {
        when(radarPort.ultimoCheckInRadar(APRENDIZ)).thenReturn(Optional.empty());

        proponer.ejecutar(APRENDIZ, invocacion("Estudio", "4.0"));
        verify(proponerAccion).proponer(any(), eq(invocacion("Estudio", "4")), any());

        assertThat(proponer.ejecutar(APRENDIZ, invocacion("Estudio", "4.5")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
    }

    @Test
    @DisplayName("si ya registro el de esta hora, no ofrece un boton que no guardaria nada")
    void franjaOcupada() {
        when(radarPort.ultimoCheckInRadar(APRENDIZ))
                .thenReturn(Optional.of(new CheckInRadar(ANOCHE_EN_LIMA, true, RESPUESTAS)));

        ResultadoHerramienta resultado = proponer.ejecutar(APRENDIZ, invocacion("Estudio", "4"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("a las 22:10").contains("uno por hora");
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("respuestas vacias, demasiado largas o energia fuera de 1..10 se rechazan antes de leer nada")
    void respuestasInvalidas() {
        assertThat(((ResultadoHerramienta.Fallo) proponer.ejecutar(APRENDIZ, invocacion(" ", "4"))).motivo())
                .contains("no la completes");
        assertThat(proponer.ejecutar(APRENDIZ, invocacion("x".repeat(2_001), "4")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(proponer.ejecutar(APRENDIZ, invocacion("Estudio", "11")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(proponer.ejecutar(APRENDIZ, invocacion("Estudio", "cuatro")))
                .isInstanceOf(ResultadoHerramienta.Fallo.class);

        verifyNoInteractions(radarPort, proponerAccion);
    }

    @Test
    @DisplayName("una cuenta sin acceso al radar no genera propuesta")
    void sinAcceso() {
        when(radarPort.ultimoCheckInRadar(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThat(proponer.ejecutar(APRENDIZ, invocacion("Estudio", "4"))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponerAccion);
    }

    @Test
    @DisplayName("al confirmar registra las respuestas guardadas y da la hora local")
    void confirma() {
        when(radarPort.registrarCheckInRadar(APRENDIZ, RESPUESTAS))
                .thenReturn(new CheckInRadarRegistrado(ANOCHE_EN_LIMA, false));

        assertThat(confirmable.aplicar(APRENDIZ, invocacion("Estudio", "4")))
                .isEqualTo(ResultadoHerramienta.exito("Codigo Renaser registrado a las 22:10."));
        assertThat(confirmable.herramienta()).isEqualTo(PropuestaDeCheckInRadar.NOMBRE);
    }

    @Test
    @DisplayName("si habits devolvio el que ya habia en esta hora, es Fallo: estas respuestas no se guardaron")
    void confirmaConFranjaOcupada() {
        when(radarPort.registrarCheckInRadar(APRENDIZ, RESPUESTAS))
                .thenReturn(new CheckInRadarRegistrado(ANOCHE_EN_LIMA, true));

        assertThat(((ResultadoHerramienta.Fallo) confirmable.aplicar(APRENDIZ, invocacion("Estudio", "4"))).motivo())
                .contains("no se guardaron");
    }

    @Test
    @DisplayName("una propuesta guardada invalida o un rechazo de habits vuelven como Fallo legible")
    void confirmaInvalidaORechazada() {
        assertThat(confirmable.aplicar(APRENDIZ, invocacion("Estudio", "0"))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(radarPort, never()).registrarCheckInRadar(any(), any());

        when(radarPort.registrarCheckInRadar(APRENDIZ, RESPUESTAS))
                .thenThrow(new NotAuthorizedException("El Codigo Renaser es exclusivo de aprendices"));
        assertThat(((ResultadoHerramienta.Fallo) confirmable.aplicar(APRENDIZ, invocacion("Estudio", "4"))).motivo())
                .contains("habilitado").doesNotContain("exclusivo");
    }

    @Test
    @DisplayName("la descripcion exige las respuestas de la persona y no decir que ya quedo registrado")
    void descripcion() {
        assertThat(proponer.definicion().nombre()).isEqualTo("proponer_check_in_radar");
        assertThat(proponer.definicion().descripcion()).startsWith("Propone").contains("Confirmar")
                .contains("Nunca digas").contains("SUYAS").contains("no inventes");
        assertThat(proponer.definicion().parametros()).hasSize(5);
    }
}
