package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.conversacion.ObtenerHistorialUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FiltroLecciones;
import com.renaser.os.rag.application.ports.out.conocimiento.VectorStorePort.FragmentoRelevante;
import com.renaser.os.rag.application.ports.out.conversacion.ConsultarLeccionesVisiblesPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaRenasiaPort;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort.Consulta;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
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
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Orquesta el caso de uso completo de los dos asistentes (docs/MODULO_RAG.md §4): verificar
 * actor activo, consumir cuota, buscar-o-crear la conversacion 1:1, guardar la pregunta,
 * buscar contexto en la base de conocimiento, preguntarle al modelo en streaming y, al
 * completar el stream, guardar la respuesta del asistente con sus fuentes.
 *
 * <p><b>D-102 — dos agentes, un servicio.</b> El acompanante de los 90 dias
 * ({@link AgenteConversacional#COMPANION}) y el tutor de cursos
 * ({@link AgenteConversacional#COURSE_TUTOR}, Sparkie) comparten esta orquestacion porque los
 * pasos son los mismos; lo que cambia por agente es (1) el historial que se lee y se escribe —
 * cada mensaje lleva su agente y la memoria de un agente nunca incluye turnos del otro —, (2) el
 * universo del contexto — el tutor, si viene {@code cursoId}, solo cita lecciones de ese curso —
 * y (3) el prompt de sistema, que elige el adaptador de {@link ChatIAPort} segun el agente. La
 * cuota diaria es una sola por persona: es proteccion de abuso, no una cuenta por asistente.
 *
 * <p><b>Sin transaccion envolvente, a proposito (C-1/C-4).</b> {@link #preguntar} tenia
 * {@code @Transactional}, y adentro llamaba a {@code buscarSimilares}, que a su vez llama al
 * puerto de embeddings. Con un proveedor real eso significa retener una conexion de Hikari
 * durante toda una llamada de red, sumada al alta de conversacion y al guardado de la
 * pregunta: el mismo agotamiento de pool que la auditoria de concurrencia del 2026-09-01
 * corrigio en {@code onboarding} y {@code evidence}. Hoy no se nota porque el adaptador de
 * embeddings responde en microsegundos; se notaria el primer dia con credenciales reales.
 *
 * <p>Lo que se pierde al sacarla es la atomicidad entre "crear la conversacion" y "guardar la
 * pregunta". Es un precio barato y acotado: la conversacion es una fila 1:1 sin contenido
 * propio, asi que en el peor caso queda vacia y la siguiente pregunta la reutiliza. Cada
 * puerto corre igual en su propia transaccion corta (cada metodo de un {@code JpaRepository}
 * ya es transaccional por si solo), que es el mismo criterio que dejo la auditoria.
 *
 * <p>El guardado del mensaje del ASISTENTE ocurre cuando el stream se completa, mas tarde y
 * en su propia transaccion — eso no cambia.
 *
 * <p><b>La busqueda de contexto se filtra por lo que el actor puede ver HOY.</b> Antes de
 * llamar a {@code vectorStorePort.buscarSimilares}, este caso de uso resuelve el conjunto de
 * lecciones visibles para {@code actorId} via {@link ConsultarLeccionesVisiblesPort} (que
 * delega en el gate de programa real de {@code academy}) y lo pasa como
 * {@link FiltroLecciones#soloVisibles}. Sin esto, el asistente podia citarle a un aprendiz en el
 * dia 3 del programa el contenido de una leccion del dia 60 que su propio modulo de academia
 * todavia tiene bloqueada — un bug real de fuga de contenido, no solo de UX. La resolucion de
 * QUE es visible vive en {@code academy} (via el finder), y DONDE se aplica el filtro vive en
 * el adaptador de {@code VectorStorePort} (ver su javadoc para el porque).
 */
@Service
public class ConversacionRenasiaService implements PreguntarRenasiaUseCase, ObtenerHistorialUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConversacionRenasiaService.class);

    private static final int LIMITE_POR_DEFECTO = 30;
    private static final int LIMITE_MAXIMO = 100;
    private static final int TOP_K = 5;
    /** D-100: cuantos turnos previos viajan al modelo. 10 mensajes = 5 idas y vueltas. */
    private static final int TURNOS_DE_MEMORIA = 10;
    /** Texto apto para mostrar cuando el modelo falla; el detalle real va al log. */
    public static final String MENSAJE_ERROR_MODELO =
            "No pude responder en este momento. Intenta de nuevo en unos segundos.";

    private final UserSummaryFinder userSummaryFinder;
    private final ControlCuotaRenasiaPort controlCuotaRenasiaPort;
    private final LoadConversacionRenasiaPort loadConversacionRenasiaPort;
    private final SaveConversacionRenasiaPort saveConversacionRenasiaPort;
    private final LoadMensajeRenasiaPort loadMensajeRenasiaPort;
    private final SaveMensajeRenasiaPort saveMensajeRenasiaPort;
    private final VectorStorePort vectorStorePort;
    private final ConsultarLeccionesVisiblesPort consultarLeccionesVisiblesPort;
    private final ChatIAPort chatIAPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ConversacionRenasiaService(UserSummaryFinder userSummaryFinder,
                                       ControlCuotaRenasiaPort controlCuotaRenasiaPort,
                                       LoadConversacionRenasiaPort loadConversacionRenasiaPort,
                                       SaveConversacionRenasiaPort saveConversacionRenasiaPort,
                                       LoadMensajeRenasiaPort loadMensajeRenasiaPort,
                                       SaveMensajeRenasiaPort saveMensajeRenasiaPort, VectorStorePort vectorStorePort,
                                       ConsultarLeccionesVisiblesPort consultarLeccionesVisiblesPort,
                                       ChatIAPort chatIAPort, Clock clock, IdGenerator idGenerator) {
        this.userSummaryFinder = userSummaryFinder;
        this.controlCuotaRenasiaPort = controlCuotaRenasiaPort;
        this.loadConversacionRenasiaPort = loadConversacionRenasiaPort;
        this.saveConversacionRenasiaPort = saveConversacionRenasiaPort;
        this.loadMensajeRenasiaPort = loadMensajeRenasiaPort;
        this.saveMensajeRenasiaPort = saveMensajeRenasiaPort;
        this.vectorStorePort = vectorStorePort;
        this.consultarLeccionesVisiblesPort = consultarLeccionesVisiblesPort;
        this.chatIAPort = chatIAPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public Flux<EventoRenasia> preguntar(PreguntarRenasiaCommand command) {
        requireActivo(command.actorId());
        requireCuotaDisponible(command.actorId());

        List<FragmentoRelevante> fragmentos;
        List<MensajeRenasia> historial;
        try {
            buscarOCrearConversacion(command.actorId());
            // D-100: la memoria se lee ANTES de guardar la pregunta nueva, para que el historial
            // no la incluya dos veces (una como turno previo y otra como pregunta). `pagina`
            // devuelve del mas nuevo al mas viejo; el modelo los quiere cronologicos.
            // D-102: solo los turnos con ESTE agente.
            historial = ultimosTurnosCronologicos(command.actorId(), command.agente());
            // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
            saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeUsuario(
                    MensajeRenasiaId.of(idGenerator.newId()), command.actorId(), command.agente(),
                    command.pregunta(), clock.now()));
            fragmentos = vectorStorePort.buscarSimilares(command.pregunta(), TOP_K, filtroDeContexto(command));
        } catch (RuntimeException e) {
            controlCuotaRenasiaPort.liberar(command.actorId());
            throw e;
        }
        List<String> contexto = fragmentos.stream().map(FragmentoRelevante::contenido).toList();

        StringBuilder respuestaCompleta = new StringBuilder();
        return chatIAPort.responder(new Consulta(command.agente(), command.pregunta(), contexto, command.ambito(),
                        historial))
                .doOnNext(evento -> acumularTexto(evento, respuestaCompleta))
                .concatMap(evento -> agregarFuentesAntesDeFin(evento, fragmentos))
                .doOnComplete(() -> persistirRespuestaAsistente(command, respuestaCompleta.toString(), fragmentos))
                .doOnError(error -> {
                    logFalloDeStreaming(error);
                    controlCuotaRenasiaPort.liberar(command.actorId());
                })
                // D-100: el fallo del modelo deja de ser invisible. Antes el controller lo convertia
                // en un `fin` pelado y el aprendiz veia su pregunta sin ninguna respuesta ni motivo.
                // Se emite un `error` apto para mostrar y despues el `fin` que el contrato SSE exige.
                .onErrorResume(error -> Flux.just(new EventoRenasia.Error(MENSAJE_ERROR_MODELO),
                        new EventoRenasia.Fin()));
    }

    /**
     * D-102: el acompanante cita cualquier leccion visible hoy; el tutor de cursos, si el cliente
     * dijo en que curso esta, solo las de ese curso (siempre dentro de lo visible: el gate de
     * {@code academy} no se relaja, se acota). Un tutor sin {@code cursoId} se comporta como el
     * acompanante en cuanto a contexto — es mejor que quedarse sin material.
     */
    private FiltroLecciones filtroDeContexto(PreguntarRenasiaCommand command) {
        boolean acotadoAlCurso = command.agente() == AgenteConversacional.COURSE_TUTOR
                && command.cursoId() != null && !command.cursoId().isBlank();
        Set<String> visibles = acotadoAlCurso
                ? consultarLeccionesVisiblesPort.visiblesParaActorEnCurso(command.actorId(), command.cursoId())
                : consultarLeccionesVisiblesPort.visiblesParaActor(command.actorId());
        return FiltroLecciones.soloVisibles(visibles);
    }

    /** Solo acumula {@link EventoRenasia.Texto}: {@code Fuentes}/{@code Fin} no aportan contenido. */
    private static void acumularTexto(EventoRenasia evento, StringBuilder respuestaCompleta) {
        if (evento instanceof EventoRenasia.Texto texto) {
            respuestaCompleta.append(texto.fragmento());
        }
    }

    /**
     * {@link ChatIAPort} solo conoce texto de contexto, no que lección lo originó — por eso
     * las fuentes las arma este caso de uso, no el adaptador de IA, a partir de lo que
     * {@link VectorStorePort} ya recuperó. Se inyectan justo antes del {@link EventoRenasia.Fin}
     * que emite el puerto, y solo si hubo al menos una lección citable (contrato SSE: "fuentes"
     * aparece a lo sumo una vez).
     */
    private static Flux<EventoRenasia> agregarFuentesAntesDeFin(EventoRenasia evento,
            List<FragmentoRelevante> fragmentos) {
        if (!(evento instanceof EventoRenasia.Fin)) {
            return Flux.just(evento);
        }
        List<String> leccionIds = fragmentos.stream()
                .map(FragmentoRelevante::leccionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return leccionIds.isEmpty() ? Flux.just(evento) : Flux.just(new EventoRenasia.Fuentes(leccionIds), evento);
    }

    @Override
    public PaginaMensajesRenasia obtenerHistorial(UserId actorId, AgenteConversacional agente, Instant cursor,
                                                  int limite) {
        Objects.requireNonNull(agente, "agente es obligatorio (D-102)");
        requireActivo(actorId);

        int limiteEfectivo = limite <= 0 ? LIMITE_POR_DEFECTO : Math.min(limite, LIMITE_MAXIMO);
        List<MensajeRenasia> pagina = loadMensajeRenasiaPort.pagina(actorId, agente, cursor, limiteEfectivo + 1);
        boolean hayMas = pagina.size() > limiteEfectivo;
        List<MensajeRenasia> resultado = hayMas ? pagina.subList(0, limiteEfectivo) : pagina;
        Instant siguienteCursor = hayMas ? resultado.get(resultado.size() - 1).creadoEn() : null;
        return new PaginaMensajesRenasia(resultado, siguienteCursor, hayMas);
    }

    /** Guarda la respuesta del asistente solo cuando el stream ya termino de emitir — nunca
     * antes (asi el historial no muestra una respuesta a medio generar si el cliente
     * cancela). Un stream vacio (sin tokens, ej. fallo silencioso del modelo) no deja un
     * mensaje de asistente sin contenido: violaria el invariante de {@link MensajeRenasia}. */
    private void persistirRespuestaAsistente(PreguntarRenasiaCommand command, String contenido,
                                             List<FragmentoRelevante> fragmentos) {
        if (contenido.isBlank()) {
            return;
        }
        List<FuenteMensaje> fuentes = fragmentos.stream()
                .map(FragmentoRelevante::leccionId)
                .filter(Objects::nonNull)
                .map(FuenteMensaje::of)
                .toList();
        saveMensajeRenasiaPort.save(MensajeRenasia.escribirDeAsistente(MensajeRenasiaId.of(idGenerator.newId()),
                command.actorId(), command.agente(), contenido, fuentes, clock.now()));
    }

    /** Ver el comentario en {@link #preguntar}: del mas viejo al mas nuevo, sin la pregunta actual. */
    private List<MensajeRenasia> ultimosTurnosCronologicos(UserId actorId, AgenteConversacional agente) {
        List<MensajeRenasia> recientes = new java.util.ArrayList<>(
                loadMensajeRenasiaPort.pagina(actorId, agente, null, TURNOS_DE_MEMORIA));
        java.util.Collections.reverse(recientes);
        return List.copyOf(recientes);
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
        log.warn("Fallo el streaming de respuesta del asistente", error);
    }
}
