package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.conversacion.ObtenerHistorialUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase;
import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ConsultarPropuestasDelTurnoUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.in.seguridad.RevisarPatronDeMalestarUseCase;
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
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort.Consulta;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import com.renaser.os.rag.domain.model.conversacion.FuenteMensaje;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
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
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
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
 *
 * <p><b>Un paso mas desde el 2026-09-15: revisar si el malestar se repite.</b> Despues de guardar
 * la pregunta y antes de hablar con el modelo, {@link RevisarPatronDeMalestarUseCase} mira si la
 * persona viene escribiendo expresiones de malestar varias veces en pocos dias. Si se repitio, el
 * aviso a ADMIN/ALQUIMISTA sale por evento y el texto de apoyo configurado se agrega al final de
 * esta misma respuesta ({@link #conApoyoAntesDelFin}). <b>Nada de esto es un diagnostico</b> ni
 * cambia el prompt, el contexto ni las herramientas: la conversacion es exactamente la misma y el
 * modelo ni se entera. Es best-effort: si esa revision falla, la persona igual recibe su respuesta.
 *
 * <p><b>Propuestas del turno (fase 2, D-153).</b> Una herramienta de escritura no escribe: deja
 * una propuesta pendiente. Al terminar el stream del modelo, este servicio recoge las que nacieron
 * en el turno y las manda antes del {@code fin} ({@link #conPropuestasAntesDelFin}).
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
    /**
     * Cuando el que no puede es el PROVEEDOR (cuota agotada, saturado): decirle "en unos segundos"
     * seria mentir y lo haria insistir contra una cuota que no vuelve. Auditoria NFR 2026-09-06.
     */
    public static final String MENSAJE_PROVEEDOR_SATURADO =
            "El asistente esta saturado en este momento. Intenta de nuevo en unos minutos.";
    /** Deja el texto de apoyo separado de lo ultimo que dijo el modelo, en vez de pegado. */
    static final String SEPARACION_DEL_APOYO = "\n\n";
    /**
     * Lo que ve una app sin botones (y el historial) por cada propuesta. Dice "Propuesta" y no
     * "Listo": todavia no se ejecuto nada. No menciona botones porque la app vieja no los tiene.
     */
    static final String ENCABEZADO_DE_PROPUESTA = "\n\nPropuesta: ";

    private final UserSummaryFinder userSummaryFinder;
    private final ControlCuotaRenasiaPort controlCuotaRenasiaPort;
    private final LoadConversacionRenasiaPort loadConversacionRenasiaPort;
    private final SaveConversacionRenasiaPort saveConversacionRenasiaPort;
    private final LoadMensajeRenasiaPort loadMensajeRenasiaPort;
    private final SaveMensajeRenasiaPort saveMensajeRenasiaPort;
    private final VectorStorePort vectorStorePort;
    private final ConsultarLeccionesVisiblesPort consultarLeccionesVisiblesPort;
    private final ChatIAPort chatIAPort;
    /** Que puede HACER el agente, ademas de responder (2026-09-05). Solo se le piden las
     * definiciones: la ejecucion la dispara el adaptador del proveedor cuando el modelo pida una,
     * y hoy no hay ninguno conectado. */
    private final EjecutarHerramientaAgenteUseCase herramientasUseCase;
    private final ConsultarSituacionDelAprendizPort situacionPort;
    /** Si la persona viene repitiendo expresiones de malestar (2026-09-15). Ver
     * {@link #textoDeApoyo}: no clasifica ni diagnostica nada, cuenta repeticiones. */
    private final RevisarPatronDeMalestarUseCase revisarPatronDeMalestarUseCase;
    private final ConsultarPropuestasDelTurnoUseCase propuestasDelTurno;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ConversacionRenasiaService(UserSummaryFinder userSummaryFinder,
                                       ControlCuotaRenasiaPort controlCuotaRenasiaPort,
                                       LoadConversacionRenasiaPort loadConversacionRenasiaPort,
                                       SaveConversacionRenasiaPort saveConversacionRenasiaPort,
                                       LoadMensajeRenasiaPort loadMensajeRenasiaPort,
                                       SaveMensajeRenasiaPort saveMensajeRenasiaPort, VectorStorePort vectorStorePort,
                                       ConsultarLeccionesVisiblesPort consultarLeccionesVisiblesPort,
                                       ChatIAPort chatIAPort, EjecutarHerramientaAgenteUseCase herramientasUseCase,
                                       ConsultarSituacionDelAprendizPort situacionPort,
                                       RevisarPatronDeMalestarUseCase revisarPatronDeMalestarUseCase,
                                       ConsultarPropuestasDelTurnoUseCase propuestasDelTurno,
                                       Clock clock, IdGenerator idGenerator) {
        this.userSummaryFinder = userSummaryFinder;
        this.controlCuotaRenasiaPort = controlCuotaRenasiaPort;
        this.loadConversacionRenasiaPort = loadConversacionRenasiaPort;
        this.saveConversacionRenasiaPort = saveConversacionRenasiaPort;
        this.loadMensajeRenasiaPort = loadMensajeRenasiaPort;
        this.saveMensajeRenasiaPort = saveMensajeRenasiaPort;
        this.vectorStorePort = vectorStorePort;
        this.consultarLeccionesVisiblesPort = consultarLeccionesVisiblesPort;
        this.chatIAPort = chatIAPort;
        this.herramientasUseCase = herramientasUseCase;
        this.situacionPort = situacionPort;
        this.revisarPatronDeMalestarUseCase = revisarPatronDeMalestarUseCase;
        this.propuestasDelTurno = propuestasDelTurno;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public Flux<EventoRenasia> preguntar(PreguntarRenasiaCommand command) {
        requireActivo(command.actorId());
        requireCuotaDisponible(command.actorId());
        // Antes de cualquier otra cosa del turno: toda propuesta de este turno nace despues.
        Instant inicioDelTurno = clock.now();

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
        // ANTES de hablar con el modelo, nunca durante: la revision abre su propia transaccion
        // corta (la necesita el outbox de Modulith) y ningun puerto de IA puede correr dentro de
        // ella (regla 01, C-1).
        String apoyo = textoDeApoyo(command);

        StringBuilder respuestaCompleta = new StringBuilder();
        Flux<EventoRenasia> delModelo = chatIAPort.responder(new Consulta(command.agente(), command.actorId(),
                command.pregunta(), contexto, command.ambito(), historial,
                herramientasUseCase.disponibles(command.agente()),
                situacionPort.de(command.actorId()).orElse(null)));
        return conApoyoAntesDelFin(conPropuestasAntesDelFin(delModelo, command.actorId(), inicioDelTurno), apoyo)
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
                .onErrorResume(error -> Flux.just(new EventoRenasia.Error(mensajeParaLaPersona(error)),
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
     * Si la persona viene repitiendo expresiones de malestar, el texto de apoyo que hay que
     * mostrarle; cadena vacia en cualquier otro caso.
     *
     * <p><b>Best-effort a proposito.</b> Un fallo revisando el patron —una consulta que se cae, un
     * dato raro— no puede dejar a alguien sin la respuesta que vino a buscar. Se registra y la
     * conversacion sigue. La revision se repite en el mensaje siguiente y, como la cuenta se deriva
     * de los mensajes guardados y no de un contador, no se pierde nada por haberla salteado una vez.
     */
    private String textoDeApoyo(PreguntarRenasiaCommand command) {
        try {
            return revisarPatronDeMalestarUseCase.revisar(command.actorId(), command.pregunta()).orElse("");
        } catch (RuntimeException e) {
            log.warn("No se pudo revisar la repeticion de expresiones de malestar; la conversacion sigue normal", e);
            return "";
        }
    }

    /**
     * Agrega el texto de apoyo como un fragmento mas de la respuesta, justo antes del {@code fin}.
     *
     * <p><b>Por que como {@link EventoRenasia.Texto} y no como una variante nueva del evento.</b>
     * El contrato SSE es de la app movil (docs/MODULO_RAG.md §4.bis) y un {@code tipo} que el
     * cliente no conoce lo ignora en silencio — o sea, la persona no veria nada, que es exactamente
     * lo contrario de lo que este camino existe para hacer. Va por el canal que el cliente ya
     * dibuja. El dia que haya una variante propia (con su tratamiento visual), este es el unico
     * metodo que cambia.
     *
     * <p>Como se inserta ANTES del {@code doOnNext} que acumula el texto, queda tambien en el
     * mensaje del asistente que se persiste: al volver al chat, la persona lo vuelve a encontrar en
     * vez de que se haya evaporado.
     *
     * <p><b>Si el modelo falla, este turno no lo muestra</b> — el {@code onErrorResume} de mas
     * abajo reemplaza el stream entero y no hay respuesta donde ponerlo. El aviso a quien pueda
     * actuar ya se emitio igual, y el proximo turno que si responda vuelve a ofrecerlo: la cuenta
     * se deriva de mensajes guardados, asi que el patron sigue dandose.
     */
    private static Flux<EventoRenasia> conApoyoAntesDelFin(Flux<EventoRenasia> respuesta, String apoyo) {
        if (apoyo.isBlank()) {
            return respuesta;
        }
        // "\n\n" literal y no System.lineSeparator(): esto viaja dentro de un JSON hacia un
        // telefono, no se escribe en un archivo del servidor — el separador del SO no pinta nada.
        return respuesta.concatMap(evento -> evento instanceof EventoRenasia.Fin
                ? Flux.just(new EventoRenasia.Texto(SEPARACION_DEL_APOYO + apoyo), evento)
                : Flux.just(evento));
    }

    /**
     * Las propuestas que nacieron en este turno, justo antes del {@code fin} del modelo: por cada
     * una, primero un {@link EventoRenasia.Texto} con su resumen y despues el
     * {@link EventoRenasia.Propuesta} con el que la app dibuja los botones.
     *
     * <p><b>Por que el texto ademas del evento.</b> La app no se actualiza por aire y un
     * {@code tipo} que no conoce lo ignora en silencio: sin el texto, quien no reinstalo no se
     * enteraria de que hubo una propuesta. Va ANTES del {@code doOnNext} que acumula, asi que queda
     * en el mensaje guardado: al volver al chat (donde los botones ya no se redibujan) la persona
     * sigue viendo que se propuso, y el modelo, en el turno siguiente, sabe que ya lo propuso.
     *
     * <p><b>Cuando corre la consulta.</b> Recien al llegar el {@code fin}, o sea cuando el modelo ya
     * termino de hablar y de llamar herramientas: ninguna conexion queda retenida durante la
     * llamada al proveedor (C-1). Best-effort, como {@link #textoDeApoyo}: si falla, se registra y
     * el turno termina normal; la propuesta sigue guardada y vence sola.
     *
     * <p>Si el modelo falla, el {@code onErrorResume} reemplaza el stream y este turno no las
     * muestra.
     */
    private Flux<EventoRenasia> conPropuestasAntesDelFin(Flux<EventoRenasia> respuesta, UserId actorId,
                                                         Instant inicioDelTurno) {
        return respuesta.concatMap(evento -> evento instanceof EventoRenasia.Fin
                ? Flux.concat(Flux.fromIterable(eventosDePropuestas(actorId, inicioDelTurno)), Flux.just(evento))
                : Flux.just(evento));
    }

    /** Tambien atrapa una propuesta con datos invalidos: no puede convertir en error una respuesta
     * que el modelo ya dio bien (el {@code onErrorResume} mandaria un {@code error} y liberaria la
     * cuota de un turno que si se respondio). */
    private List<EventoRenasia> eventosDePropuestas(UserId actorId, Instant inicioDelTurno) {
        try {
            List<EventoRenasia> eventos = new java.util.ArrayList<>();
            for (PropuestaCreada propuesta : propuestasDelTurno.pendientesCreadasDesde(actorId, inicioDelTurno)) {
                eventos.add(new EventoRenasia.Texto(ENCABEZADO_DE_PROPUESTA + propuesta.resumen()));
                eventos.add(new EventoRenasia.Propuesta(propuesta.id(), propuesta.resumen(), propuesta.venceEn()));
            }
            return eventos;
        } catch (RuntimeException e) {
            log.warn("No se pudieron recoger las propuestas del turno; la respuesta termina sin ellas", e);
            return List.of();
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
        return soloTurnosRespondidos(recientes);
    }

    /**
     * <b>A la memoria solo entran los turnos que fueron respondidos</b> (2026-09-15, D-132).
     *
     * <p>El caso real que lo motiva: alguien escribio <i>"marca como completado el habito jugo
     * verde"</i>, el modelo no llego a contestar —la respuesta tarda entre 12 y 30 segundos y esa
     * vez se corto—, y el mensaje quedo guardado igual. Al dia siguiente, un <i>"Hola"</i> volvio
     * a mandar ese pedido al modelo como si fuera parte de la conversacion: el agente lo leyo como
     * una instruccion pendiente, <b>llamo a la herramienta y cerro el habito</b>. Un saludo marco
     * un habito que nadie pidio marcar ese dia. Verificado en la base el 2026-09-15: el registro
     * quedo en COMPLETADO a los 11 segundos del saludo.
     *
     * <p>Un mensaje de usuario sin respuesta <b>no es un turno de conversacion</b>: es un intento
     * que fallo. Dejarlo en el contexto es pedirle al modelo que adivine si sigue vigente — y con
     * herramientas de escritura disponibles, esa adivinanza escribe en la base.
     *
     * <p>No se borra nada: el mensaje sigue en {@code mensajes_renasia} y se sigue viendo en el
     * historial de la pantalla, que es la conversacion real de la persona. Lo que cambia es
     * unicamente <b>que se le manda al modelo</b>.
     */
    private static List<MensajeRenasia> soloTurnosRespondidos(List<MensajeRenasia> cronologicos) {
        List<MensajeRenasia> completos = new java.util.ArrayList<>(cronologicos.size());
        for (int i = 0; i < cronologicos.size(); i++) {
            MensajeRenasia mensaje = cronologicos.get(i);
            boolean loSiguienteEsLaRespuesta = i + 1 < cronologicos.size()
                    && cronologicos.get(i + 1).rol() == RolMensaje.ASISTENTE;
            if (mensaje.rol() != RolMensaje.USUARIO || loSiguienteEsLaRespuesta) {
                completos.add(mensaje);
            }
        }
        return List.copyOf(completos);
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
     * docs/MODULO_RAG.md D-47). Tampoco el id del actor (es el `sub` de Supabase).
     *
     * <p><b>Sin traza cuando el proveedor esta caido o saturado (2026-09-14).</b> Un 503 de Google
     * ("this model is currently experiencing high demand") no es un defecto nuestro: es una
     * condicion transitoria que este servicio YA maneja — la traduce, se la explica a la persona y
     * libera la cuota. Imprimir 150 lineas de pila de Reactor por cada una no agrega ni un dato
     * accionable, y entierra los errores que si lo son.
     *
     * <p>Lo mismo que se hizo con el ruido de avisos duplicados: lo esperado se resume en una
     * linea, lo inesperado conserva la traza entera. Si algun dia falla por un defecto real —un
     * NPE, un contrato roto— la pila sigue apareciendo completa, que es cuando hace falta. */
    private void logFalloDeStreaming(Throwable error) {
        if (error instanceof ProveedorIaNoDisponibleException) {
            log.warn("El proveedor de IA no respondio ({}). El aprendiz recibio el aviso y su cuota se libero.",
                    error.getMessage());
            return;
        }
        log.warn("Fallo el streaming de respuesta del asistente", error);
    }

    /**
     * El stream ya arranco con 200 cuando el modelo falla, asi que el unico canal para avisar es
     * el texto del evento de error. Se distingue "el proveedor no puede ahora" del resto para que
     * el mensaje no invite a reintentar enseguida algo que no va a funcionar.
     */
    private static String mensajeParaLaPersona(Throwable error) {
        return error instanceof ProveedorIaNoDisponibleException ? MENSAJE_PROVEEDOR_SATURADO : MENSAJE_ERROR_MODELO;
    }
}
