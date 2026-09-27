package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.RellenarConversacionesDeSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.SalirDeConversacionSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.SalirDeConversacionSoporteUseCase.SalirDeConversacionSoporteCommand;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotoDelSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotoDelSoporteUseCase.FotoDelSoporte;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Las operaciones propias del chat de soporte por aprendiz (D-136; la foto, D-205).
 *
 * <p>Sin {@code @RequestMapping} de clase a proposito: las rutas viven en arboles distintos de
 * la API —las acciones del usuario dentro del chat y la del panel de administracion— y
 * forzar un prefijo comun obligaria a inventar una ruta que no corresponde a ninguno de los dos.
 * Se prefiere eso a partir en dos controllers una funcion que es una sola.
 */
@RestController
public class ConversacionSoporteController {

    /** Lo que {@link ConversacionResponse#photoPath} le da a la app para pedir la foto (D-205). */
    static final String RUTA_DE_LA_FOTO = "/api/v1/chat/conversations/{id}/foto";

    /**
     * Un día en el teléfono, y solo en el suyo ({@code private}: la foto sale con sesión). La tarjeta
     * cambia solo si cambia el primer nombre; pasado el día se revalida con el ETag y, si es la misma,
     * el servidor contesta 304 sin mandarla de nuevo.
     */
    private static final CacheControl UN_DIA_Y_PRIVADA = CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();

    private final SalirDeConversacionSoporteUseCase salirUseCase;
    private final RellenarConversacionesDeSoporteUseCase rellenarUseCase;
    private final VerFotoDelSoporteUseCase fotoUseCase;

    public ConversacionSoporteController(SalirDeConversacionSoporteUseCase salirUseCase,
                                          RellenarConversacionesDeSoporteUseCase rellenarUseCase,
                                          VerFotoDelSoporteUseCase fotoUseCase) {
        this.salirUseCase = salirUseCase;
        this.rellenarUseCase = rellenarUseCase;
        this.fotoUseCase = fotoUseCase;
    }

    /** El staff se va de un chat de soporte. El aprendiz dueño recibe 403 — el caso de uso lo
     * rechaza, no este metodo. */
    @RequiresPermission(value = Permission.USE_APP,
            scope = "participante de la conversacion, y no el aprendiz dueño del soporte")
    @PostMapping("/api/v1/chat/conversations/{id}/leave")
    public ResponseEntity<Map<String, String>> salir(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        salirUseCase.salir(new SalirDeConversacionSoporteCommand(actorId, ConversacionId.of(id)));
        return ResponseEntity.ok(Map.of("id", id.toString(), "left", "true"));
    }

    /**
     * La foto del chat de soporte (D-205): la tarjeta de Canva con el primer nombre del aprendiz
     * dueño. 404 si la conversación no existe o no es un soporte; 403 si quien pide no participa.
     *
     * <p>Con el ETag que va en la respuesta, un {@code If-None-Match} igual recibe 304 sin cuerpo
     * (lo resuelve Spring al escribir el {@code ResponseEntity}).
     */
    @RequiresPermission(value = Permission.USE_APP, scope = "participante del chat de soporte")
    @GetMapping(RUTA_DE_LA_FOTO)
    public ResponseEntity<byte[]> foto(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        FotoDelSoporte foto = fotoUseCase.foto(actorId, ConversacionId.of(id));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(UN_DIA_Y_PRIVADA)
                .eTag(foto.huella())
                .body(foto.jpeg());
    }

    /**
     * Le da su chat de soporte a los aprendices que ya estaban antes de que la funcion existiera.
     * Explicito y manual: una corrida masiva automatica al arrancar es justo lo que nadie puede
     * deshacer. Idempotente — repetirlo no crea nada de nuevo.
     */
    @RequiresPermission(Permission.MANAGE_TRAINEES)
    @PostMapping("/api/v1/admin/chat/support-conversations/backfill")
    public RellenoSoporteResponse rellenar(@ActorAutenticado UserId actorId) {
        return RellenoSoporteResponse.from(rellenarUseCase.rellenar(actorId));
    }
}
