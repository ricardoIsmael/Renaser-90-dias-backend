package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.asistencia.PasarListaUseCase;
import com.renaser.os.calendar.application.ports.in.asistencia.PasarListaUseCase.MarcarAsistenciaCommand;
import com.renaser.os.calendar.application.ports.in.asistencia.VerRespuestasDelEventoUseCase;
import com.renaser.os.calendar.domain.model.asistencia.EstadoAsistencia;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.infrastructure.adapter.in.rest.evento.AsistenciaResponses.ListaWire;
import com.renaser.os.calendar.infrastructure.adapter.in.rest.evento.AsistenciaResponses.PersonaListaWire;
import com.renaser.os.calendar.infrastructure.adapter.in.rest.evento.AsistenciaResponses.RespuestasWire;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Quién respondió y pasar lista en un evento (D-256, {@code docs/api/CONTRATO_ASISTENCIA_EVENTOS.md}). Rutas
 * nuevas bajo el mismo prefijo de {@link EventoController}, sin tocar las que ya usa la app instalada.
 *
 * <p>La regla de quién puede (quien creó el evento, Admin, Alquimista o Líder de mentores) es de relación con
 * el evento, no solo de rol: la aplica el servicio ({@code AccesoALaListaService}), no este controller.
 */
@RestController
@RequestMapping("/api/v1/calendar/events")
class AsistenciaController {

    private static final String QUIEN = "quien creó el evento, ADMIN, ALCHEMIST o MENTOR_LEAD (D-256): el servicio "
            + "rechaza al resto con 403";

    private final VerRespuestasDelEventoUseCase respuestasUseCase;
    private final PasarListaUseCase pasarListaUseCase;

    AsistenciaController(VerRespuestasDelEventoUseCase respuestasUseCase, PasarListaUseCase pasarListaUseCase) {
        this.respuestasUseCase = respuestasUseCase;
        this.pasarListaUseCase = pasarListaUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = QUIEN)
    @GetMapping("/{id}/responses")
    public RespuestasWire respuestas(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                     @RequestParam("occurrenceStart") String occurrenceStart) {
        return AsistenciaResponses.de(respuestasUseCase.respuestas(actor, EventoId.of(id), Instant.parse(occurrenceStart)));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = QUIEN)
    @GetMapping("/{id}/attendance")
    public ListaWire lista(@ActorAutenticado UserId actor, @PathVariable UUID id,
                           @RequestParam("occurrenceStart") String occurrenceStart) {
        return AsistenciaResponses.de(pasarListaUseCase.ver(actor, EventoId.of(id), Instant.parse(occurrenceStart)));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = QUIEN)
    @PutMapping("/{id}/attendance/{userId}")
    public PersonaListaWire marcar(@ActorAutenticado UserId actor, @PathVariable UUID id, @PathVariable UUID userId,
                                   @Valid @RequestBody MarcarAsistenciaRequest request) {
        var comando = new MarcarAsistenciaCommand(actor, EventoId.of(id), Instant.parse(request.occurrenceStart()),
                UserId.of(userId), estadoDe(request.estado()));
        return AsistenciaResponses.filaDe(pasarListaUseCase.marcar(comando));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = QUIEN)
    @PostMapping("/{id}/attendance/close")
    public ListaWire cerrar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                            @Valid @RequestBody OcurrenciaRequest request) {
        return AsistenciaResponses.de(pasarListaUseCase.cerrar(actor, EventoId.of(id),
                Instant.parse(request.occurrenceStart())));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = QUIEN)
    @PostMapping("/{id}/attendance/reopen")
    public ListaWire reabrir(@ActorAutenticado UserId actor, @PathVariable UUID id,
                             @Valid @RequestBody OcurrenciaRequest request) {
        return AsistenciaResponses.de(pasarListaUseCase.reabrir(actor, EventoId.of(id),
                Instant.parse(request.occurrenceStart())));
    }

    private static EstadoAsistencia estadoDe(String wire) {
        if (wire == null) {
            return null;
        }
        return switch (wire) {
            case "A_TIEMPO" -> EstadoAsistencia.A_TIEMPO;
            case "TARDE" -> EstadoAsistencia.TARDE;
            default -> throw new IllegalArgumentException("estado invalido: " + wire + " (A_TIEMPO, TARDE o null)");
        };
    }
}
