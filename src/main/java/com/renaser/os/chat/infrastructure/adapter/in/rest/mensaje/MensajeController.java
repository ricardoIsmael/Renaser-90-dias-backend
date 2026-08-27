package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.ListarMensajesUseCase;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat/conversations/{conversationId}/messages")
public class MensajeController {

    private final EnviarMensajeUseCase enviarUseCase;
    private final ListarMensajesUseCase listarUseCase;

    public MensajeController(EnviarMensajeUseCase enviarUseCase, ListarMensajesUseCase listarUseCase) {
        this.enviarUseCase = enviarUseCase;
        this.listarUseCase = listarUseCase;
    }

    @PostMapping
    public ResponseEntity<MensajeResponse> enviar(@ActorAutenticado UserId actorId,
                                                    @PathVariable UUID conversationId,
                                                    @RequestBody @Valid EnviarMensajeRequest request) {
        var mensaje = enviarUseCase.enviar(new EnviarMensajeCommand(actorId,
                ConversacionId.of(conversationId), parseTipoMensaje(request.type()), request.text(),
                request.mediaBucket(), request.mediaPath(), request.mediaMime(), request.mediaBytes(),
                request.mediaDurationSeconds(),
                request.replyToId() != null ? MensajeId.of(UUID.fromString(request.replyToId())) : null));
        return ResponseEntity.status(HttpStatus.CREATED).body(MensajeResponse.from(mensaje));
    }

    @GetMapping
    public MensajesPageResponse listar(@ActorAutenticado UserId actorId,
                                        @PathVariable UUID conversationId,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(required = false, defaultValue = "30") int limit) {
        Instant cursorInstant = cursor != null ? Instant.parse(cursor) : null;
        var pagina = listarUseCase.listar(actorId, ConversacionId.of(conversationId), cursorInstant,
                limit);
        return MensajesPageResponse.from(pagina);
    }

    private static TipoMensaje parseTipoMensaje(String type) {
        return switch (type) {
            case "TEXT" -> TipoMensaje.TEXTO;
            case "IMAGE" -> TipoMensaje.IMAGEN;
            case "AUDIO" -> TipoMensaje.AUDIO;
            case "VIDEO" -> TipoMensaje.VIDEO;
            case "SYSTEM" -> TipoMensaje.SISTEMA;
            default -> throw new IllegalArgumentException("Tipo de mensaje invalido: " + type);
        };
    }
}
