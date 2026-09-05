package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import com.renaser.os.chat.application.ports.in.mensaje.SolicitarUrlSubidaMediaChatUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.SolicitarUrlSubidaMediaChatUseCase.SolicitarUrlSubidaMediaChatCommand;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Subida de fotos y audios de chat, con el patron de 3 pasos ya establecido en el resto del
 * sistema: "upload-url -> PUT directo a S3 -> enviar el mensaje con {@code mediaBucket}/{@code
 * mediaPath}". Los bytes no pasan por el backend en ningun momento.
 *
 * <p>Controller aparte de {@link MensajeController} solo porque la ruta no cuelga de
 * {@code .../messages}: el objeto se sube antes de que exista mensaje alguno.
 */
@RestController
@RequestMapping("/api/v1/chat/conversations/{conversationId}/media")
public class ChatMediaController {

    private final SolicitarUrlSubidaMediaChatUseCase solicitarUrlUseCase;

    public ChatMediaController(SolicitarUrlSubidaMediaChatUseCase solicitarUrlUseCase) {
        this.solicitarUrlUseCase = solicitarUrlUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "participante de la conversacion")
    @PostMapping("/upload-url")
    public UrlSubidaMediaChatResponse urlDeSubida(@ActorAutenticado UserId actorId,
                                                    @PathVariable UUID conversationId,
                                                    @RequestBody @Valid SolicitarUrlSubidaMediaChatRequest request) {
        var url = solicitarUrlUseCase.solicitarUrl(new SolicitarUrlSubidaMediaChatCommand(actorId,
                ConversacionId.of(conversationId), request.tipoContenido()));
        return UrlSubidaMediaChatResponse.from(url);
    }
}
