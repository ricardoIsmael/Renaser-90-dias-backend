package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.GuardarMensajeUnicoPort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.chat.api.MensajeDeChatGuardadoEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

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
    /** D-221: el aviso push de un mensaje del programa, igual que el de una persona ({@code MensajeService}). */
    private final ApplicationEventPublisher eventos;
    /** D-223: las piezas con id calculado ({@link #enviarUnaVez}). */
    private final GuardarMensajeUnicoPort guardarUnicoPort;

    public MensajeDelProgramaService(LoadConversacionPort loadConversacionPort, SaveMensajePort saveMensajePort,
                                     PublicarMensajeFanoutPort publicarMensajeFanoutPort, Clock clock,
                                     IdGenerator idGenerator, ApplicationEventPublisher eventos,
                                     GuardarMensajeUnicoPort guardarUnicoPort) {
        this.loadConversacionPort = loadConversacionPort;
        this.saveMensajePort = saveMensajePort;
        this.guardarUnicoPort = guardarUnicoPort;
        this.publicarMensajeFanoutPort = publicarMensajeFanoutPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.eventos = eventos;
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
        // Dentro de la transacción: va al outbox junto con el mensaje (ver MensajeService.avisarQueSeGuardo).
        eventos.publishEvent(new MensajeDeChatGuardadoEvent(guardado.id().value(), conversacionId.value()));
        return guardado;
    }

    /**
     * Cada pieza lleva un milisegundo más que la anterior: el chat ordena por {@code creado_en}, y la imagen
     * tiene que quedar antes que su texto aunque el reloj devuelva el mismo instante (D-223).
     */
    @Override
    @Transactional
    public int enviarUnaVez(EntregaDelPrograma entrega) {
        ConversacionId conversacionId = entrega.conversacionId();
        if (loadConversacionPort.porId(conversacionId).isEmpty()) {
            throw new NoSuchElementException("Conversacion no encontrada: " + conversacionId);
        }
        Instant ahora = clock.now();
        int enviadas = 0;
        for (int i = 0; i < entrega.piezas().size(); i++) {
            PiezaDelPrograma pieza = entrega.piezas().get(i);
            Mensaje mensaje = mensajeDe(entrega, pieza, ahora.plusMillis(i));
            if (guardarUnicoPort.guardarSiNoExiste(mensaje)) {
                publicarDespuesDelCommit(mensaje);
                avisar(mensaje, pieza.aviso(), entrega.sobreQuien());
                enviadas++;
            }
        }
        return enviadas;
    }

    /** Sin persona a quien se refiera (D-262), el mensaje se guarda sin {@code emisor_id}. */
    private static Mensaje mensajeDe(EntregaDelPrograma entrega, PiezaDelPrograma pieza, Instant creadoEn) {
        if (entrega.sobreQuien() == null) {
            return Mensaje.delProgramaSinPersona(pieza.id(), entrega.conversacionId(), pieza.contenido(), creadoEn);
        }
        return Mensaje.delPrograma(pieza.id(), entrega.conversacionId(), entrega.sobreQuien(), pieza.contenido(),
                creadoEn);
    }

    /** Dentro de la transacción, como {@link #enviarDelPrograma}: va al outbox junto con el mensaje. */
    private void avisar(Mensaje mensaje, AvisoDeLaPieza aviso, UserId sobreQuien) {
        if (aviso == AvisoDeLaPieza.SIN_AVISO) {
            return;
        }
        UUID soloPara = aviso == AvisoDeLaPieza.SOLO_A_QUIEN_SE_REFIERE ? sobreQuien.value() : null;
        eventos.publishEvent(new MensajeDeChatGuardadoEvent(mensaje.id().value(), mensaje.conversacionId().value(),
                soloPara));
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
