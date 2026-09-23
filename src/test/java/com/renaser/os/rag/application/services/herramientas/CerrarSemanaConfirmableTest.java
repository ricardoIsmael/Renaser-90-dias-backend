package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.Motivo;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.ReglasDelCierre;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.ResultadoCierre;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.RevisionDelEje;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La escritura de {@code proponer_cerrar_semana}: cierra la semana GUARDADA en la propuesta, via {@code rocks}. */
class CerrarSemanaConfirmableTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final String GUARDADO = "{\"semana\":3,\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":7,"
            + "\"bloqueoPrincipal\":\"Reuniones\",\"correccion\":\"Bloquear la agenda\"}]}";
    private static final List<RevisionDelEje> REVISIONES = List.of(
            new RevisionDelEje("TRABAJO", 7, "Reuniones", "Bloquear la agenda"));

    private final CerrarSemanaDeRocasPort cierre = mock(CerrarSemanaDeRocasPort.class);
    private final CerrarSemanaConfirmable confirmable = new CerrarSemanaConfirmable(cierre);

    @BeforeEach
    void reglas() {
        when(cierre.reglas()).thenReturn(new ReglasDelCierre(List.of("CUERPO", "TRABAJO", "RELACIONES"), 1, 10));
    }

    private static InvocacionHerramienta invocacion(String cierre) {
        return new InvocacionHerramienta(ProponerCerrarSemanaHerramienta.NOMBRE,
                Map.of(ProponerCerrarSemanaHerramienta.ARGUMENTO_CIERRE, cierre));
    }

    @Test
    @DisplayName("se registra con el nombre de la herramienta cuya propuesta ejecuta")
    void nombre() {
        assertThat(confirmable.herramienta()).isEqualTo("proponer_cerrar_semana");
    }

    @Test
    @DisplayName("cierra la semana guardada (no 'la de hoy' al confirmar) con las revisiones guardadas")
    void cierraLaSemanaGuardada() {
        when(cierre.cerrarSemana(APRENDIZ, 3, REVISIONES)).thenReturn(new ResultadoCierre.Cerrada(List.of("TRABAJO")));

        assertThat(confirmable.aplicar(APRENDIZ, invocacion(GUARDADO)))
                .isEqualTo(ResultadoHerramienta.exito("Semana 3 cerrada: Trabajo."));
        verify(cierre).cerrarSemana(APRENDIZ, 3, REVISIONES);
    }

    @Test
    @DisplayName("si entre proponer y confirmar la cerro en la app, Fallo legible: no se pisa")
    void yaRevisada() {
        when(cierre.cerrarSemana(any(), anyInt(), any())).thenReturn(new ResultadoCierre.Rechazado(Motivo.YA_REVISADA));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, invocacion(GUARDADO));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("desde el chat no se reescribe");
    }

    @Test
    @DisplayName("una propuesta sin semana no se ejecuta: no se adivina la semana al confirmar")
    void sinSemanaNoSeEjecuta() {
        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, invocacion("{\"ejes\":[{\"eje\":\"TRABAJO\","
                + "\"autoevaluacion\":7,\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"}]}"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(cierre, never()).cerrarSemana(any(), anyInt(), any());
    }

    @Test
    @DisplayName("un error que no es rechazo tampoco sube: Fallo legible")
    void errorInesperado() {
        when(cierre.cerrarSemana(any(), anyInt(), any())).thenThrow(new IllegalStateException("base caida"));

        assertThat(confirmable.aplicar(APRENDIZ, invocacion(GUARDADO)))
                .isEqualTo(ResultadoHerramienta.fallo("No pude guardar el cierre de la semana en este momento."));
    }
}
