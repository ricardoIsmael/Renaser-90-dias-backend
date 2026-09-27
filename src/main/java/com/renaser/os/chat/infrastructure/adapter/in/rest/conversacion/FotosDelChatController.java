package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase.FotoDelChat;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Las fotos del chat que son la tarjeta de Canva con un primer nombre (2026-09-27): la del chat de
 * soporte (D-205) y la de cada integrante de un grupo o de un soporte (D-206).
 *
 * <p>Las dos salen privadas por un día y con el ETag de su contenido: pasado el día se revalidan y, si
 * la tarjeta es la misma, el servidor contesta 304 sin mandarla de nuevo (lo resuelve Spring al
 * escribir el {@code ResponseEntity} con un {@code If-None-Match} igual).
 * <blockquote><b>Corregido 2026-09-27 (D-206).</b> La foto del soporte vivía en
 * {@code ConversacionSoporteController}; se mudó acá al sumarse la de los integrantes, con la misma
 * ruta y la misma respuesta.</blockquote>
 */
@RestController
public class FotosDelChatController {

    static final String RUTA_FOTO_DEL_SOPORTE = "/api/v1/chat/conversations/{id}/foto";
    static final String RUTA_FOTO_DE_INTEGRANTE = "/api/v1/chat/conversations/{id}/miembros/{usuarioId}/foto";

    /** Un día en el teléfono, y solo en el suyo ({@code private}: la foto sale con sesión). */
    private static final CacheControl UN_DIA_Y_PRIVADA = CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();

    private final VerFotosDelChatUseCase fotos;

    public FotosDelChatController(VerFotosDelChatUseCase fotos) {
        this.fotos = fotos;
    }

    /**
     * La tarjeta con el primer nombre del aprendiz dueño del soporte (D-205). 404 si la conversación no
     * existe o no es un soporte; 403 si quien pide no participa.
     */
    @RequiresPermission(value = Permission.USE_APP, scope = "participante del chat de soporte")
    @GetMapping(RUTA_FOTO_DEL_SOPORTE)
    public ResponseEntity<byte[]> fotoDelSoporte(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        return comoImagen(fotos.fotoDelSoporte(actorId, ConversacionId.of(id)));
    }

    /**
     * La tarjeta con el primer nombre de un integrante de un grupo o de un soporte (D-206), para la
     * lista de integrantes de la info del chat. 403 si quien pide no puede ver la conversación; 404 si
     * no existe, si es la comunidad o un 1 a 1, o si esa persona no es integrante.
     */
    @RequiresPermission(value = Permission.USE_APP,
            scope = "quien puede ver el grupo o el soporte, y solo de sus integrantes")
    @GetMapping(RUTA_FOTO_DE_INTEGRANTE)
    public ResponseEntity<byte[]> fotoDeIntegrante(@ActorAutenticado UserId actorId, @PathVariable UUID id,
                                                   @PathVariable UUID usuarioId) {
        return comoImagen(fotos.fotoDeIntegrante(actorId, ConversacionId.of(id), UserId.of(usuarioId)));
    }

    /** Lo que {@link ConversacionResponse#photoPath} le da a la app para la foto de un soporte. */
    public static String rutaDeLaFotoDelSoporte(ConversacionId soporteId) {
        return RUTA_FOTO_DEL_SOPORTE.replace("{id}", soporteId.toString());
    }

    /** La ruta de la tarjeta de un integrante, la que llega a la info del chat (D-206). */
    public static String rutaDeLaFotoDeIntegrante(ConversacionId conversacionId, UserId integranteId) {
        return RUTA_FOTO_DE_INTEGRANTE.replace("{id}", conversacionId.toString())
                .replace("{usuarioId}", integranteId.toString());
    }

    private static ResponseEntity<byte[]> comoImagen(FotoDelChat foto) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(UN_DIA_Y_PRIVADA)
                .eTag(foto.huella())
                .body(foto.jpeg());
    }
}
