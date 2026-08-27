package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import com.renaser.os.habits.application.ports.in.guiaadmin.ConfirmarAdjuntoGuiaArchivoUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.ConfirmarAdjuntoGuiaArchivoUseCase.ConfirmarAdjuntoGuiaArchivoCommand;
import com.renaser.os.habits.application.ports.in.guiaadmin.CrearAdjuntoGuiaEnlaceUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.CrearAdjuntoGuiaEnlaceUseCase.CrearAdjuntoGuiaEnlaceCommand;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarAdjuntoGuiaUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarAdjuntoGuiaUseCase.EliminarAdjuntoGuiaCommand;
import com.renaser.os.habits.application.ports.in.guiaadmin.SolicitarUrlAdjuntoGuiaUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.SolicitarUrlAdjuntoGuiaUseCase.SolicitarUrlAdjuntoGuiaCommand;
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
 * Panel admin de adjuntos de guia (hueco #11). ENLACE se cuelga en un solo paso
 * ({@link #crear}); IMAGEN/AUDIO usan el patron de 3 pasos "upload-url -> PUT -> confirmar"
 * ya establecido en el resto del backend ({@link #urlDeSubida} + {@link #confirmar}) — no
 * multipart, ver javadoc de {@code SolicitarUrlAdjuntoGuiaUseCase}/
 * {@code ConfirmarAdjuntoGuiaArchivoUseCase} y el reporte de esta tarea.
 */
@RestController
@RequestMapping("/api/v1/admin/habits")
public class GuiaAdjuntoAdminController {

    private final CrearAdjuntoGuiaEnlaceUseCase crearUseCase;
    private final EliminarAdjuntoGuiaUseCase eliminarUseCase;
    private final SolicitarUrlAdjuntoGuiaUseCase urlAdjuntoUseCase;
    private final ConfirmarAdjuntoGuiaArchivoUseCase confirmarUseCase;

    public GuiaAdjuntoAdminController(CrearAdjuntoGuiaEnlaceUseCase crearUseCase,
                                       EliminarAdjuntoGuiaUseCase eliminarUseCase,
                                       SolicitarUrlAdjuntoGuiaUseCase urlAdjuntoUseCase,
                                       ConfirmarAdjuntoGuiaArchivoUseCase confirmarUseCase) {
        this.crearUseCase = crearUseCase;
        this.eliminarUseCase = eliminarUseCase;
        this.urlAdjuntoUseCase = urlAdjuntoUseCase;
        this.confirmarUseCase = confirmarUseCase;
    }

    @PostMapping("/{habitId}/guide-attachments")
    public ResponseEntity<HabitGuideAttachmentResponse> crear(@RequestHeader("X-Actor-Id") String actorId,
                                                                @PathVariable UUID habitId,
                                                                @RequestBody @Valid CreateGuideAttachmentRequest request) {
        var adjunto = crearUseCase.crear(new CrearAdjuntoGuiaEnlaceCommand(UserId.of(actorId), HabitoId.of(habitId),
                request.startDay(), request.section().toDomain(), request.url(), request.title()));
        return ResponseEntity.status(HttpStatus.CREATED).body(HabitGuideAttachmentResponse.from(adjunto));
    }

    @PostMapping("/{habitId}/guide-attachments/upload-url")
    public UrlAdjuntoGuiaResponse urlDeSubida(@RequestHeader("X-Actor-Id") String actorId,
                                               @PathVariable UUID habitId,
                                               @RequestBody @Valid SolicitarUrlAdjuntoGuiaRequest request) {
        var url = urlAdjuntoUseCase.solicitarUrl(new SolicitarUrlAdjuntoGuiaCommand(UserId.of(actorId),
                HabitoId.of(habitId), request.tipoContenido()));
        return UrlAdjuntoGuiaResponse.from(url);
    }

    @PostMapping("/{habitId}/guide-attachments/confirm")
    public ResponseEntity<HabitGuideAttachmentResponse> confirmar(@RequestHeader("X-Actor-Id") String actorId,
                                                                    @PathVariable UUID habitId,
                                                                    @RequestBody @Valid ConfirmarAdjuntoGuiaArchivoRequest request) {
        var adjunto = confirmarUseCase.confirmar(new ConfirmarAdjuntoGuiaArchivoCommand(UserId.of(actorId),
                HabitoId.of(habitId), request.startDay(), request.section().toDomain(),
                request.mediaType().toDomain(), request.bucket(), request.ruta(), request.mimeType(),
                request.sizeBytes(), request.originalName(), request.title()));
        return ResponseEntity.status(HttpStatus.CREATED).body(HabitGuideAttachmentResponse.from(adjunto));
    }

    @DeleteMapping("/guide-attachments/{attachmentId}")
    public ResponseEntity<Void> eliminar(@RequestHeader("X-Actor-Id") String actorId,
                                          @PathVariable UUID attachmentId) {
        eliminarUseCase.eliminar(new EliminarAdjuntoGuiaCommand(UserId.of(actorId), AdjuntoGuiaId.of(attachmentId)));
        return ResponseEntity.noContent().build();
    }
}
