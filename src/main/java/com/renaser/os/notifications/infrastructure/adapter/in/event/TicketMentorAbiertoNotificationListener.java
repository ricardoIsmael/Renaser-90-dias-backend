package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.TicketMentorAbiertoEvent;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Le avisa al mentor asignado que un aprendiz suyo le abrio un ticket (E-217: hasta aca el evento
 * se publicaba y nadie lo escuchaba, y el mentor solo se enteraba si entraba a su bandeja).
 *
 * <p><b>A quien.</b> Al {@code mentorId} de la participacion del aprendiz, que es el mismo criterio
 * con el que {@code support} decide quien puede responder ese ticket. Sin mentor asignado no se
 * avisa a nadie: elegir otro destinatario (ADMIN, el lider de celula) es una decision de producto
 * que no esta tomada, y se registra en INFO para que el caso quede a la vista.
 *
 * <p><b>Que dice.</b> Solo quien lo abrio. Nunca el texto del bloqueo: es contenido personal, y el
 * mismo cuerpo sale por push a la pantalla bloqueada del mentor. El detalle se lee dentro de la
 * app, contra un endpoint que revalida que siga siendo su mentor.
 *
 * <p>El id del ticket viaja como {@code origenEventoId}: el indice unico de {@code notificaciones}
 * descarta la segunda entrega del outbox de Modulith, que es at-least-once (C-7).
 */
@Component
class TicketMentorAbiertoNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(TicketMentorAbiertoNotificationListener.class);

    private static final String TITULO = "Nuevo ticket de un aprendiz";
    private static final String QUIEN_SIN_NOMBRE = "Un aprendiz";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;
    private final ParticipacionProgramaFinder participacionFinder;
    private final UserSummaryFinder userSummaryFinder;

    TicketMentorAbiertoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase,
                                             ParticipacionProgramaFinder participacionFinder,
                                             UserSummaryFinder userSummaryFinder) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
        this.participacionFinder = participacionFinder;
        this.userSummaryFinder = userSummaryFinder;
    }

    @ApplicationModuleListener
    void on(TicketMentorAbiertoEvent event) {
        Optional<UserId> mentor = mentorAsignado(event.participanteId());
        if (mentor.isEmpty()) {
            log.info("[notifications.TicketMentorAbiertoNotificationListener] ticket {} abierto por un aprendiz "
                    + "sin mentor asignado: no se avisa a nadie", event.ticketId());
            return;
        }
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(mentor.get(), TipoNotificacion.TICKET_ABIERTO,
                TITULO, cuerpo(event.participanteId()), null, event.ticketId()));
    }

    private Optional<UserId> mentorAsignado(UserId participanteId) {
        return participacionFinder.deParticipante(participanteId).map(ParticipacionPrograma::mentorId);
    }

    private String cuerpo(UserId participanteId) {
        String quien = userSummaryFinder.findById(participanteId)
                .map(UserSummary::fullName)
                .filter(nombre -> !nombre.isBlank())
                .orElse(QUIEN_SIN_NOMBRE);
        return quien + " te abrió un ticket y espera tu respuesta.";
    }
}
