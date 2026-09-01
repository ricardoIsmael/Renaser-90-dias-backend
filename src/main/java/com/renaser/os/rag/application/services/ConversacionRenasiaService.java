package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.conversacion.ObtenerHistorialUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FragmentoRelevante;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaRenasiaPort;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.FuenteMensaje;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Orquesta el caso de uso completo de Renasia (docs/MODULO_RAG.md §4): verificar actor
 * activo, consumir cuota, buscar-o-crear la conversacion 1:1, guardar la pregunta,
 * buscar contexto en la base de conocimiento, preguntarle al modelo en streaming y, al
 * completar el stream, guardar la respuesta del asistente con sus fuentes.
 *
 * <p><b>Limite de la transaccion:</b> {@link #preguntar} arma y devuelve un {@code Flux}
 * sin suscribirse — Spring cierra la transaccion declarativa de este metodo apenas el
 * metodo retorna, es decir, apenas se construye el {@code Flux} (no cuando termina de
 * emitir). Eso alcanza para que "verificar actor + consumir cuota + buscar-o-crear
 * conversacion + guardar la pregunta" sea atomico (son llamadas sincronas dentro del
 * cuerpo del metodo). El guardado del mensaje del ASISTENTE ocurre despues, cuando el
 * stream se completa — fuera de esa transaccion, en su propia transaccion implicita (cada
 * metodo de un {@code JpaRepository} ya es transaccional por si solo).
 */
@Service
public class ConversacionRenasiaService implements PreguntarRenasiaUseCase, ObtenerHistorialUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConversacionRenasiaService.class);

    private static final int LIMITE_POR_DEFECTO = 30;
    private static final int LIMITE_MAXIMO = 100;
    private static final int TOP_K = 5;

    private final UserSummaryFinder userSummaryFinder;
    private final ControlCuotaRenasiaPort controlCuotaRenasiaPort;
    private final LoadConversacionRenasiaPort loadConversacionRenasiaPort;
    private final SaveConversacionRenasiaPort saveConversacionRenasiaPort;
    private final LoadMensajeRenasiaPort loadMensajeRenasiaPort;
    private final SaveMensajeRenasiaPort saveMensajeRenasiaPort;
    private final VectorStorePort vectorStorePort;
    private final ChatIAPort chatIAPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ConversacionRenasiaService(UserSummaryFinder userSummaryFinder,
                                       ControlCuotaRenasiaPort controlCuotaRenasiaPort,
                                       LoadConversacionRenasiaPort loadConversacionRenasiaPort,
                                       SaveConversacionRenasiaPort saveConversacionRenasiaPort,
                                       LoadMensajeRenasiaPort loadMensajeRenasiaPort,
                                       SaveMensajeRenasiaPort saveMensajeRenasiaPort, VectorStorePort vectorStorePort,
                                       ChatIAPort chatIAPort, Clock clock, IdGenerator idGenerator) {
        this.userSummaryFinder = userSummaryFinder;
        this.controlCuotaRenasiaPort = controlCuotaRenasiaPort;
        this.loadConversacionRenasiaPort = loadConversacionRenasiaPort;
        this.saveConversacionRenasiaPort = saveConversacionRenasiaPort;
        this.loadMensajeRenasiaPort = loadMensajeRenasiaPort;
        this.saveMensajeRenasiaPort = saveMensajeRenasiaPort;
        this.vectorStorePort = vectorStorePort;
        this.chatIAPort = chatIAPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public Flux<String> preguntar(PreguntarRenasiaCommand command) {
        requireActivo(command.actorId());
        requireCuotaDisponible(command.actorId());

        List<FragmentoRelevante> fragmentos;
        try {
            buscarOCrearConversacion(command.actorId());
            // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
            saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeUsuario(
                    MensajeRenasiaId.of(idGenerator.newId()), command.actorId(), command.pregunta(), clock.now()));
            fragmentos = vectorStorePort.buscarSimilares(command.pregunta(), TOP_K);
        } catch (RuntimeException e) {
            controlCuotaRenasiaPort.liberar(command.actorId());
            throw e;
        }
        List<String> contexto = fragmentos.stream().map(FragmentoRelevante::contenido).toList();

        StringBuilder respuestaCompleta = new StringBuilder();
        return chatIAPort.responder(command.pregunta(), contexto)
                .doOnNext(respuestaCompleta::append)
                .doOnComplete(() -> persistirRespuestaAsistente(command.actorId(), respuestaCompleta.toString(),
                        fragmentos))
                .doOnError(error -> {
                    logFalloDeStreaming(error);
                    controlCuotaRenasiaPort.liberar(command.actorId());
                });
    }

    @Override
    public PaginaMensajesRenasia obtenerHistorial(UserId actorId, Instant cursor, int limite) {
        requireActivo(actorId);

        int limiteEfectivo = limite <= 0 ? LIMITE_POR_DEFECTO : Math.min(limite, LIMITE_MAXIMO);
        List<MensajeRenasia> pagina = loadMensajeRenasiaPort.pagina(actorId, cursor, limiteEfectivo + 1);
        boolean hayMas = pagina.size() > limiteEfectivo;
        List<MensajeRenasia> resultado = hayMas ? pagina.subList(0, limiteEfectivo) : pagina;
        Instant siguienteCursor = hayMas ? resultado.get(resultado.size() - 1).creadoEn() : null;
        return new PaginaMensajesRenasia(resultado, siguienteCursor, hayMas);
    }

    /** Guarda la respuesta del asistente solo cuando el stream ya termino de emitir — nunca
     * antes (asi el historial no muestra una respuesta a medio generar si el cliente
     * cancela). Un stream vacio (sin tokens, ej. fallo silencioso del modelo) no deja un
     * mensaje de asistente sin contenido: violaria el invariante de {@link MensajeRenasia}. */
    private void persistirRespuestaAsistente(UserId actorId, String contenido, List<FragmentoRelevante> fragmentos) {
        if (contenido.isBlank()) {
            return;
        }
        List<FuenteMensaje> fuentes = fragmentos.stream()
                .map(FragmentoRelevante::leccionId)
                .filter(Objects::nonNull)
                .map(FuenteMensaje::of)
                .toList();
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeAsistente(MensajeRenasiaId.of(idGenerator.newId()),
                actorId, contenido, fuentes, clock.now()));
    }

    private ConversacionRenasia buscarOCrearConversacion(UserId actorId) {
        return loadConversacionRenasiaPort.porUsuarioId(actorId)
                .orElseGet(() -> saveConversacionRenasiaPort.save(ConversacionRenasia.iniciar(actorId, clock.now())));
    }

    private void requireCuotaDisponible(UserId actorId) {
        if (!controlCuotaRenasiaPort.intentarConsumir(actorId)) {
            throw new RateLimitExceededException("Se alcanzo el limite diario de mensajes a Renasia");
        }
    }

    private void requireActivo(UserId actorId) {
        UserSummary usuario = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }

    /** Nunca se loguea la pregunta ni la respuesta: es dato personal (CLAUDE.MD sec. 5.4.9,
     * docs/MODULO_RAG.md D-47). Tampoco el id del actor (es el `sub` de Supabase). */
    private void logFalloDeStreaming(Throwable error) {
        log.warn("Fallo el streaming de respuesta de Renasia", error);
    }
}
