package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.ListarMensajesUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido.RespuestaPreview;
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
import com.renaser.os.shared.domain.IdGenerator;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

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
    private final IdGenerator idGenerator;

    public MensajeService(LoadConversacionPort loadConversacionPort, EsParticipantePort esParticipantePort,
                           MarcarLeidoPort marcarLeidoPort, SaveMensajePort saveMensajePort,
                           LoadMensajePort loadMensajePort, PublicarMensajeFanoutPort publicarMensajeFanoutPort,
                           UserSummaryFinder userSummaryFinder, Clock clock, IdGenerator idGenerator) {
        this.loadConversacionPort = loadConversacionPort;
        this.esParticipantePort = esParticipantePort;
        this.marcarLeidoPort = marcarLeidoPort;
        this.saveMensajePort = saveMensajePort;
        this.loadMensajePort = loadMensajePort;
        this.publicarMensajeFanoutPort = publicarMensajeFanoutPort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
        this.idGenerator = idGenerator;
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
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD §5.4.7).
        Mensaje mensaje = Mensaje.escribir(MensajeId.of(idGenerator.newId()), command.conversacionId(),
                command.actorId(), command.tipo(), command.texto(), command.mediaBucket(), command.mediaRuta(),
                command.mediaMime(), command.mediaBytes(), command.mediaDuracionS(), command.respuestaAId(),
                ahora);
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

        List<MensajeEnriquecido> enriquecidos = enriquecer(resultado);
        return new PaginaMensajes(enriquecidos, siguienteCursor, hayMas);
    }

    /**
     * Resuelve nombre/avatar del emisor de cada mensaje y el preview de "respuesta a"
     * para TODA la pagina en, como mucho, DOS consultas EN LOTE — una a
     * {@code loadMensajePort.porIds} (mensajes originales citados) y una a
     * {@code userSummaryFinder.findByIds} (todos los emisores involucrados, propios y de
     * los originales) — nunca una consulta por mensaje (#29, mismo criterio que
     * {@code TracksDelDiaProyeccionService} de `habits`).
     */
    private List<MensajeEnriquecido> enriquecer(List<Mensaje> mensajes) {
        if (mensajes.isEmpty()) {
            return List.of();
        }
        List<MensajeId> idsRespuesta = mensajes.stream().map(Mensaje::respuestaAId).filter(Objects::nonNull)
                .distinct().toList();
        Map<MensajeId, Mensaje> originales = idsRespuesta.isEmpty() ? Map.of() : loadMensajePort.porIds(idsRespuesta);

        Set<UserId> idsUsuarios = new LinkedHashSet<>();
        mensajes.forEach(m -> idsUsuarios.add(m.emisorId()));
        originales.values().forEach(o -> idsUsuarios.add(o.emisorId()));
        Map<UserId, UserSummary> usuarios = userSummaryFinder.findByIds(idsUsuarios);

        return mensajes.stream().map(m -> aEnriquecido(m, originales, usuarios)).toList();
    }

    private static MensajeEnriquecido aEnriquecido(Mensaje mensaje, Map<MensajeId, Mensaje> originales,
                                                     Map<UserId, UserSummary> usuarios) {
        UserSummary emisor = usuarios.get(mensaje.emisorId());
        RespuestaPreview preview = mensaje.respuestaAId() == null ? null
                : previewDe(originales.get(mensaje.respuestaAId()), usuarios);
        return new MensajeEnriquecido(mensaje, emisor != null ? emisor.fullName() : null,
                emisor != null ? emisor.avatarUrl() : null, preview);
    }

    /** {@code null} si el mensaje original ya no esta disponible (no deberia pasar hoy —
     * no hay borrado fisico — pero no hay razon para reventar el listado completo por
     * eso). */
    private static RespuestaPreview previewDe(Mensaje original, Map<UserId, UserSummary> usuarios) {
        if (original == null) {
            return null;
        }
        UserSummary emisorOriginal = usuarios.get(original.emisorId());
        return new RespuestaPreview(original.id(), emisorOriginal != null ? emisorOriginal.fullName() : null,
                original.tipo(), recortar(original.texto()), original.eliminadoEn());
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= MensajeEnriquecido.LARGO_PREVIEW ? limpio
                : limpio.substring(0, MensajeEnriquecido.LARGO_PREVIEW) + "…";
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
