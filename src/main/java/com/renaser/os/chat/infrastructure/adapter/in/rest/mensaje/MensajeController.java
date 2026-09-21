package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import com.renaser.os.chat.application.ports.in.mensaje.CompartirPublicacionUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.CompartirPublicacionUseCase.CompartirPublicacionCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.application.ports.in.mensaje.ListarMensajesUseCase;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
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
    private final CompartirPublicacionUseCase compartirPublicacionUseCase;

    public MensajeController(EnviarMensajeUseCase enviarUseCase, ListarMensajesUseCase listarUseCase,
                              CompartirPublicacionUseCase compartirPublicacionUseCase) {
        this.enviarUseCase = enviarUseCase;
        this.listarUseCase = listarUseCase;
        this.compartirPublicacionUseCase = compartirPublicacionUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "participante de la conversacion")
    @PostMapping
    public ResponseEntity<MensajeResponse> enviar(@ActorAutenticado UserId actorId,
                                                    @PathVariable UUID conversationId,
                                                    @RequestBody @Valid EnviarMensajeRequest request) {
        var mensaje = enviarUseCase.enviar(new EnviarMensajeCommand(actorId,
                ConversacionId.of(conversationId), parseTipoMensaje(request.type()), request.text(),
                request.mediaBucket(), request.mediaPath(), request.mediaMime(), request.mediaBytes(),
                request.mediaDurationSeconds(),
                request.replyToId() != null ? MensajeId.of(UUID.fromString(request.replyToId())) : null,
                // La ruta la eligio el TELEFONO: solo vale si es del prefijo de esta conversacion.
                // El cuerpo no tiene forma de pedir otra cosa — `EnviarMensajeRequest` no expone
                // este campo a proposito, es lo unico que separa compartir de pegar una clave ajena.
                OrigenMedia.CLIENTE));
        return ResponseEntity.status(HttpStatus.CREATED).body(MensajeResponse.from(mensaje));
    }

    /**
     * Comparte una publicacion del Muro en esta conversacion. Devuelve el MISMO
     * {@link MensajeResponse} y el mismo 201 que {@link #enviar}: para el cliente el resultado es
     * un mensaje mas de la conversacion, no un recurso nuevo.
     *
     * <p>Existe como endpoint propio, y no como un {@code POST .../messages} que el cliente arma
     * solo, porque el texto y la referencia a la foto los tiene que resolver el servidor — el
     * motivo completo esta en {@link CompartirPublicacionUseCase}.
     */
    @RequiresPermission(value = Permission.USE_APP, scope = "participante de la conversacion")
    @PostMapping("/share-wall-post")
    public ResponseEntity<MensajeResponse> compartirPublicacion(@ActorAutenticado UserId actorId,
                                                                 @PathVariable UUID conversationId,
                                                                 @RequestBody @Valid
                                                                 CompartirPublicacionRequest request) {
        var mensaje = compartirPublicacionUseCase.compartir(new CompartirPublicacionCommand(actorId,
                ConversacionId.of(conversationId), request.postId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(MensajeResponse.from(mensaje));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "participante de la conversacion")
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
