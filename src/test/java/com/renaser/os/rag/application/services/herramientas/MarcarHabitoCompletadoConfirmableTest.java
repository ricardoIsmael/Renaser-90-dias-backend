package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La escritura que corre cuando la persona toca "Confirmar" (fase 2, D-153): delega en el
 * {@code completar} de siempre y traduce el rechazo del negocio con los mismos textos que la
 * herramienta con el flag apagado.
 */
class MarcarHabitoCompletadoConfirmableTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID REGISTRO = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final ConsultarAgendaHabitosPort agenda = mock(ConsultarAgendaHabitosPort.class);
    private final MarcarHabitoCompletadoConfirmable confirmable = new MarcarHabitoCompletadoConfirmable(agenda);

    private static InvocacionHerramienta marcar(String registroId) {
        return new InvocacionHerramienta(CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                Map.of(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID, registroId));
    }

    @Test
    @DisplayName("se registra con el nombre de la herramienta cuya propuesta ejecuta")
    void nombre() {
        assertThat(confirmable.herramienta()).isEqualTo(CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO);
    }

    @Test
    @DisplayName("al confirmar, completa el habito y devuelve los puntos otorgados")
    void completa() {
        when(agenda.completar(APRENDIZ, REGISTRO)).thenReturn(6);

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, marcar(REGISTRO.toString()));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.exito(
                "Habito marcado como completado. Puntos otorgados: 6."));
        verify(agenda).completar(APRENDIZ, REGISTRO);
    }

    @Test
    @DisplayName("si entre proponer y confirmar el negocio lo rechaza, vuelve un Fallo legible, no la excepcion")
    void rechazoDelNegocio() {
        when(agenda.completar(APRENDIZ, REGISTRO)).thenThrow(new IllegalStateException("El habito expiro"));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, marcar(REGISTRO.toString()));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("No se pudo marcar ese habito")
                .doesNotContain("IllegalStateException").doesNotContain("expiro");
    }

    @Test
    @DisplayName("una invocacion guardada sin un id valido no llega a completar")
    void idInvalido() {
        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(agenda, never()).completar(any(), any());
    }
}
