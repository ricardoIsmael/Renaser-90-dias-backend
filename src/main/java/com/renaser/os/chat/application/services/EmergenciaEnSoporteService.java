package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.emergencia.AvisarEmergenciaEnSoporteUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.AvisoDeLaPieza;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.EntregaDelPrograma;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.PiezaDelPrograma;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.emergencia.AvisoDeEmergencia;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.users.api.EmergenciaPedidaEvent;
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
        Optional<Conversacion> soporte = conversaciones.porClaveDirecta(Conversacion.claveSoporteDe(pedido.aprendizId()));
        if (soporte.isEmpty()) {
            log.warn("[chat.emergencia] {} no tiene chat de soporte: el pedido {} queda solo en el aviso a soporte",
                    pedido.aprendizId(), pedido.solicitudId());
            return;
        }
        AvisoDeEmergencia aviso = new AvisoDeEmergencia(pedido.solicitudId(), pedido.queOcurrio(), pedido.diaPedido(),
                pedido.diaAlPedir());
        delPrograma.enviarUnaVez(new EntregaDelPrograma(soporte.get().id(), pedido.aprendizId(), List.of(
                new PiezaDelPrograma(aviso.idDelMensaje(), ContenidoDelPrograma.texto(aviso.texto()),
                        AvisoDeLaPieza.SIN_AVISO))));
    }
}
