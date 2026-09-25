package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * La escritura de {@code marcar_habito_completado}, que solo corre cuando la persona confirma la
 * propuesta con el boton (fase 2, D-153). La propuso {@link PropuestaDeMarcarHabito}.
 *
 * <p>Completa con el mismo puerto y la misma traduccion de errores que la herramienta con el flag
 * apagado ({@link CompletacionDeHabito}): si entre proponer y confirmar el habito vencio o ya se
 * marco desde la pantalla de Hoy, {@code habits} lo rechaza y vuelve un {@code Fallo} legible.
 */
@Component
public class MarcarHabitoCompletadoConfirmable implements AccionConfirmable {

    private final ConsultarAgendaHabitosPort agendaHabitosPort;

    public MarcarHabitoCompletadoConfirmable(ConsultarAgendaHabitosPort agendaHabitosPort) {
        this.agendaHabitosPort = agendaHabitosPort;
    }

    @Override
    public String herramienta() {
        return CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        return CompletacionDeHabito.registroIdDe(invocacion.argumento(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID))
                .map(registroId -> CompletacionDeHabito.completar(agendaHabitosPort, actorId, registroId))
                .orElseGet(CompletacionDeHabito::identificadorInvalido);
    }
}
