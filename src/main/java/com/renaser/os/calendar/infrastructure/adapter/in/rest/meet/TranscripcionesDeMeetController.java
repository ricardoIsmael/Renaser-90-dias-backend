package com.renaser.os.calendar.infrastructure.adapter.in.rest.meet;

import com.renaser.os.calendar.application.ports.in.meet.TranscripcionesDeMeetUseCase;
import com.renaser.os.calendar.application.ports.in.meet.TranscripcionesDeMeetUseCase.TranscripcionDescargada;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spec 047: la transcripción de las sesiones por Meet. NINGÚN endpoint de la app la devuelve: solo este, para ADMIN y ALCHEMIST, con motivo y
 * constancia (decisión P1-A del dueño), y el estado por fecha sin texto (FR-012). La respuesta del texto no se guarda en ninguna caché.
 */
@RestController
@RequestMapping("/api/v1/calendar/events/{id}/meet/transcripciones")
@Funcionalidad("eventos.meet.transcripcion")
public class TranscripcionesDeMeetController {

    private final TranscripcionesDeMeetUseCase useCase;

    public TranscripcionesDeMeetController(TranscripcionesDeMeetUseCase useCase) {
        this.useCase = useCase;
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 047: solo ADMIN y ALCHEMIST; el servicio rechaza a los demás")
    @GetMapping
    public List<EstadoResponse> estados(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        return useCase.estados(actor, EventoId.of(id)).stream()
                .map(e -> new EstadoResponse(e.fecha().toString(), e.estado(), e.motivo(),
                        e.obtenidaEn() == null ? null : e.obtenidaEn().toString(),
                        e.borrarDespuesDe() == null ? null : e.borrarDespuesDe().toString(), e.grabacion()))
                .toList();
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 047 P1-A: solo ADMIN y ALCHEMIST, con motivo; queda constancia")
    @PostMapping(value = "/{fecha}/descarga", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> descargar(@ActorAutenticado UserId actor, @PathVariable UUID id, @PathVariable LocalDate fecha,
                                            @Valid @RequestBody DescargaRequest request) {
        TranscripcionDescargada t = useCase.descargar(actor, EventoId.of(id), fecha, request.motivo());
        StringBuilder cuerpo = new StringBuilder();
        if (t.enlaceDeLaGrabacion() != null) {
            cuerpo.append("Grabación: ").append(t.enlaceDeLaGrabacion()).append("\n\n");
        }
        cuerpo.append(t.texto());
        if (t.recortada()) {
            cuerpo.append("\n\n[La transcripción era más larga y se guardó recortada.]");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(new MediaType("text", "plain",
                java.nio.charset.StandardCharsets.UTF_8)).body(cuerpo.toString());
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "spec 047 FR-016: solo ADMIN y ALCHEMIST, con motivo; queda constancia")
    @PostMapping("/{fecha}/borrar")
    public ResponseEntity<Void> borrar(@ActorAutenticado UserId actor, @PathVariable UUID id, @PathVariable LocalDate fecha,
                                       @Valid @RequestBody DescargaRequest request) {
        useCase.borrarAPedido(actor, EventoId.of(id), fecha, request.motivo());
        return ResponseEntity.noContent().build();
    }

    public record DescargaRequest(@NotBlank @Size(min = 10, max = 500) String motivo) {
    }

    public record EstadoResponse(String fecha, String estado, String motivo, String obtenidaEn, String borrarDespuesDe, boolean grabacion) {
    }
}
