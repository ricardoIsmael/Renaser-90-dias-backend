package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.agenda.AgendaSemanalPort;
import com.renaser.os.rag.domain.model.agenda.AgendaSemanal;
import com.renaser.os.rag.domain.model.agenda.DiasDeSemana;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aplica {@code proponer_guardar_agenda} cuando la persona toca Confirmar (D-161). Relee la agenda
 * en ese momento y reemplaza solo los dias propuestos: si entre proponer y confirmar cambio otro
 * dia, ese cambio se respeta.
 */
@Component
public class GuardarAgendaConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(GuardarAgendaConfirmable.class);

    private final AgendaSemanalPort agendaPort;

    public GuardarAgendaConfirmable(AgendaSemanalPort agendaPort) {
        this.agendaPort = agendaPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeGuardarAgenda.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        AgendaSemanal nueva;
        try {
            nueva = agendaPort.de(actorId).conDias(
                    DiasDeSemana.leer(invocacion.argumento(PropuestaDeGuardarAgenda.ARGUMENTO_DIAS)),
                    invocacion.argumento(PropuestaDeGuardarAgenda.ARGUMENTO_OCUPADO));
        } catch (IllegalArgumentException mal) {
            return ResultadoHerramienta.fallo("La propuesta no trae una agenda valida: no se guardo nada.");
        }
        try {
            agendaPort.guardar(actorId, nueva);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la agenda ({})", falla.getClass().getSimpleName());
            return ResultadoHerramienta.fallo("No se pudo guardar en este momento: no cambio nada.");
        }
        return ResultadoHerramienta.exito("Listo, quedo guardado. Horas ocupadas: " + nueva.texto() + ".");
    }
}
