package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.ListarMensajesUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class MensajeService implements EnviarMensajeUseCase, ListarMensajesUseCase {

    private static final int LIMITE_POR_DEFECTO = 30;
    private static final int LIMITE_MAXIMO = 100;

    private final LoadConversacionPort loadConversacionPort;
    private final EsParticipantePort esParticipantePort;
    private final MarcarLeidoPort marcarLeidoPort;
    private final SaveMensajePort saveMensajePort;
    private final LoadMensajePort loadMensajePort;
    private final PublicarMensajeFanoutPort publicarMensajeFanoutPort;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    public MensajeService(LoadConversacionPort loadConversacionPort, EsParticipantePort esParticipantePort,
                           MarcarLeidoPort marcarLeidoPort, SaveMensajePort saveMensajePort,
                           LoadMensajePort loadMensajePort, PublicarMensajeFanoutPort publicarMensajeFanoutPort,
                           UserSummaryFinder userSummaryFinder, Clock clock) {
        this.loadConversacionPort = loadConversacionPort;
        this.esParticipantePort = esParticipantePort;
        this.marcarLeidoPort = marcarLeidoPort;
        this.saveMensajePort = saveMensajePort;
        this.loadMensajePort = loadMensajePort;
        this.publicarMensajeFanoutPort = publicarMensajeFanoutPort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Mensaje enviar(EnviarMensajeCommand command) {
        requireActivo(command.actorId());
        requireConversacion(command.conversacionId());
        requireParticipante(command.conversacionId(), command.actorId());
        if (command.respuestaAId() != null) {
            requireRespuestaEnMismaConversacion(command.respuestaAId(), command.conversacionId());
        }

        Instant ahora = clock.now();
        Mensaje mensaje = Mensaje.escribir(command.conversacionId(), command.actorId(), command.tipo(),
                command.texto(), command.mediaBucket(), command.mediaRuta(), command.mediaMime(),
                command.mediaBytes(), command.mediaDuracionS(), command.respuestaAId(), ahora);
        Mensaje guardado = saveMensajePort.save(mensaje);
        // El emisor "ya leyo" hasta el mensaje que acaba de escribir.
        marcarLeidoPort.marcarLeido(command.conversacionId(), command.actorId(), ahora);
        publicarDespuesDelCommit(guardado);
        return guardado;
    }

    /**
     * Redis Pub/Sub solo empuja: el mensaje YA esta durable en Postgres antes de esta
     * llamada. Publicar antes del commit arriesgaria mostrar en vivo un mensaje que un
     * rollback despues borra (CLAUDE.MD del encargo: "SIEMPRE primero en Postgres").
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

    @Override
    public PaginaMensajes listar(UserId actorId, ConversacionId conversacionId, Instant cursor, int limite) {
        requireActivo(actorId);
        requireConversacion(conversacionId);
        requireParticipante(conversacionId, actorId);

        int limiteEfectivo = limite <= 0 ? LIMITE_POR_DEFECTO : Math.min(limite, LIMITE_MAXIMO);
        List<Mensaje> pagina = loadMensajePort.pagina(conversacionId, cursor, limiteEfectivo + 1);
        boolean hayMas = pagina.size() > limiteEfectivo;
        List<Mensaje> resultado = hayMas ? pagina.subList(0, limiteEfectivo) : pagina;
        Instant siguienteCursor = hayMas ? resultado.get(resultado.size() - 1).creadoEn() : null;
        return new PaginaMensajes(resultado, siguienteCursor, hayMas);
    }

    private void requireRespuestaEnMismaConversacion(MensajeId respuestaAId, ConversacionId conversacionId) {
        Mensaje original = loadMensajePort.porId(respuestaAId)
                .orElseThrow(() -> new NoSuchElementException("Mensaje no encontrado: " + respuestaAId));
        if (!original.conversacionId().equals(conversacionId)) {
            throw new IllegalArgumentException("No se puede responder a un mensaje de otra conversacion");
        }
    }

    private void requireParticipante(ConversacionId conversacionId, UserId usuarioId) {
        if (!esParticipantePort.esParticipante(conversacionId, usuarioId)) {
            throw new NotAuthorizedException("No sos participante de esta conversacion");
        }
    }

    private void requireConversacion(ConversacionId id) {
        loadConversacionPort.porId(id)
                .orElseThrow(() -> new NoSuchElementException("Conversacion no encontrada: " + id));
    }

    private void requireActivo(UserId usuarioId) {
        UserSummary usuario = userSummaryFinder.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + usuarioId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }
}
