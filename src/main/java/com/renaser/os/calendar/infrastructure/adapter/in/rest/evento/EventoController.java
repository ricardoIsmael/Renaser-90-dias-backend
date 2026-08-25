package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ActualizarEventoUseCase.ActualizarEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.CancelarOcurrenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ConfirmarPortadaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.EliminarEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.EventoVista;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ObtenerEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.SolicitarUrlPortadaUseCase;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Recurrencia;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
class EventoController {

    private final ListarEventosParaVisorUseCase listarUseCase;
    private final ObtenerEventoUseCase obtenerUseCase;
    private final CrearEventoUseCase crearUseCase;
    private final ActualizarEventoUseCase actualizarUseCase;
    private final EliminarEventoUseCase eliminarUseCase;
    private final CancelarOcurrenciaUseCase cancelarOcurrenciaUseCase;
    private final SolicitarUrlPortadaUseCase solicitarUrlPortadaUseCase;
    private final ConfirmarPortadaUseCase confirmarPortadaUseCase;
    private final ConfirmarAsistenciaUseCase confirmarAsistenciaUseCase;

    EventoController(ListarEventosParaVisorUseCase listarUseCase, ObtenerEventoUseCase obtenerUseCase,
                      CrearEventoUseCase crearUseCase, ActualizarEventoUseCase actualizarUseCase,
                      EliminarEventoUseCase eliminarUseCase, CancelarOcurrenciaUseCase cancelarOcurrenciaUseCase,
                      SolicitarUrlPortadaUseCase solicitarUrlPortadaUseCase,
                      ConfirmarPortadaUseCase confirmarPortadaUseCase,
                      ConfirmarAsistenciaUseCase confirmarAsistenciaUseCase) {
        this.listarUseCase = listarUseCase;
        this.obtenerUseCase = obtenerUseCase;
        this.crearUseCase = crearUseCase;
        this.actualizarUseCase = actualizarUseCase;
        this.eliminarUseCase = eliminarUseCase;
        this.cancelarOcurrenciaUseCase = cancelarOcurrenciaUseCase;
        this.solicitarUrlPortadaUseCase = solicitarUrlPortadaUseCase;
        this.confirmarPortadaUseCase = confirmarPortadaUseCase;
        this.confirmarAsistenciaUseCase = confirmarAsistenciaUseCase;
    }

    @GetMapping
    public List<OcurrenciaResponse> listar(@RequestHeader("X-Actor-Id") String actorId,
                                            @RequestParam("from") String from, @RequestParam("to") String to,
                                            @RequestParam(value = "scope", required = false) String scope) {
        var ocurrencias = listarUseCase.listar(UserId.of(actorId), Instant.parse(from), Instant.parse(to));
        return ocurrencias.stream().map(OcurrenciaResponse::from).toList();
    }

    @GetMapping("/{id}")
    public EventoResponse obtener(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id) {
        EventoVista vista = obtenerUseCase.obtener(UserId.of(actorId), EventoId.of(id));
        return EventoResponse.from(vista.evento(), vista.coverUrl());
    }

    @PostMapping
    public ResponseEntity<EventoResponse> crear(@RequestHeader("X-Actor-Id") String actorId,
                                                 @Valid @RequestBody EventoRequest request) {
        EventoVista creado = crearUseCase.crear(aComandoCrear(UserId.of(actorId), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(EventoResponse.from(creado.evento(), creado.coverUrl()));
    }

    @PutMapping("/{id}")
    public EventoResponse actualizar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id,
                                      @Valid @RequestBody EventoRequest request) {
        EventoVista actualizado = actualizarUseCase.actualizar(aComandoActualizar(UserId.of(actorId), id, request));
        return EventoResponse.from(actualizado.evento(), actualizado.coverUrl());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id) {
        eliminarUseCase.eliminar(UserId.of(actorId), EventoId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/rsvp")
    public ResponseEntity<Void> rsvp(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id,
                                      @Valid @RequestBody RsvpRequest request) {
        confirmarAsistenciaUseCase.confirmar(UserId.of(actorId), EventoId.of(id), Instant.parse(request.occurrenceStart()),
                EventoWireMapper.fromWireEstadoConfirmacion(request.status()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel-occurrence")
    public ResponseEntity<Void> cancelarOcurrencia(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id,
                                                    @Valid @RequestBody CancelarOcurrenciaRequest request) {
        cancelarOcurrenciaUseCase.cancelar(UserId.of(actorId), EventoId.of(id), Instant.parse(request.occurrenceStart()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/portada/upload-url")
    public UrlPortadaResponse solicitarUrlPortada(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id,
                                                   @Valid @RequestBody SolicitarUrlPortadaRequest request) {
        var url = solicitarUrlPortadaUseCase.solicitar(UserId.of(actorId), EventoId.of(id), request.contentType());
        return UrlPortadaResponse.from(url);
    }

    @PostMapping("/{id}/portada/confirm")
    public EventoResponse confirmarPortada(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id,
                                            @Valid @RequestBody ConfirmarPortadaRequest request) {
        EventoVista vista = confirmarPortadaUseCase.confirmar(UserId.of(actorId), EventoId.of(id), request.ruta());
        return EventoResponse.from(vista.evento(), vista.coverUrl());
    }

    // ─── Traduccion wire -> comando ─────────────────────────────────────────────

    private CrearEventoCommand aComandoCrear(UserId actorId, EventoRequest r) {
        return new CrearEventoCommand(actorId, r.title(), r.description(), r.startsAtInstant(), r.durationMinutes(),
                ZoneId.of(r.timezone()), EventoWireMapper.fromWireUbicacion(r.locationType()), r.locationValue(),
                EventoWireMapper.fromWireAudiencia(r.audienceType()), r.minLevelId(), r.courseId(),
                r.targetCellId() == null ? null : UUID.fromString(r.targetCellId()), TipoEvento.valueOf(r.eventType()),
                r.notifyOnCreate(), r.remindByEmail(), r.reminderRules() != null, recurrenciaDe(r), rolesDestinoDe(r),
                reglasDe(r));
    }

    private ActualizarEventoCommand aComandoActualizar(UserId actorId, UUID id, EventoRequest r) {
        return new ActualizarEventoCommand(actorId, EventoId.of(id), r.title(), r.description(), r.startsAtInstant(),
                r.durationMinutes(), ZoneId.of(r.timezone()), EventoWireMapper.fromWireUbicacion(r.locationType()),
                r.locationValue(), EventoWireMapper.fromWireAudiencia(r.audienceType()), r.minLevelId(), r.courseId(),
                r.targetCellId() == null ? null : UUID.fromString(r.targetCellId()), r.notifyOnCreate(),
                r.remindByEmail(), r.reminderRules() != null, recurrenciaDe(r), rolesDestinoDe(r), reglasDe(r));
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
