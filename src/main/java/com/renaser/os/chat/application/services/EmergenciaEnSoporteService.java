package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.emergencia.AvisarEmergenciaEnSoporteUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.AvisoDeLaPieza;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.EntregaDelPrograma;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.PiezaDelPrograma;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.emergencia.AvisoDeEmergencia;
import com.renaser.os.chat.domain.model.emergencia.RespuestaAEmergencia;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.users.api.EmergenciaPedidaEvent;
import com.renaser.os.users.api.EmergenciaResueltaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Deja el pedido de emergencia en el chat de soporte del aprendiz (D-244).
 *
 * <p><b>Sin push del chat</b> ({@link AvisoDeLaPieza#SIN_AVISO}): a quienes atienden soporte les llega el aviso
 * propio de la emergencia ({@code notifications}, que se ve también en la campana y no depende de que tengan
 * prendidos los avisos de «Mensajes»); con el push del chat les llegarían dos. Y al aprendiz, que acaba de
 * mandarlo, no hace falta avisarle de su propio pedido.
 *
 * <p><b>La respuesta al resolverlo</b> ({@link #responder}, pedido del dueño del 2026-10-02) sí avisa, y solo a
 * la persona ({@link AvisoDeLaPieza#SOLO_A_QUIEN_SE_REFIERE}): es para ella, el staff ya sabe lo que hizo.
 */
@Service
public class EmergenciaEnSoporteService implements AvisarEmergenciaEnSoporteUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmergenciaEnSoporteService.class);

    private final LoadConversacionPort conversaciones;
    private final EnviarMensajeDelProgramaUseCase delPrograma;

    EmergenciaEnSoporteService(LoadConversacionPort conversaciones, EnviarMensajeDelProgramaUseCase delPrograma) {
        this.conversaciones = conversaciones;
        this.delPrograma = delPrograma;
    }

    @Override
    public void avisar(EmergenciaPedidaEvent pedido) {
        AvisoDeEmergencia aviso = new AvisoDeEmergencia(pedido.solicitudId(), pedido.queOcurrio(), pedido.diaPedido(),
                pedido.diaAlPedir());
        soporteDe(pedido.aprendizId(), pedido.solicitudId()).ifPresent(soporte -> enviar(soporte, pedido.aprendizId(),
                new PiezaDelPrograma(aviso.idDelMensaje(), ContenidoDelPrograma.texto(aviso.texto()),
                        AvisoDeLaPieza.SIN_AVISO)));
    }

    @Override
    public void responder(EmergenciaResueltaEvent resuelta) {
        RespuestaAEmergencia respuesta = new RespuestaAEmergencia(resuelta.solicitudId(), resuelta.diaAplicado(),
                resuelta.diaActual());
        soporteDe(resuelta.aprendizId(), resuelta.solicitudId()).ifPresent(soporte -> enviar(soporte,
                resuelta.aprendizId(), new PiezaDelPrograma(respuesta.idDelMensaje(),
                        ContenidoDelPrograma.texto(respuesta.texto()), AvisoDeLaPieza.SOLO_A_QUIEN_SE_REFIERE)));
    }

    private Optional<ConversacionId> soporteDe(UserId aprendizId, java.util.UUID solicitudId) {
        Optional<Conversacion> soporte = conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(aprendizId));
        if (soporte.isEmpty()) {
            log.warn("[chat.emergencia] {} no tiene chat de soporte: el pedido {} no se escribe en el chat",
                    aprendizId, solicitudId);
        }
        return soporte.map(Conversacion::id);
    }

    private void enviar(ConversacionId soporte, UserId aprendizId, PiezaDelPrograma pieza) {
        delPrograma.enviarUnaVez(new EntregaDelPrograma(soporte, aprendizId, List.of(pieza)));
    }
}
