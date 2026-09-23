package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort;
import com.renaser.os.rag.application.ports.out.rocas.CerrarSemanaDeRocasPort.ReglasDelCierre;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;
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
 * {@code proponer_cerrar_semana} (R2): valida la forma, verifica con la semana que devuelve
 * {@code rocks} que haya algo que cerrar, y deja una propuesta; nunca escribe.
 *
 * <p>La herramienta no calcula que semana es: la da {@code rocks} en la zona del participante
 * ({@code RocasDelAprendizServiceTest}, con reloj en madrugada UTC). Lo que se prueba aca es que la
 * semana que vio la persona queda ESCRITA en la propuesta.
 */
class ProponerCerrarSemanaHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final ReglasDelCierre REGLAS = new ReglasDelCierre(List.of("CUERPO", "TRABAJO", "RELACIONES"), 1, 10);
    private static final RocaDeLaSemana TRABAJO = new RocaDeLaSemana("TRABAJO", "Cerrar 2 ventas", null, null, false,
            false);
    private static final RocaDeLaSemana CUERPO_CERRADA = new RocaDeLaSemana("CUERPO", "Bajar 1 kg", null, null, false,
            true);
    private static final RocasDeLaSemana SEMANA_3 = new RocasDeLaSemana(3, LocalDate.of(2026, 9, 21),
            LocalDate.of(2026, 9, 27), List.of(CUERPO_CERRADA, TRABAJO));
    private static final String CIERRE_TRABAJO = "{\"ejes\":[{\"eje\":\"trabajo\",\"autoevaluacion\":7,"
            + "\"bloqueoPrincipal\":\" Reuniones \",\"correccion\":\"Bloquear la agenda\"}]}";

    private final ConsultarRocasDelAprendizPort rocas = mock(ConsultarRocasDelAprendizPort.class);
    private final CerrarSemanaDeRocasPort cierre = mock(CerrarSemanaDeRocasPort.class);
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final ProponerCerrarSemanaHerramienta herramienta = new ProponerCerrarSemanaHerramienta(rocas, cierre,
            proponer);

    @BeforeEach
    void reglas() {
        when(cierre.reglas()).thenReturn(REGLAS);
        when(rocas.deLaSemana(APRENDIZ)).thenReturn(SEMANA_3);
    }

    private static InvocacionHerramienta con(String cierre) {
        return new InvocacionHerramienta(ProponerCerrarSemanaHerramienta.NOMBRE,
                Map.of(ProponerCerrarSemanaHerramienta.ARGUMENTO_CIERRE, cierre));
    }

    @Test
    @DisplayName("propone con el resumen exacto por eje y guarda la semana que vio la persona")
    void propone() {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, con(CIERRE_TRABAJO));

        String resumen = "Cerrar la semana 3 (2026-09-21 al 2026-09-27). Trabajo (Cerrar 2 ventas): autoevaluacion "
                + "7/10; bloqueo principal: Reuniones; correccion: Bloquear la agenda. Una vez cerrada, desde el "
                + "chat no se reescribe.";
        verify(proponer).proponer(APRENDIZ, con("{\"semana\":3,\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":7,"
                + "\"bloqueoPrincipal\":\"Reuniones\",\"correccion\":\"Bloquear la agenda\"}]}"), resumen);
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .startsWith("Propuesta creada: " + resumen)
                .contains("no suma puntos")
                .contains("TODAVIA NO esta guardado");
        verify(cierre, never()).cerrarSemana(any(), anyInt(), any());
    }

    @Test
    @DisplayName("un eje ya cerrado no se propone: desde el chat no se reescribe")
    void yaCerrado() {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, con("{\"ejes\":[{\"eje\":\"CUERPO\","
                + "\"autoevaluacion\":5,\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"}]}"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("Cuerpo ya tiene su cierre de la semana 3");
        verifyNoInteractions(proponer);
    }

    @Test
    @DisplayName("un eje sin objetivo esa semana, o una semana sin objetivos: no hay nada que cerrar")
    void sinObjetivo() {
        ResultadoHerramienta sinEje = herramienta.ejecutar(APRENDIZ, con("{\"ejes\":[{\"eje\":\"RELACIONES\","
                + "\"autoevaluacion\":5,\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"}]}"));
        assertThat(((ResultadoHerramienta.Fallo) sinEje).motivo()).contains("no tiene objetivo de Relaciones");

        when(rocas.deLaSemana(APRENDIZ)).thenReturn(new RocasDeLaSemana(3, LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 27), List.of()));
        ResultadoHerramienta vacia = herramienta.ejecutar(APRENDIZ, con(CIERRE_TRABAJO));
        assertThat(((ResultadoHerramienta.Fallo) vacia).motivo()).contains("no tiene objetivos semanales");
        verifyNoInteractions(proponer);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":11,\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"}]}",
            "{\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":0,\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"}]}",
            "{\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":\"7\",\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"}]}",
            "{\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":7.5,\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"}]}",
            "{\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":7,\"correccion\":\"y\"}]}",
            "{\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":7,\"bloqueoPrincipal\":\"x\",\"correccion\":\"  \"}]}",
            "{\"ejes\":[{\"eje\":\"NEGOCIO\",\"autoevaluacion\":7,\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"}]}",
            "{\"semana\":2,\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":7,\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"}]}",
            "{\"ejes\":[{\"eje\":\"TRABAJO\",\"autoevaluacion\":7,\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"},"
                    + "{\"eje\":\"trabajo\",\"autoevaluacion\":6,\"bloqueoPrincipal\":\"x\",\"correccion\":\"y\"}]}"
    })
    @DisplayName("forma invalida (escala, obligatorios, eje, semana elegida por el modelo, eje repetido): Fallo sin propuesta")
    void formaInvalida(String cierreInvalido) {
        assertThat(herramienta.ejecutar(APRENDIZ, con(cierreInvalido))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponer);
    }

    @Test
    @DisplayName("cuenta sin acceso a rocas: Fallo legible y sin propuesta")
    void sinAcceso() {
        when(rocas.deLaSemana(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        assertThat(herramienta.ejecutar(APRENDIZ, con(CIERRE_TRABAJO))).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponer);
    }

    @Test
    @DisplayName("si no se puede guardar la propuesta, Fallo legible y no se dice 'propuesta creada'")
    void falloAlGuardarLaPropuesta() {
        when(proponer.proponer(any(), any(), anyString())).thenThrow(new IllegalStateException("base caida"));

        assertThat(herramienta.ejecutar(APRENDIZ, con(CIERRE_TRABAJO)))
                .isEqualTo(ResultadoHerramienta.fallo("No pude preparar la confirmacion en este momento."));
    }
}
