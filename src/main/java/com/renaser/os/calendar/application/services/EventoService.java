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
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
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
import java.util.UUID;

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

    public EventoService(LoadEventoPort loadEventoPort, SaveEventoPort saveEventoPort,
                          LoadExcepcionPort loadExcepcionPort, SaveExcepcionPort saveExcepcionPort,
                          LoadConfirmacionPort loadConfirmacionPort, SaveRecordatorioPort saveRecordatorioPort,
                          LoadNivelMembresiaPort nivelPort, AlmacenamientoPort almacenamientoPort,
                          AccesoEventoService accesoEventoService, Clock clock) {
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
    }

    @Override
    public List<OcurrenciaVista> listar(UserId actorId, Instant desde, Instant hasta) {
        ProgresoParticipanteCalendar progreso = accesoEventoService.requireProgreso(actorId);
        var visor = accesoEventoService.buildVisor(progreso);

        List<Evento> candidatos = loadEventoPort.candidatosParaVisor(desde, hasta);
        List<Evento> visibles = candidatos.stream()
                .filter(e -> accesoEventoService.puedeAcceder(actorId, progreso, visor, e))
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
        var visor = accesoEventoService.buildVisor(progreso);
        if (!accesoEventoService.puedeAcceder(actorId, progreso, visor, evento)) {
            throw new NotAuthorizedException("No tienes acceso a este evento");
        }
        return new EventoVista(evento, coverUrlDe(evento));
    }

    @Override
    @Transactional
    public EventoVista crear(CrearEventoCommand cmd) {
        ProgresoParticipanteCalendar progreso = requireRolCreador(cmd.actorId());

        TipoAudiencia tipoAudiencia = cmd.tipoAudiencia();
        Integer nivelMinimoId = cmd.nivelMinimoId();
        String cursoId = cmd.cursoId();
        UUID celulaDestinoId = cmd.celulaDestinoId();
        Set<RolUsuario> rolesDestino = cmd.rolesDestino();

        if (progreso.rol() == RolUsuario.MENTOR) {
            celulaDestinoId = requireCelulaLiderada(progreso);
            tipoAudiencia = TipoAudiencia.CELULA;
            nivelMinimoId = null;
            cursoId = null;
            rolesDestino = Set.of();
        }
        requireNivelExiste(tipoAudiencia, nivelMinimoId);

        Evento evento = Evento.crear(cmd.titulo(), cmd.descripcion(), cmd.iniciaEn(), cmd.duracionMinutos(),
                cmd.timezone(), cmd.tipoUbicacion(), cmd.valorUbicacion(), tipoAudiencia, nivelMinimoId, cursoId,
                celulaDestinoId, cmd.tipoEvento(), cmd.notificarAlCrear(), cmd.recordarPorEmail(),
                cmd.recordatoriosPersonalizados(), cmd.recurrencia(), rolesDestino, cmd.reglasRecordatorio(),
                cmd.actorId(), clock);
        Evento guardado = saveEventoPort.guardar(evento);
        return new EventoVista(guardado, coverUrlDe(guardado));
    }

    @Override
    @Transactional
    public EventoVista actualizar(ActualizarEventoCommand cmd) {
        ProgresoParticipanteCalendar progreso = requireRolCreador(cmd.actorId());
        Evento evento = requireEvento(cmd.eventoId());
        requirePropioSiMentor(progreso, evento, cmd.actorId(), "editar");

        TipoAudiencia tipoAudiencia = cmd.tipoAudiencia();
        Integer nivelMinimoId = cmd.nivelMinimoId();
        String cursoId = cmd.cursoId();
        UUID celulaDestinoId = cmd.celulaDestinoId();
        Set<RolUsuario> rolesDestino = cmd.rolesDestino();

        if (progreso.rol() == RolUsuario.MENTOR) {
            celulaDestinoId = requireCelulaLiderada(progreso);
            tipoAudiencia = TipoAudiencia.CELULA;
            nivelMinimoId = null;
            cursoId = null;
            rolesDestino = Set.of();
        }
        requireNivelExiste(tipoAudiencia, nivelMinimoId);

        evento.actualizar(cmd.titulo(), cmd.descripcion(), cmd.iniciaEn(), cmd.duracionMinutos(), cmd.timezone(),
                cmd.tipoUbicacion(), cmd.valorUbicacion(), tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId,
                cmd.notificarAlCrear(), cmd.recordarPorEmail(), cmd.recordatoriosPersonalizados(), cmd.recurrencia(),
                rolesDestino, cmd.reglasRecordatorio(), clock);
        Evento guardado = saveEventoPort.guardar(evento);

        // Los avisos ya generados nacieron de la version ANTERIOR — fuera del camino de
        // error a proposito (el evento ya se guardo), mismo criterio que updateEvent() del
        // repo viejo: si esto falla, el cron los recalcula igual en su siguiente pasada.
        try {
            saveRecordatorioPort.borrarPendientesFuturos(cmd.eventoId(), clock.now());
        } catch (RuntimeException ex) {
            log.warn("[EventoService.actualizar] no se pudieron limpiar los avisos pendientes de {}: {}",
                    cmd.eventoId(), ex.getMessage());
        }
        return new EventoVista(guardado, coverUrlDe(guardado));
    }

    @Override
    @Transactional
    public void eliminar(UserId actorId, EventoId eventoId) {
        ProgresoParticipanteCalendar progreso = requireRolCreador(actorId);
        Evento evento = requireEvento(eventoId);
        requirePropioSiMentor(progreso, evento, actorId, "eliminar");

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
        ProgresoParticipanteCalendar progreso = requireRolCreador(actorId);
        Evento evento = requireEvento(eventoId);
        requirePropioSiMentor(progreso, evento, actorId, "cancelar una ocurrencia de");

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
                .orElseGet(() -> Excepcion.cancelar(eventoId, inicioOcurrencia));
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
        ProgresoParticipanteCalendar progreso = requireRolCreador(actorId);
        Evento evento = requireEvento(eventoId);
        requirePropioSiMentor(progreso, evento, actorId, "editar");

        String ruta = PREFIJO_RUTA + "/" + eventoId + "/portada-" + clock.now().toEpochMilli();
        URI url = almacenamientoPort.firmarSubida(ruta, tipoContenido, VALIDEZ_URL_SUBIDA);
        return new UrlPortada(url, BUCKET_PORTADAS, ruta);
    }

    @Override
    @Transactional
    public EventoVista confirmar(UserId actorId, EventoId eventoId, String ruta) {
        ProgresoParticipanteCalendar progreso = requireRolCreador(actorId);
        Evento evento = requireEvento(eventoId);
        requirePropioSiMentor(progreso, evento, actorId, "editar");

        evento.fijarPortada(ruta);
        Evento guardado = saveEventoPort.guardar(evento);
        return new EventoVista(guardado, coverUrlDe(guardado));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /** Resuelve la URL de LECTURA de la portada aca (no en el controller): el controller
     * nunca depende de un puerto `out` (ArchitectureTest.controllersDoNotTouchPersistence). */
    private String coverUrlDe(Evento evento) {
        if (evento.portadaRuta() == null) {
            return null;
        }
        return almacenamientoPort.firmarLectura(evento.portadaRuta(), VALIDEZ_URL_LECTURA_PORTADA).toString();
    }

    private Evento requireEvento(EventoId eventoId) {
        return loadEventoPort.byId(eventoId)
                .orElseThrow(() -> new NoSuchElementException("Evento no encontrado: " + eventoId));
    }

    /** Crear/editar eventos es de rol administrativo (ADMIN/ALCHEMIST) o MENTOR (acotado a
     * su propia celula) — un TRAINEE no administra el calendario. MENTOR_LEAD queda fuera
     * hasta que se confirme su alcance (docs/MODULO_CALENDAR.md §6). */
    private ProgresoParticipanteCalendar requireRolCreador(UserId actorId) {
        ProgresoParticipanteCalendar progreso = accesoEventoService.requireProgreso(actorId);
        boolean autorizado = progreso.rol() == RolUsuario.ADMIN || progreso.rol() == RolUsuario.ALCHEMIST
                || progreso.rol() == RolUsuario.MENTOR;
        if (!autorizado) {
            throw new NotAuthorizedException("No tienes permiso para administrar el calendario");
        }
        return progreso;
    }

    private void requirePropioSiMentor(ProgresoParticipanteCalendar progreso, Evento evento, UserId actorId,
                                        String accion) {
        if (progreso.rol() == RolUsuario.MENTOR && (evento.creadoPor() == null || !evento.creadoPor().equals(actorId))) {
            throw new NotAuthorizedException("Solo puedes " + accion + " los eventos que creaste");
        }
    }

    private UUID requireCelulaLiderada(ProgresoParticipanteCalendar progreso) {
        if (progreso.celulaId() == null) {
            throw new NotAuthorizedException("Todavia no lideras una celula — no puedes administrar sesiones");
        }
        return progreso.celulaId();
    }

    private void requireNivelExiste(TipoAudiencia tipoAudiencia, Integer nivelMinimoId) {
        if (tipoAudiencia != TipoAudiencia.NIVEL_MINIMO || nivelMinimoId == null) {
            return;
        }
        boolean existe = nivelPort.listar().stream().anyMatch(n -> n.id() == nivelMinimoId);
        if (!existe) {
            throw new IllegalArgumentException("El nivel minimo indicado no existe: " + nivelMinimoId);
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
