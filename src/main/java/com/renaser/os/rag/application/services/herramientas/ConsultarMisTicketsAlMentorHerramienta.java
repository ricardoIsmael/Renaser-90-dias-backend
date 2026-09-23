package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.soporte.GestionarTicketsAlMentorPort;
import com.renaser.os.rag.application.ports.out.soporte.GestionarTicketsAlMentorPort.TicketAlMentor;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@code consultar_mis_tickets_al_mentor} (R0, solo lectura, 2026-09-23): los tickets que la persona
 * le abrio a su mentor, si ya le respondio y que le dijo.
 *
 * <p>Solo los propios: {@code support.api.TicketsAlMentor.propios} filtra por quien pregunta, aunque
 * la cuenta sea de mentor. El rol y la cuenta activa los decide {@code support}. El texto de los
 * tickets no se loguea nunca.
 */
@Component
public class ConsultarMisTicketsAlMentorHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_mis_tickets_al_mentor";

    static final int MAXIMO_TICKETS = 5;
    private static final int LARGO_PREGUNTA = 300;
    private static final int LARGO_RESPUESTA = 1500;

    private static final Logger log = LoggerFactory.getLogger(ConsultarMisTicketsAlMentorHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = DefinicionHerramienta.sinParametros(NOMBRE,
            "Devuelve los ultimos tickets que la persona le abrio a su mentor: que bloqueo planteo, si el mentor "
                    + "ya respondio y la respuesta. Usala cuando pregunte si su mentor le contesto o que le dijo; "
                    + "no supongas la respuesta.");

    private final GestionarTicketsAlMentorPort ticketsPort;
    private final Clock clock;

    public ConsultarMisTicketsAlMentorHerramienta(GestionarTicketsAlMentorPort ticketsPort, Clock clock) {
        this.ticketsPort = ticketsPort;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        try {
            return ResultadoHerramienta.exito(texto(ticketsPort.propios(actorId), clock.now()));
        } catch (NoSuchElementException sinCuenta) {
            return ResultadoHerramienta.fallo("No encontre esta cuenta.");
        } catch (NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("No puedo consultar sus tickets: la cuenta esta suspendida o no es "
                    + "de aprendiz.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer los tickets: {}", NOMBRE,
                    falla.getClass().getSimpleName());
            return ResultadoHerramienta.fallo("No pude consultar sus tickets en este momento.");
        }
    }

    static String texto(List<TicketAlMentor> tickets, Instant ahora) {
        if (tickets.isEmpty()) {
            return "No le abrio ningun ticket a su mentor.";
        }
        StringBuilder texto = new StringBuilder("Sus ultimos tickets al mentor, el mas nuevo primero:");
        tickets.stream().limit(MAXIMO_TICKETS).forEach(ticket -> texto.append("\n\n").append(bloque(ticket, ahora)));
        if (tickets.size() > MAXIMO_TICKETS) {
            texto.append("\n\n(tiene mas tickets anteriores; los puede ver en la app)");
        }
        return texto.toString();
    }

    private static String bloque(TicketAlMentor ticket, Instant ahora) {
        String encabezado = "- Abierto " + TiempoTranscurrido.desde(ticket.creadoEn(), ahora) + ". Bloqueo: "
                + TiempoTranscurrido.recortado(ticket.textos().descripcionBloqueo(), LARGO_PREGUNTA);
        if (!ticket.respondido()) {
            return encabezado + "\n  Estado: esperando la respuesta del mentor.";
        }
        String cuando = ticket.respondidoEn() == null ? ""
                : " " + TiempoTranscurrido.desde(ticket.respondidoEn(), ahora);
        return encabezado + "\n  Estado: respondido" + cuando + ". Respuesta del mentor: "
                + TiempoTranscurrido.recortado(ticket.respuestaMentor(), LARGO_RESPUESTA);
    }
}
