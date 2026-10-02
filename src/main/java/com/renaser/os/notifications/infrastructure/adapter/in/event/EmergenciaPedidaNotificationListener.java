package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.chat.api.SoporteDelAprendizFinder;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EmergenciaPedidaEvent;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Avisa a quienes atienden soporte que un aprendiz pidió ayuda por una emergencia (D-244).
 *
 * <p><b>A quién:</b> ADMIN y ALCHEMIST activos, los mismos que están en cada chat de soporte
 * ({@code ConversacionSoporteService.STAFF_ADMINISTRATIVO}, confirmado por el dueño). A todos y no a uno, como
 * {@code PatronDeMalestarNotificationListener}: no hay un «dueño» del soporte.
 *
 * <p><b>Con qué tipo:</b> {@code TICKET_ABIERTO} — es un pedido que alguien abrió y espera respuesta. Así no
 * hace falta un valor nuevo del enum de Postgres, se ve en la campana y no depende del interruptor
 * «Mensajes» del chat. Tocarlo abre el chat de soporte de esa persona, donde está el mensaje y el acceso a
 * «Cambiar día del programa».
 *
 * <p><b>Qué dice:</b> quién, a qué día y en cuál está. NO lo que escribió: el push sale en la pantalla
 * bloqueada, y eso se lee en el chat.
 *
 * <p>El id del pedido es el {@code origenEventoId}: el índice único de {@code notificaciones} descarta la
 * segunda entrega del outbox (C-7).
 */
@Component
class EmergenciaPedidaNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EmergenciaPedidaNotificationListener.class);

    private static final Set<UserRole> QUIENES_ATIENDEN_SOPORTE = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST);
    static final String TITULO = "Emergencia de un aprendiz";
    private static final String QUIEN_SIN_NOMBRE = "Un aprendiz";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;
    private final ParticipacionProgramaFinder participacionFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final SoporteDelAprendizFinder soporteFinder;

    EmergenciaPedidaNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase,
                                         ParticipacionProgramaFinder participacionFinder,
                                         UserSummaryFinder userSummaryFinder, SoporteDelAprendizFinder soporteFinder) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
        this.participacionFinder = participacionFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.soporteFinder = soporteFinder;
    }

    @ApplicationModuleListener
    void on(EmergenciaPedidaEvent pedido) {
        List<UserId> quienesAtienden = participacionFinder.usuariosActivosConRol(QUIENES_ATIENDEN_SOPORTE);
        if (quienesAtienden.isEmpty()) {
            log.warn("[notifications.Emergencia] el pedido {} no tiene a quién avisar: no hay ADMIN ni ALCHEMIST activos",
                    pedido.solicitudId());
            return;
        }
        String ruta = soporteFinder.conversacionDeSoporteDe(pedido.aprendizId()).map(id -> "/chat/" + id).orElse(null);
        String cuerpo = cuerpo(pedido);
        for (UserId destinatario : quienesAtienden) {
            emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(destinatario, TipoNotificacion.TICKET_ABIERTO,
                    TITULO, cuerpo, ruta, pedido.solicitudId()));
        }
    }

    private String cuerpo(EmergenciaPedidaEvent pedido) {
        String quien = userSummaryFinder.findById(pedido.aprendizId())
                .map(UserSummary::fullName)
                .filter(nombre -> !nombre.isBlank())
                .orElse(QUIEN_SIN_NOMBRE);
        return quien + " pide volver al día " + pedido.diaPedido() + " (hoy está en el día " + pedido.diaAlPedir()
                + "). Te espera en su chat de soporte.";
    }
}
