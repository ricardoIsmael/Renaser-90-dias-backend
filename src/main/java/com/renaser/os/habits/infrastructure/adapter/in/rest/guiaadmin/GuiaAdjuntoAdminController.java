package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import com.renaser.os.habits.application.ports.in.guiaadmin.CrearAdjuntoGuiaEnlaceUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.CrearAdjuntoGuiaEnlaceUseCase.CrearAdjuntoGuiaEnlaceCommand;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarAdjuntoGuiaUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarAdjuntoGuiaUseCase.EliminarAdjuntoGuiaCommand;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuiaId;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Panel admin de adjuntos de guia (hueco #11) — solo ENLACE en esta pasada. La subida de
 * archivo ({@code POST .../guide-attachments/upload}, IMAGEN/AUDIO) queda documentada
 * como pendiente: ver {@code CrearAdjuntoGuiaEnlaceUseCase} javadoc y el reporte de esta
 * tarea en docs/MODULO_HABITS.md.
 */
@RestController
@RequestMapping("/api/v1/admin/habits")
public class GuiaAdjuntoAdminController {

    private final CrearAdjuntoGuiaEnlaceUseCase crearUseCase;
    private final EliminarAdjuntoGuiaUseCase eliminarUseCase;

    public GuiaAdjuntoAdminController(CrearAdjuntoGuiaEnlaceUseCase crearUseCase,
                                       EliminarAdjuntoGuiaUseCase eliminarUseCase) {
        this.crearUseCase = crearUseCase;
        this.eliminarUseCase = eliminarUseCase;
    }

    @PostMapping("/{habitId}/guide-attachments")
    public ResponseEntity<HabitGuideAttachmentResponse> crear(@RequestHeader("X-Actor-Id") String actorId,
                                                                @PathVariable UUID habitId,
                                                                @RequestBody @Valid CreateGuideAttachmentRequest request) {
        var adjunto = crearUseCase.crear(new CrearAdjuntoGuiaEnlaceCommand(UserId.of(actorId), HabitoId.of(habitId),
                request.startDay(), request.section().toDomain(), request.url(), request.title()));
        return ResponseEntity.status(HttpStatus.CREATED).body(HabitGuideAttachmentResponse.from(adjunto));
    }

    @DeleteMapping("/guide-attachments/{attachmentId}")
    public ResponseEntity<Void> eliminar(@RequestHeader("X-Actor-Id") String actorId,
                                          @PathVariable UUID attachmentId) {
        eliminarUseCase.eliminar(new EliminarAdjuntoGuiaCommand(UserId.of(actorId), AdjuntoGuiaId.of(attachmentId)));
        return ResponseEntity.noContent().build();
    }
}
