package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EditarComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EditarComentarioUseCase.EditarComentarioCommand;
import com.renaser.os.community.application.ports.in.publicacion.EscribirComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EscribirComentarioUseCase.EscribirComentarioCommand;
import com.renaser.os.community.application.ports.in.publicacion.OcultarComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.OcultarComentarioUseCase.OcultarComentarioCommand;
import com.renaser.os.community.domain.model.publicacion.ComentarioId;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wall/{postId}/comments")
public class WallCommentController {

    private final ConsultarComentariosUseCase consultarUseCase;
    private final EscribirComentarioUseCase escribirUseCase;
    private final EditarComentarioUseCase editarUseCase;
    private final OcultarComentarioUseCase ocultarUseCase;

    public WallCommentController(ConsultarComentariosUseCase consultarUseCase,
                                  EscribirComentarioUseCase escribirUseCase, EditarComentarioUseCase editarUseCase,
                                  OcultarComentarioUseCase ocultarUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.escribirUseCase = escribirUseCase;
        this.editarUseCase = editarUseCase;
        this.ocultarUseCase = ocultarUseCase;
    }

    @GetMapping
    public WallCommentsPageResponse listar(@PathVariable UUID postId,
                                            @RequestParam(required = false) String cursor) {
        return WallCommentsPageResponse.from(consultarUseCase.pagina(PublicacionId.of(postId), parseCursor(cursor)));
    }

    /** Un cursor mal formado es un error del cliente, no del servidor: 400, no 500. */
    private static Instant parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(cursor);
        } catch (Exception e) {
            throw new IllegalArgumentException("El cursor no tiene un formato de fecha valido");
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestHeader("X-Actor-Id") String actorId,
                                                       @PathVariable UUID postId,
                                                       @RequestBody @Valid CreateWallCommentRequest request) {
        var resultado = escribirUseCase.escribir(new EscribirComentarioCommand(UserId.of(actorId),
                PublicacionId.of(postId), request.text()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "comment", WallCommentResponse.from(resultado.comentario()),
                "commentCount", resultado.cantidadComentarios()));
    }

    @PatchMapping("/{commentId}")
    public WallCommentResponse actualizar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID postId,
                                           @PathVariable UUID commentId,
                                           @RequestBody @Valid UpdateWallCommentRequest request) {
        return WallCommentResponse.from(editarUseCase.editar(
                new EditarComentarioCommand(UserId.of(actorId), ComentarioId.of(commentId), request.text())));
    }

    @DeleteMapping("/{commentId}")
    public Map<String, Integer> eliminar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID postId,
                                          @PathVariable UUID commentId) {
        var resultado = ocultarUseCase.ocultar(new OcultarComentarioCommand(UserId.of(actorId),
                ComentarioId.of(commentId)));
        return Map.of("commentCount", resultado.cantidadComentarios());
    }
}
