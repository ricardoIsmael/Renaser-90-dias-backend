package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.soporte.GestionarTicketsAlMentorPort;
import com.renaser.os.rag.application.ports.out.soporte.GestionarTicketsAlMentorPort.TextosDelTicket;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * La escritura de {@code proponer_ticket_al_mentor}, que solo corre cuando la persona confirma con el
 * boton. La propuso {@link PropuestaDeTicketAlMentor}; envia los tres textos guardados en la
 * propuesta, exactamente los que vio la persona.
 *
 * <p>Delega en {@code AbrirTicketMentorUseCase} (via {@code support.api}), que exige el rol TRAINEE,
 * la cuenta activa y los largos. Un rechazo vuelve como {@code Fallo} legible. Los textos nunca se
 * loguean: del rechazo se registra solo su tipo.
 *
 * <p>Sin condicion de flag: una propuesta ya guardada tiene que poder confirmarse aunque el flag se
 * apague despues.
 */
@Component
public class AbrirTicketAlMentorConfirmable implements AccionConfirmable {

    private static final Logger log = LoggerFactory.getLogger(AbrirTicketAlMentorConfirmable.class);

    private final GestionarTicketsAlMentorPort ticketsPort;

    public AbrirTicketAlMentorConfirmable(GestionarTicketsAlMentorPort ticketsPort) {
        this.ticketsPort = ticketsPort;
    }

    @Override
    public String herramienta() {
        return PropuestaDeTicketAlMentor.NOMBRE;
    }

    @Override
    public ResultadoHerramienta aplicar(UserId actorId, InvocacionHerramienta invocacion) {
        TextosDelTicket textos = PropuestaDeTicketAlMentor.textosDe(invocacion);
        if (textos.descripcionBloqueo().isEmpty() || textos.solucionesIntentadas().isEmpty()
                || textos.impactoMetaSmart().isEmpty()) {
            return ResultadoHerramienta.fallo("La propuesta no trae los tres textos: no se envio nada.");
        }
        try {
            ticketsPort.abrir(actorId, textos);
        } catch (RuntimeException rechazo) {
            return traducir(rechazo);
        }
        return ResultadoHerramienta.exito("Ticket enviado a tu mentor. Cuando responda, la respuesta aparece en la "
                + "app y tambien me la puedes pedir.");
    }

    private ResultadoHerramienta traducir(RuntimeException rechazo) {
        log.info("[rag] {} no pudo abrir el ticket: {}", herramienta(), rechazo.getClass().getSimpleName());
        return ResultadoHerramienta.fallo(switch (rechazo) {
            case NotAuthorizedException sinPermiso ->
                    "Solo una cuenta de aprendiz activa puede abrirle un ticket a su mentor: no se envio nada.";
            case NoSuchElementException sinCuenta -> "No encontre esta cuenta: no se envio nada.";
            default -> "No se pudo enviar el ticket en este momento: no se envio nada.";
        });
    }
}
