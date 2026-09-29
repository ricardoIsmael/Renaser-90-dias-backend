package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.presencia.RegistrarConversacionAbiertaUseCase;
import com.renaser.os.chat.application.ports.out.presencia.ConversacionAbiertaPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

/**
 * Anota qué chat tiene abierto cada persona (D-221), con la misma vigencia que la presencia
 * ({@link PresenciaService#VIGENCIA}, renovada cada 45 s por el adaptador): perder tres renovaciones
 * seguidas la da por cerrada, y el costo de equivocarse para ese lado es un push de más, nunca uno de
 * menos.
 */
@Service
public class ConversacionAbiertaService implements RegistrarConversacionAbiertaUseCase {

    private final ConversacionAbiertaPort conversacionAbiertaPort;

    public ConversacionAbiertaService(ConversacionAbiertaPort conversacionAbiertaPort) {
        this.conversacionAbiertaPort = conversacionAbiertaPort;
    }

    @Override
    public void laAbrio(UserId usuarioId, ConversacionId conversacionId) {
        conversacionAbiertaPort.marcarAbierta(usuarioId, conversacionId, PresenciaService.VIGENCIA);
    }

    @Override
    public void laCerro(UserId usuarioId, ConversacionId conversacionId) {
        conversacionAbiertaPort.marcarCerrada(usuarioId, conversacionId);
    }

    @Override
    public void sigueAbierta(UserId usuarioId, ConversacionId conversacionId) {
        conversacionAbiertaPort.marcarAbierta(usuarioId, conversacionId, PresenciaService.VIGENCIA);
    }
}
