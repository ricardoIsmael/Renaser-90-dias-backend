package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.NoSuchElementException;

/**
 * El programa escribe en un chat (D-199/D-204). Aparte de {@code MensajeService} a propósito: ese
 * caso de uso gira alrededor de quien escribe (activo, participante, marca de leído), y acá no
 * escribe nadie.
 */
@Service
public class MensajeDelProgramaService implements EnviarMensajeDelProgramaUseCase {

    private final LoadConversacionPort loadConversacionPort;
    private final SaveMensajePort saveMensajePort;
    private final PublicarMensajeFanoutPort publicarMensajeFanoutPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public MensajeDelProgramaService(LoadConversacionPort loadConversacionPort, SaveMensajePort saveMensajePort,
                                     PublicarMensajeFanoutPort publicarMensajeFanoutPort, Clock clock,
                                     IdGenerator idGenerator) {
        this.loadConversacionPort = loadConversacionPort;
        this.saveMensajePort = saveMensajePort;
        this.publicarMensajeFanoutPort = publicarMensajeFanoutPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public Mensaje enviarDelPrograma(ConversacionId conversacionId, UserId sobreQuien, ContenidoDelPrograma contenido) {
        if (loadConversacionPort.porId(conversacionId).isEmpty()) {
            throw new NoSuchElementException("Conversacion no encontrada: " + conversacionId);
        }
        Mensaje mensaje = Mensaje.delPrograma(MensajeId.of(idGenerator.newId()), conversacionId, sobreQuien,
                contenido, clock.now());
        Mensaje guardado = saveMensajePort.save(mensaje);
        publicarDespuesDelCommit(guardado);
        return guardado;
    }

    /**
     * El mismo criterio que {@code MensajeService}: Redis solo empuja lo que ya está en Postgres, así
     * que se publica recién después del commit (si un rollback deshace el mensaje, nadie lo vio).
     */
    private void publicarDespuesDelCommit(Mensaje mensaje) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publicarMensajeFanoutPort.publicar(mensaje);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publicarMensajeFanoutPort.publicar(mensaje);
            }
        });
    }
}
