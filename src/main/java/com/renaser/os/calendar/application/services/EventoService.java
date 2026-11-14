package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CancelarOcurrenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ConfirmarPortadaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.EliminarEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.EventoVista;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ObtenerEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.SolicitarUrlPortadaUseCase;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.evento.SaveEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.SaveExcepcionPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Excepcion;
import com.renaser.os.calendar.domain.model.evento.ExpansorOcurrencias;
import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.FormacionDelContenido;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.calendar.application.ports.in.audiencia.AudienciaRechazadaException;
import com.renaser.os.calendar.application.ports.in.audiencia.PedidoDeAudiencia;
import com.renaser.os.calendar.application.ports.out.audiencia.AudienciaElegidaPort;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.audiencia.AudienciaElegida;
import com.renaser.os.calendar.domain.model.evento.audiencia.QuienConvoca;
import com.renaser.os.shared.application.Interruptores;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class EventoService implements ListarEventosParaVisorUseCase, ObtenerEventoUseCase, CrearEventoUseCase,
        ActualizarEventoUseCase, EliminarEventoUseCase, CancelarOcurrenciaUseCase, SolicitarUrlPortadaUseCase,
        ConfirmarPortadaUseCase {

    private static final Logger log = LoggerFactory.getLogger(EventoService.class);

    /** Tolerancia de comparacion de instantes al validar que un `occurrenceStart` recibido
     * corresponde a una ocurrencia real de la serie — mismo margen que isRealOccurrence()
     * del repo viejo (service.ts, 180_000 ms). */
    private static final long TOLERANCIA_OCURRENCIA_MS = 180_000;
    static final String BUCKET_PORTADAS = "renaser-files";
    private static final String PREFIJO_RUTA = "calendar";
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);
    private static final Duration VALIDEZ_URL_LECTURA_PORTADA = Duration.ofHours(1);

    private final LoadEventoPort loadEventoPort;
    private final SaveEventoPort saveEventoPort;
    private final LoadExcepcionPort loadExcepcionPort;
    private final SaveExcepcionPort saveExcepcionPort;
    private final LoadConfirmacionPort loadConfirmacionPort;
    private final SaveRecordatorioPort saveRecordatorioPort;
    private final LoadNivelMembresiaPort nivelPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final AccesoEventoService accesoEventoService;
    private final Clock clock;
    private final IdGenerator idGenerator;
    /** Spec 044: la audiencia elegida (las reglas y lo guardado) y el interruptor {@code eventos.convocar}. {@code null} en pruebas viejas. */
    private final AudienciaElegidaService audienciaElegida;
    private final AudienciaElegidaPort audienciaPort;
    private final Interruptores interruptores;

    /** Interruptor de Soporte TI de los eventos para personas elegidas (spec 044, FR-028). */
    static final String CLAVE_CONVOCAR = "eventos.convocar";
    /** Interruptor de Administración del calendario (lo de siempre). */
    static final String CLAVE_GESTION = "eventos.gestion";

    public EventoService(LoadEventoPort loadEventoPort, SaveEventoPort saveEventoPort,
                          LoadExcepcionPort loadExcepcionPort, SaveExcepcionPort saveExcepcionPort,
                          LoadConfirmacionPort loadConfirmacionPort, SaveRecordatorioPort saveRecordatorioPort,
                          LoadNivelMembresiaPort nivelPort, AlmacenamientoPort almacenamientoPort,
                          AccesoEventoService accesoEventoService, Clock clock, IdGenerator idGenerator) {
        this(loadEventoPort, saveEventoPort, loadExcepcionPort, saveExcepcionPort, loadConfirmacionPort, saveRecordatorioPort, nivelPort,
                almacenamientoPort, accesoEventoService, clock, idGenerator, null, null, null);
    }

    @Autowired
    public EventoService(LoadEventoPort loadEventoPort, SaveEventoPort saveEventoPort,
                          LoadExcepcionPort loadExcepcionPort, SaveExcepcionPort saveExcepcionPort,
                          LoadConfirmacionPort loadConfirmacionPort, SaveRecordatorioPort saveRecordatorioPort,
                          LoadNivelMembresiaPort nivelPort, AlmacenamientoPort almacenamientoPort,
                          AccesoEventoService accesoEventoService, Clock clock, IdGenerator idGenerator,
                          AudienciaElegidaService audienciaElegida, AudienciaElegidaPort audienciaPort, Interruptores interruptores) {
        this.audienciaElegida = audienciaElegida;
        this.audienciaPort = audienciaPort;
        this.interruptores = interruptores;
        this.loadEventoPort = loadEventoPort;
        this.saveEventoPort = saveEventoPort;
        this.loadExcepcionPort = loadExcepcionPort;
        this.saveExcepcionPort = saveExcepcionPort;
        this.loadConfirmacionPort = loadConfirmacionPort;
        this.saveRecordatorioPort = saveRecordatorioPort;
        this.nivelPort = nivelPort;
        this.almacenamientoPort = almacenamientoPort;
        this.accesoEventoService = accesoEventoService;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<OcurrenciaVista> listar(UserId actorId, Instant desde, Instant hasta, Formacion formacion) {
        ProgresoParticipanteCalendar progreso = accesoEventoService.requireProgreso(actorId);
        var visor = accesoEventoService.buildVisor(progreso);
        AlcanceDeFormacion alcance = accesoEventoService.alcanceDe(actorId, formacion);

        List<Evento> candidatos = loadEventoPort.candidatosParaVisor(desde, hasta).stream()
                .filter(e -> alcance.incluye(e.formacion())).toList();
        Map<EventoId, AudienciaElegida> elegidas = accesoEventoService.precargar(candidatos);
        List<Evento> visibles = candidatos.stream()
                .filter(e -> accesoEventoService.puedeAcceder(actorId, progreso, visor, e, elegidas))
                .toList();
        Set<EventoId> visibleIds = visibles.stream().map(Evento::id).collect(java.util.stream.Collectors.toSet());

        Map<EventoId, List<Excepcion>> excepcionesPorEvento = loadExcepcionPort.porEventos(visibleIds);
        Map<String, EstadoConfirmacion> confirmacionesPorVisor = loadConfirmacionPort.paraVisor(actorId, visibleIds);

        List<OcurrenciaVista> resultado = new ArrayList<>();
        for (Evento evento : visibles) {
            List<Excepcion> excepciones = excepcionesPorEvento.getOrDefault(evento.id(), List.of());
            List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(evento.iniciaEn(), evento.duracionMinutos(),
                    evento.timezone(), evento.recurrencia(), desde, hasta, excepciones);
            String coverUrl = coverUrlDe(evento);
            for (Ocurrencia occ : ocurrencias) {
                String clave = evento.id() + "|" + occ.inicioOcurrencia();
                resultado.add(new OcurrenciaVista(evento, coverUrl, occ.inicioOcurrencia(), occ.iniciaEn(),
                        occ.duracionMinutos(), occ.titulo() != null ? occ.titulo() : evento.titulo(),
                        confirmacionesPorVisor.get(clave)));
            }
        }
        resultado.sort((a, b) -> a.iniciaEn().compareTo(b.iniciaEn()));
        return resultado;
    }

    @Override
    public EventoVista obtener(UserId actorId, EventoId eventoId) {
        ProgresoParticipanteCalendar progreso = accesoEventoService.requireProgreso(actorId);
        Evento evento = requireEvento(eventoId);
        accesoEventoService.requireEnSuFormacion(actorId, evento);
        var visor = accesoEventoService.buildVisor(progreso);
        if (!accesoEventoService.puedeAcceder(actorId, progreso, visor, evento)) {
            throw new NotAuthorizedException("No tienes acceso a este evento");
        }
        return new EventoVista(evento, coverUrlDe(evento));
    }

    @Override
    @Transactional
    public EventoVista crear(CrearEventoCommand cmd) {
        ProgresoParticipanteCalendar progreso = accesoEventoService.requireProgreso(cmd.actorId());
        RolUsuario rol = progreso.rol();
        boolean elegida = cmd.tipoAudiencia() == TipoAudiencia.SELECCION;
        requirePuedeCrear(cmd.actorId(), rol, elegida, cmd.tipoEvento(), true);
        requireNivelExiste(cmd.tipoAudiencia(), cmd.nivelMinimoId());
        // Spec 024 (FR-017): Administración no pertenece a una formación; elige siempre en cuál nace el evento.
        Formacion donde = FormacionDelContenido.paraCrear(accesoEventoService.alcanceDe(cmd.actorId(), null), cmd.formacion());

        AudienciaElegida audiencia = null;
        if (elegida) {
            List<com.renaser.os.calendar.domain.model.evento.audiencia.ParteDeAudiencia> partes =
                    cmd.audiencia() == null ? List.of() : cmd.audiencia().partes();
            audiencia = audienciaElegida.armar(cmd.actorId(), rol, donde, partes, rol == RolUsuario.MENTOR ? cmd.actorId() : null,
                    AudienciaElegida.vacia(), true, clock.now());
        }

        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
        Evento evento = Evento.crear(EventoId.of(idGenerator.newId()), cmd.titulo(), cmd.descripcion(),
                cmd.iniciaEn(), cmd.duracionMinutos(), cmd.timezone(), cmd.tipoUbicacion(), cmd.valorUbicacion(),
                cmd.tipoAudiencia(), cmd.nivelMinimoId(), cmd.cursoId(), cmd.celulaDestinoId(), cmd.tipoEvento(),
                cmd.notificarAlCrear(), cmd.recordarPorEmail(), cmd.recordatoriosPersonalizados(), cmd.recurrencia(),
                cmd.rolesDestino(), cmd.reglasRecordatorio(), cmd.actorId(), clock, donde, cmd.accesoSalaMeet());
        Evento guardado = saveEventoPort.guardar(evento);
        if (audiencia != null) {
            audienciaPort.reemplazar(guardado.id(), audiencia);
        }
        return new EventoVista(guardado, coverUrlDe(guardado));
    }

    @Override
    @Transactional
    public EventoVista actualizar(ActualizarEventoCommand cmd) {
        Evento evento = requireEvento(cmd.eventoId());
        ProgresoParticipanteCalendar progreso = accesoEventoService.requireProgreso(cmd.actorId());
        RolUsuario rol = progreso.rol();
        requireGestiona(cmd.actorId(), evento);
        boolean elegida = cmd.tipoAudiencia() == TipoAudiencia.SELECCION;
        // Spec 053: el tipo es inmutable y ya se aprobó al crear; el líder sigue editando los de sesión especial de antes y los de sus mentores.
        requirePuedeCrear(cmd.actorId(), rol, elegida, evento.tipoEvento(), false);
        requireNivelExiste(cmd.tipoAudiencia(), cmd.nivelMinimoId());

        AudienciaElegida nueva = audienciaAlEditar(cmd, evento, rol);

        evento.actualizar(cmd.titulo(), cmd.descripcion(), cmd.iniciaEn(), cmd.duracionMinutos(), cmd.timezone(),
                cmd.tipoUbicacion(), cmd.valorUbicacion(), cmd.tipoAudiencia(), cmd.nivelMinimoId(), cmd.cursoId(),
                cmd.celulaDestinoId(), cmd.notificarAlCrear(), cmd.recordarPorEmail(),
                cmd.recordatoriosPersonalizados(), cmd.recurrencia(), cmd.rolesDestino(), cmd.reglasRecordatorio(),
                clock, cmd.accesoSalaMeet());
        Evento guardado = saveEventoPort.guardar(evento);
        if (nueva != null) {
            audienciaPort.reemplazar(guardado.id(), nueva);
        }

        // Los avisos ya generados nacieron de la version ANTERIOR — fuera del camino de
        // error a proposito (el evento ya se guardo), mismo criterio que updateEvent() del
        // repo viejo: si esto falla, el cron los recalcula igual en su siguiente pasada.
        // Spec 044 (FR-022): es tambien lo que deja sin avisos a quien salio de la audiencia elegida.
        try {
            saveRecordatorioPort.borrarPendientesFuturos(cmd.eventoId(), clock.now());
        } catch (RuntimeException ex) {
            log.warn("[EventoService.actualizar] no se pudieron limpiar los avisos pendientes de {}: {}",
                    cmd.eventoId(), ex.getMessage());
        }
        return new EventoVista(guardado, coverUrlDe(guardado));
    }

    /**
     * La audiencia elegida que queda al editar (spec 044): {@code null} = no se toca lo guardado. Sin {@code audience} en el pedido se conserva la
     * guardada (FR-030: una app anterior reenvía el evento sin ella); un evento que deja de ser {@code SELECCION} se queda sin partes.
     */
    private AudienciaElegida audienciaAlEditar(ActualizarEventoCommand cmd, Evento evento, RolUsuario rol) {
        boolean eraElegida = evento.tipoAudiencia() == TipoAudiencia.SELECCION;
        boolean esElegida = cmd.tipoAudiencia() == TipoAudiencia.SELECCION;
        if (!esElegida) {
            return eraElegida ? AudienciaElegida.vacia() : null;
        }
        PedidoDeAudiencia pedido = cmd.audiencia();
        AudienciaElegida previa = eraElegida ? audienciaPort.de(evento.id()) : AudienciaElegida.vacia();
        if (pedido == null || pedido.partes().isEmpty()) {
            if (eraElegida) {
                return null;
            }
            throw AudienciaRechazadaException.invalida("Elige para quién es el evento.");
        }
        UserId mentor = creadorActuaComoMentor(evento) ? evento.creadoPor() : null;
        return audienciaElegida.armar(cmd.actorId(), rol, evento.formacion(), pedido.partes(), mentor, previa, pedido.recalcular(),
                clock.now());
    }

    private boolean creadorActuaComoMentor(Evento evento) {
        return evento.creadoPor() != null && accesoEventoService.rolDe(evento.creadoPor()).filter(r -> r == RolUsuario.MENTOR).isPresent();
    }

    @Override
    @Transactional
    public void eliminar(UserId actorId, EventoId eventoId) {
        Evento evento = requireEvento(eventoId);
        requireGestiona(actorId, evento);

        if (evento.portadaRuta() != null) {
            try {
                almacenamientoPort.borrar(evento.portadaRuta());
            } catch (RuntimeException ex) {
                log.warn("[EventoService.eliminar] no se pudo borrar la portada de {}: {}", eventoId, ex.getMessage());
            }
        }
        saveEventoPort.eliminar(eventoId);
    }

    @Override
    @Transactional
    public void cancelar(UserId actorId, EventoId eventoId, Instant inicioOcurrencia) {
        Evento evento = requireEvento(eventoId);
        requireGestiona(actorId, evento);

        if (!evento.esRecurrente()) {
            throw new IllegalArgumentException(
                    "Este evento no es recurrente — eliminalo en vez de cancelar una ocurrencia");
        }
        if (!esOcurrenciaReal(evento, inicioOcurrencia)) {
            throw new IllegalArgumentException("inicioOcurrencia no corresponde a una ocurrencia real de este evento");
        }

        // upsertOverride() del repo viejo: crea o reemplaza el override por (eventoId, inicioOcurrencia) —
        // UNIQUE (evento_id, inicio_ocurrencia), el adaptador de persistencia hace el upsert real.
        var excepcionExistente = loadExcepcionPort.porEvento(eventoId).stream()
                .filter(e -> e.inicioOcurrencia().equals(inicioOcurrencia)).findFirst();
        Excepcion excepcion = excepcionExistente
                .map(e -> new Excepcion(e.id(), eventoId, inicioOcurrencia, true, null, null, null))
                .orElseGet(() -> Excepcion.cancelar(idGenerator.newId(), eventoId, inicioOcurrencia));
        saveExcepcionPort.upsert(excepcion);

        try {
            saveRecordatorioPort.cancelarPorOcurrencia(eventoId, inicioOcurrencia, RecordatorioEvento.MOTIVO_EVENTO_CANCELADO);
        } catch (RuntimeException ex) {
            log.warn("[EventoService.cancelar] no se pudieron cancelar los avisos de {}/{}: {}", eventoId,
                    inicioOcurrencia, ex.getMessage());
        }
    }

    @Override
    public UrlPortada solicitar(UserId actorId, EventoId eventoId, String tipoContenido) {
        // 404 si no existe: no se firma subida para un evento inexistente
        requireGestiona(actorId, requireEvento(eventoId));

        String ruta = PREFIJO_RUTA + "/" + eventoId + "/portada-" + clock.now().toEpochMilli();
        URI url = almacenamientoPort.firmarSubida(ruta, tipoContenido, VALIDEZ_URL_SUBIDA);
        return new UrlPortada(url, BUCKET_PORTADAS, ruta);
    }

    @Override
    @Transactional
    public EventoVista confirmar(UserId actorId, EventoId eventoId, String ruta) {
        Evento evento = requireEvento(eventoId);
        requireGestiona(actorId, evento);

        evento.fijarPortada(exigirPortadaDeEsteEvento(ruta, eventoId));
        Evento guardado = saveEventoPort.guardar(evento);
        return new EventoVista(guardado, coverUrlDe(guardado));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /**
     * La portada tiene que ser un objeto DE ESTE evento.
     *
     * <p><b>El agujero que cierra (2026-09-18).</b> {@code confirmar} guardaba la ruta que mandara
     * el cliente, sin mirarla. Esa misma ruta se usa despues en DOS lugares peligrosos: se firma
     * para lectura en {@code coverUrlDe}, y se BORRA del bucket en {@code eliminar}. Como hay un
     * solo bucket fisico, quien administrara el calendario (entonces tambien un MENTOR, sobre sus
     * propios eventos) podia leer o borrar cualquier objeto del sistema —evidencias, firmas de
     * contrato, avatares— apuntando su portada ahi y despues eliminando el evento.
     *
     * <p>Se exige el evento y no solo el prefijo del modulo: la operacion es sobre ESTE evento,
     * asi que ese es exactamente el alcance que el actor tiene derecho a tocar. Coincide con lo
     * que emite {@code solicitar}, que arma
     * {@code <prefijo>/<eventoId>/portada-<instante>}: nada que el sistema haya firmado queda fuera.
     *
     * <p>{@code null} sigue siendo valido: es como se quita una portada.
     *
     * @throws IllegalArgumentException si la ruta apunta a otro evento o fuera del modulo.
     */
    private static String exigirPortadaDeEsteEvento(String ruta, EventoId eventoId) {
        if (ruta == null) {
            return null;
        }
        String esperado = PREFIJO_RUTA + "/" + eventoId + "/";
        if (!ruta.startsWith(esperado)) {
            throw new IllegalArgumentException(
                    "La portada tiene que ser un objeto de este evento (" + esperado + ")");
        }
        return ruta;
    }

    /** Resuelve la URL de LECTURA de la portada aca (no en el controller): el controller
     * nunca depende de un puerto `out` (ArchitectureTest.controllersDoNotTouchPersistence). */
    private String coverUrlDe(Evento evento) {
        if (evento.portadaRuta() == null) {
            return null;
        }
        return almacenamientoPort.firmarLectura(evento.portadaRuta(), VALIDEZ_URL_LECTURA_PORTADA).toString();
    }

    @Override
    @Transactional
    public void cancelarDesde(UserId actorId, EventoId eventoId, Instant inicioOcurrencia) {
        Evento evento = requireEvento(eventoId);
        requireGestiona(actorId, evento);
        if (!evento.esRecurrente()) {
            throw new IllegalArgumentException("Este evento no es recurrente — eliminalo en vez de cancelar las siguientes");
        }
        if (!esOcurrenciaReal(evento, inicioOcurrencia)) {
            throw new IllegalArgumentException("inicioOcurrencia no corresponde a una ocurrencia real de este evento");
        }
        evento.terminarLaSerieAntesDe(inicioOcurrencia, clock);
        saveEventoPort.guardar(evento);
        try {
            // Los avisos pendientes nacieron de la serie larga: se borran y el cron regenera los de las fechas que quedan.
            saveRecordatorioPort.borrarPendientesFuturos(eventoId, clock.now());
        } catch (RuntimeException ex) {
            log.warn("[EventoService.cancelarDesde] no se pudieron limpiar los avisos pendientes de {}: {}", eventoId, ex.getMessage());
        }
    }

    private Evento requireEvento(EventoId eventoId) {
        return loadEventoPort.byId(eventoId)
                .orElseThrow(() -> new NoSuchElementException("Evento no encontrado: " + eventoId));
    }

    /**
     * Quién crea (spec 044, FR-001, FR-007 y FR-028). ADMIN y ALCHEMIST, lo de siempre con {@code eventos.gestion}; con audiencia elegida, además
     * {@code eventos.convocar}. El líder, el mentor y la guía, solo con {@code eventos.convocar}, solo con audiencia elegida y solo «Sesión especial» o
     * «Espontáneo». El aprendiz, nunca.
     *
     * <blockquote><b>Corregido 2026-11-06 (spec 044).</b> Desde D-186 (2026-09-26) crear era solo de ADMIN y ALCHEMIST y el mentor no podía. El dueño
     * pidió que el líder, el mentor y la guía vuelvan a crear eventos, cada uno para su gente.</blockquote>
     */
    private void requirePuedeCrear(UserId actorId, RolUsuario rol, boolean conAudienciaElegida, TipoEvento tipo, boolean verificarTipo) {
        if (!QuienConvoca.puedeCrear(rol)) {
            throw new NotAuthorizedException("No tienes permiso para crear eventos");
        }
        boolean administra = QuienConvoca.administra(rol);
        if (interruptores != null) {
            if (administra) {
                interruptores.exigirPrendidaPara(CLAVE_GESTION, actorId);
            }
            if (!administra || conAudienciaElegida) {
                interruptores.exigirPrendidaPara(CLAVE_CONVOCAR, actorId);
            }
        }
        if (administra) {
            return;
        }
        if (!conAudienciaElegida) {
            throw new NotAuthorizedException("Elige para quién es el evento");
        }
        if (verificarTipo && !QuienConvoca.tiposDe(rol).contains(tipo)) {
            throw new NotAuthorizedException("No puedes crear un evento de ese tipo");
        }
    }

    /** FR-006: quien lo creó, Administración, Alquimista, o el líder de quien lo creó. */
    private void requireGestiona(UserId actorId, Evento evento) {
        accesoEventoService.requireGestiona(actorId, evento);
        if (interruptores != null) {
            interruptores.exigirPrendidaPara(accesoEventoService.administraElCalendario(actorId) ? CLAVE_GESTION : CLAVE_CONVOCAR, actorId);
        }
    }

    private void requireNivelExiste(TipoAudiencia tipoAudiencia, Integer nivelMinimoId) {
        if (tipoAudiencia != TipoAudiencia.NIVEL_MINIMO || nivelMinimoId == null) {
            return;
        }
        boolean existe = nivelPort.listar().stream().anyMatch(n -> n.id() == nivelMinimoId);
        if (!existe) {
            throw new IllegalArgumentException("El nivel mínimo indicado no existe: " + nivelMinimoId);
        }
    }

    /** isRealOccurrence() del repo viejo. */
    private boolean esOcurrenciaReal(Evento evento, Instant inicioOcurrencia) {
        if (!evento.esRecurrente()) {
            return Math.abs(evento.iniciaEn().toEpochMilli() - inicioOcurrencia.toEpochMilli()) <= TOLERANCIA_OCURRENCIA_MS;
        }
        Instant desde = inicioOcurrencia.minusSeconds(86_400);
        Instant hasta = inicioOcurrencia.plusSeconds(86_400);
        List<Ocurrencia> coincidencias = ExpansorOcurrencias.expandir(evento.iniciaEn(), evento.duracionMinutos(),
                evento.timezone(), evento.recurrencia(), desde, hasta, List.of());
        return coincidencias.stream().anyMatch(o ->
                Math.abs(o.inicioOcurrencia().toEpochMilli() - inicioOcurrencia.toEpochMilli()) <= TOLERANCIA_OCURRENCIA_MS);
    }
}
