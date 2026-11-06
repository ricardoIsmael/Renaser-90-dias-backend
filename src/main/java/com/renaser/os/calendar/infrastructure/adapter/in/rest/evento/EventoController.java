package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.ConfirmarPortadaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.EventoVista;
import com.renaser.os.calendar.application.ports.in.evento.SolicitarUrlPortadaUseCase;
import com.renaser.os.calendar.application.ports.in.meet.DisponibilidadDeMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.EventoConMeetVista;
import com.renaser.os.calendar.application.ports.in.meet.EventosConMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.ReintentarSalaDeMeetUseCase;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Recurrencia;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.shared.domain.FormacionDelContenido;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.calendar.application.ports.in.audiencia.ConvocatoriaUseCase;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rutas y contrato JSON iguales a {@code /api/v1/calendar/*} del repo viejo (calendar.ts,
 * app instalada) — ver docs/MODULO_CALENDAR.md §3/§6 para las dos excepciones deliberadas:
 * la portada usa URL prefirmada en dos pasos en vez del multipart directo (AlmacenamientoPort,
 * CLAUDE.MD §STORAGE — resuelta EN EL SERVICIO, nunca aca: el controller no toca puertos
 * `out`, ArchitectureTest.controllersDoNotTouchPersistence), y {@code scope=manage} se
 * acepta pero no cambia el comportamiento todavia (no se tuvo acceso a `route.ts` del repo
 * viejo para portar esa regla con fidelidad).
 */
@RestController
@RequestMapping("/api/v1/calendar/events")
@Funcionalidad("eventos")
class EventoController {

    /** Listar, ver, crear, editar, borrar y cancelar una fecha: los casos de uso de siempre, con el enlace de Meet automático (spec 040). */
    private final EventosConMeetUseCase eventosUseCase;
    private final SolicitarUrlPortadaUseCase solicitarUrlPortadaUseCase;
    private final ConfirmarPortadaUseCase confirmarPortadaUseCase;
    private final ConfirmarAsistenciaUseCase confirmarAsistenciaUseCase;
    private final DisponibilidadDeMeetUseCase disponibilidadDeMeetUseCase;
    private final ReintentarSalaDeMeetUseCase reintentarSalaUseCase;
    /** Spec 044: «Para quién es» y quién lo organizó. */
    private final ConvocatoriaUseCase convocatoriaUseCase;

    EventoController(EventosConMeetUseCase eventosUseCase, SolicitarUrlPortadaUseCase solicitarUrlPortadaUseCase,
                      ConfirmarPortadaUseCase confirmarPortadaUseCase,
                      ConfirmarAsistenciaUseCase confirmarAsistenciaUseCase,
                      DisponibilidadDeMeetUseCase disponibilidadDeMeetUseCase,
                      ReintentarSalaDeMeetUseCase reintentarSalaUseCase, ConvocatoriaUseCase convocatoriaUseCase) {
        this.convocatoriaUseCase = convocatoriaUseCase;
        this.eventosUseCase = eventosUseCase;
        this.solicitarUrlPortadaUseCase = solicitarUrlPortadaUseCase;
        this.confirmarPortadaUseCase = confirmarPortadaUseCase;
        this.confirmarAsistenciaUseCase = confirmarAsistenciaUseCase;
        this.disponibilidadDeMeetUseCase = disponibilidadDeMeetUseCase;
        this.reintentarSalaUseCase = reintentarSalaUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "el listado ya viene filtrado por la audiencia de cada evento")
    @GetMapping
    public List<OcurrenciaResponse> listar(@ActorAutenticado UserId actor,
                                            @RequestParam("from") String from, @RequestParam("to") String to,
                                            @RequestParam(value = "scope", required = false) String scope,
                                            @RequestParam(value = "formation", required = false) String formation) {
        var ocurrencias = eventosUseCase.listar(actor, Instant.parse(from), Instant.parse(to),
                FormacionDelContenido.pedida(formation));
        return ocurrencias.stream().map(OcurrenciaResponse::from).toList();
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "el actor tiene que estar en la audiencia del evento")
    @GetMapping("/{id}")
    public EventoResponse obtener(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        return aRespuesta(actor, eventosUseCase.obtener(actor, EventoId.of(id)));
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 044: ADMIN/ALCHEMIST, y el líder, el mentor y la guía en sus propios eventos (y el líder en los de sus mentores); el servicio rechaza al aprendiz")
    @Funcionalidad(value = "eventos.gestion", laResuelveElCasoDeUso = true)
    @PostMapping
    public ResponseEntity<EventoResponse> crear(@ActorAutenticado UserId actor,
                                                 @Valid @RequestBody EventoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aRespuesta(actor, eventosUseCase.crear(aComandoCrear(actor, request))));
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 044: ADMIN/ALCHEMIST, y el líder, el mentor y la guía en sus propios eventos (y el líder en los de sus mentores); el servicio rechaza al aprendiz")
    @Funcionalidad(value = "eventos.gestion", laResuelveElCasoDeUso = true)
    @PutMapping("/{id}")
    public EventoResponse actualizar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                      @Valid @RequestBody EventoRequest request) {
        return aRespuesta(actor, eventosUseCase.actualizar(aComandoActualizar(actor, id, request)));
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 044: ADMIN/ALCHEMIST, y el líder, el mentor y la guía en sus propios eventos (y el líder en los de sus mentores); el servicio rechaza al aprendiz")
    @Funcionalidad(value = "eventos.gestion", laResuelveElCasoDeUso = true)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        eventosUseCase.eliminar(actor, EventoId.of(id));
        return ResponseEntity.noContent().build();
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "el actor tiene que estar en la audiencia del evento")
    @PutMapping("/{id}/rsvp")
    public ResponseEntity<Void> rsvp(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                      @Valid @RequestBody RsvpRequest request) {
        confirmarAsistenciaUseCase.confirmar(actor, EventoId.of(id), Instant.parse(request.occurrenceStart()),
                EventoWireMapper.fromWireEstadoConfirmacion(request.status()));
        return ResponseEntity.ok().build();
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 044: ADMIN/ALCHEMIST, y el líder, el mentor y la guía en sus propios eventos (y el líder en los de sus mentores); el servicio rechaza al aprendiz")
    @Funcionalidad(value = "eventos.gestion", laResuelveElCasoDeUso = true)
    @PostMapping("/{id}/cancel-occurrence")
    public ResponseEntity<Void> cancelarOcurrencia(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                                    @Valid @RequestBody CancelarOcurrenciaRequest request) {
        eventosUseCase.cancelarOcurrencia(actor, EventoId.of(id), Instant.parse(request.occurrenceStart()));
        return ResponseEntity.ok().build();
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 044: ADMIN/ALCHEMIST, y el líder, el mentor y la guía en sus propios eventos (y el líder en los de sus mentores); el servicio rechaza al aprendiz")
    @Funcionalidad(value = "eventos.gestion", laResuelveElCasoDeUso = true)
    @PostMapping("/{id}/portada/upload-url")
    public UrlPortadaResponse solicitarUrlPortada(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                                   @Valid @RequestBody SolicitarUrlPortadaRequest request) {
        var url = solicitarUrlPortadaUseCase.solicitar(actor, EventoId.of(id), request.contentType());
        return UrlPortadaResponse.from(url);
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 044: ADMIN/ALCHEMIST, y el líder, el mentor y la guía en sus propios eventos (y el líder en los de sus mentores); el servicio rechaza al aprendiz")
    @Funcionalidad(value = "eventos.gestion", laResuelveElCasoDeUso = true)
    @PostMapping("/{id}/portada/confirm")
    public EventoResponse confirmarPortada(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                            @Valid @RequestBody ConfirmarPortadaRequest request) {
        EventoVista vista = confirmarPortadaUseCase.confirmar(actor, EventoId.of(id), request.ruta());
        return EventoResponse.from(vista.evento(), vista.coverUrl());
    }

    /**
     * Si el formulario puede ofrecer «Crear enlace de Meet» (spec 040, FR-014): hay cuenta de Google activa y Soporte TI prendió {@code eventos.meet}.
     * Solo ADMIN y ALCHEMIST. Un backend anterior responde 404 y la app no dibuja la opción.
     */
    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 044: ADMIN/ALCHEMIST, y el líder, el mentor y la guía en sus propios eventos (y el líder en los de sus mentores); el servicio rechaza al aprendiz")
    @Funcionalidad(value = "eventos.gestion", laResuelveElCasoDeUso = true)
    @GetMapping("/meet/status")
    public DisponibilidadDeMeetResponse disponibilidadDeMeet(@ActorAutenticado UserId actor) {
        return DisponibilidadDeMeetResponse.from(disponibilidadDeMeetUseCase.consultar(actor));
    }

    /** «Reintentar» el enlace de Meet de un evento (spec 040, FR-008): vuelve a pedir lo que quedó sin hacer, sin duplicar nada (FR-007). */
    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 044: ADMIN/ALCHEMIST, y el líder, el mentor y la guía en sus propios eventos (y el líder en los de sus mentores); el servicio rechaza al aprendiz")
    @Funcionalidad("eventos.meet")
    @PostMapping("/{id}/meet/retry")
    public EventoResponse reintentarMeet(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        return aRespuesta(actor, reintentarSalaUseCase.reintentar(actor, EventoId.of(id)));
    }

    // ─── Traduccion wire -> comando ─────────────────────────────────────────────

    private EventoResponse aRespuesta(UserId actor, EventoConMeetVista vista) {
        EventoVista evento = vista.evento();
        return EventoResponse.from(evento.evento(), evento.coverUrl(), vista.meet()).conAudiencia(
                convocatoriaUseCase.resumenPara(actor, evento.evento()).orElse(null),
                convocatoriaUseCase.organizador(evento.evento()).orElse(null));
    }

    private CrearEventoCommand aComandoCrear(UserId actorId, EventoRequest r) {
        return new CrearEventoCommand(actorId, r.title(), r.description(), r.startsAtInstant(), r.durationMinutes(),
                ZoneId.of(r.timezone()), EventoWireMapper.fromWireUbicacion(r.locationType()), r.locationValue(),
                EventoWireMapper.fromWireAudiencia(r.audienceType()), r.minLevelId(), r.courseId(),
                r.targetCellId() == null ? null : UUID.fromString(r.targetCellId()), TipoEvento.valueOf(r.eventType()),
                r.notifyOnCreate(), r.remindByEmail(), r.reminderRules() != null, recurrenciaDe(r), rolesDestinoDe(r),
                reglasDe(r), FormacionDelContenido.pedida(r.formation()), r.accesoPedido(), r.audienciaPedida());
    }

    private ActualizarEventoCommand aComandoActualizar(UserId actorId, UUID id, EventoRequest r) {
        return new ActualizarEventoCommand(actorId, EventoId.of(id), r.title(), r.description(), r.startsAtInstant(),
                r.durationMinutes(), ZoneId.of(r.timezone()), EventoWireMapper.fromWireUbicacion(r.locationType()),
                r.locationValue(), EventoWireMapper.fromWireAudiencia(r.audienceType()), r.minLevelId(), r.courseId(),
                r.targetCellId() == null ? null : UUID.fromString(r.targetCellId()), r.notifyOnCreate(),
                r.remindByEmail(), r.reminderRules() != null, recurrenciaDe(r), rolesDestinoDe(r), reglasDe(r), r.accesoPedido(),
                r.audienciaPedida());
    }

    private static Recurrencia recurrenciaDe(EventoRequest r) {
        if (r.recurrenceFrequency() == null) {
            return null;
        }
        return new Recurrencia(EventoWireMapper.fromWireFrecuencia(r.recurrenceFrequency()),
                r.recurrenceInterval() == null ? 1 : r.recurrenceInterval(), r.recurrenceUntilInstant(),
                r.recurrenceCount(), EventoWireMapper.fromWireDiasSemana(r.recurrenceByWeekday()));
    }

    private static Set<RolUsuario> rolesDestinoDe(EventoRequest r) {
        return r.targetRoles().stream().map(RolUsuario::valueOf).collect(Collectors.toSet());
    }

    private static List<ReglaRecordatorio> reglasDe(EventoRequest r) {
        if (r.reminderRules() == null) {
            return List.of();
        }
        List<ReglaRecordatorio> reglas = new ArrayList<>();
        int orden = 1;
        for (var wire : r.reminderRules()) {
            reglas.add(EventoWireMapper.fromWireRegla(orden++, wire));
        }
        return reglas;
    }
}
