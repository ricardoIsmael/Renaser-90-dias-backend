package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** La escritura de {@code proponer_renombrar_habito} (D-236), que solo corre al confirmar. */
class RenombrarHabitoConfirmableTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID JUGO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final RenombrarHabitoConfirmable confirmable = new RenombrarHabitoConfirmable(planPort);

    private static InvocacionHerramienta renombrar(String nombre, String motivo) {
        return new InvocacionHerramienta(PropuestaDeRenombrarHabito.NOMBRE, Map.of(
                PropuestaDeRenombrarHabito.ARGUMENTO_HABITO_ID, JUGO.toString(),
                PropuestaDeRenombrarHabito.ARGUMENTO_ACCION, "renombrar",
                PropuestaDeRenombrarHabito.ARGUMENTO_NOMBRE, nombre,
                PropuestaDeRenombrarHabito.ARGUMENTO_MOTIVO, motivo));
    }

    @Test
    @DisplayName("es la escritura de proponer_renombrar_habito")
    void herramienta() {
        assertThat(confirmable.herramienta()).isEqualTo("proponer_renombrar_habito");
    }

    @Test
    @DisplayName("renombra con el nombre y el motivo guardados, y vuelve al original")
    void renombraYVuelve() {
        ResultadoHerramienta hecho = confirmable.aplicar(APRENDIZ, renombrar("Batido de papaya", "gastritis"));
        verify(planPort).renombrar(APRENDIZ, JUGO, "Batido de papaya", "gastritis");
        assertThat(((ResultadoHerramienta.Exito) hecho).contenido()).contains("'Batido de papaya'");

        ResultadoHerramienta original = confirmable.aplicar(APRENDIZ, new InvocacionHerramienta(
                PropuestaDeRenombrarHabito.NOMBRE, Map.of(PropuestaDeRenombrarHabito.ARGUMENTO_HABITO_ID,
                JUGO.toString(), PropuestaDeRenombrarHabito.ARGUMENTO_ACCION, "volver_al_original")));
        verify(planPort).quitarRenombre(APRENDIZ, JUGO);
        assertThat(((ResultadoHerramienta.Exito) original).contenido()).contains("nombre del programa");
    }

    @Test
    @DisplayName("un rechazo de habits vuelve como fallo legible, nunca como excepcion")
    void rechazosLegibles() {
        doThrow(new IllegalArgumentException("Este habito no se puede reemplazar"))
                .when(planPort).renombrar(any(), any(), any(), any());
        assertThat(((ResultadoHerramienta.Fallo) confirmable.aplicar(APRENDIZ, renombrar("B", "m"))).motivo())
                .contains("no se le puede cambiar el nombre");

        doThrow(new NotAuthorizedException("Cuenta suspendida")).when(planPort).quitarRenombre(any(), any());
        ResultadoHerramienta suspendida = confirmable.aplicar(APRENDIZ, new InvocacionHerramienta(
                PropuestaDeRenombrarHabito.NOMBRE, Map.of(PropuestaDeRenombrarHabito.ARGUMENTO_HABITO_ID,
                JUGO.toString(), PropuestaDeRenombrarHabito.ARGUMENTO_ACCION, "volver_al_original")));
        assertThat(((ResultadoHerramienta.Fallo) suspendida).motivo()).contains("suspendida");
    }

    @Test
    @DisplayName("una propuesta con datos invalidos no escribe")
    void invalidaNoEscribe() {
        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, renombrar("x".repeat(61), "m"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(planPort);
    }
}
