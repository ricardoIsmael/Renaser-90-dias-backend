package com.renaser.os.notifications.application.services;

import com.renaser.os.chat.api.RecordatorioDeEventoEnElChat;
import com.renaser.os.notifications.application.ports.in.evento.AvisoDeMensajeDelEvento;
import com.renaser.os.notifications.application.ports.in.evento.RecordarEventoEnElChatUseCase;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.domain.model.evento.VeredictoDelMensaje;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * El recordatorio de un evento en el chat de soporte de la persona (spec 041, FR-001 a FR-006). {@code notifications} decide A QUIEN y CUANDO sirve;
 * {@code chat} escribe el mensaje del programa, una sola vez por fila de la cola, firmado con el nombre de la formacion de esa persona.
 *
 * <p>La preferencia manda (FR-003): quien apago {@code RECORDATORIO_EVENTO} en Yo → Notificaciones no recibe el aviso ni este mensaje. Las cuentas
 * suspendidas y las de prueba tampoco. Que la persona no tenga chat de soporte (el personal) no es un error: recibe el aviso de la app y nada mas.
 * Si el mensaje llega con el evento ya empezado o con mas de 15 minutos de atraso, no se escribe.
 */
@Service
public class RecordatorioDeEventoEnElChatService implements RecordarEventoEnElChatUseCase {

    static final String CLAVE_DE_EVENTOS = "eventos";
    private static final Logger log = LoggerFactory.getLogger(RecordatorioDeEventoEnElChatService.class);

    private final DestinatarioDelMensajeDeEvento destinatarios;
    private final LoadPreferenciasPort preferencias;
    private final RecordatorioDeEventoEnElChat chat;
    private final Interruptores interruptores;
    private final Clock clock;

    RecordatorioDeEventoEnElChatService(DestinatarioDelMensajeDeEvento destinatarios, LoadPreferenciasPort preferencias,
                                        RecordatorioDeEventoEnElChat chat, Interruptores interruptores, Clock clock) {
        this.destinatarios = destinatarios;
        this.preferencias = preferencias;
        this.chat = chat;
        this.interruptores = interruptores;
        this.clock = clock;
    }

    @Override
    public void recordar(AvisoDeMensajeDelEvento aviso) {
        if (!interruptores.prendida(CLAVE_DE_EVENTOS) || destinatarios.siLeCorresponde(aviso).isEmpty()) {
            return;
        }
        Instant ahora = clock.now();
        VeredictoDelMensaje veredicto = VeredictoDelMensaje.de(aviso.inicio(), aviso.avisoMin(), ahora, VeredictoDelMensaje.ATRASO_MAXIMO);
        if (!veredicto.envia()) {
            log.info("[notifications.evento-chat] recordatorio {} no se escribe en el chat: {}", aviso.recordatorioId(), veredicto.motivo());
            return;
        }
        boolean encendida = preferencias.habilitadaPara(aviso.destinatarioId(), TipoNotificacion.RECORDATORIO_EVENTO)
                .orElse(PreferenciaNotificacion.DEFAULT_HABILITADA);
        if (!encendida) {
            return;
        }
        var resultado = chat.escribir(aviso.destinatarioId(), aviso.recordatorioId(), aviso.mensaje().textoParaElChat(ahora));
        log.debug("[notifications.evento-chat] recordatorio {} -> {}", aviso.recordatorioId(), resultado);
    }
}
