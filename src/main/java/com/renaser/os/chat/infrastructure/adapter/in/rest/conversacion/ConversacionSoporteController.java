package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.ListarSoportesUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.ListarSoportesUseCase.PedidoDeSoportes;
import com.renaser.os.chat.application.ports.in.conversacion.RellenarConversacionesDeSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.SalirDeConversacionSoporteUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.SalirDeConversacionSoporteUseCase.SalirDeConversacionSoporteCommand;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.CursorDeSoportes;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Las operaciones propias del chat de soporte por aprendiz (D-136). La foto del soporte (D-205) vive
 * en {@link FotosDelChatController}, junto con la de los integrantes (D-206).
 *
 * <p>Sin {@code @RequestMapping} de clase a proposito: las rutas viven en arboles distintos de
 * la API —las acciones del usuario dentro del chat y la del panel de administracion— y
 * forzar un prefijo comun obligaria a inventar una ruta que no corresponde a ninguno de los dos.
 * Se prefiere eso a partir en dos controllers una funcion que es una sola.
 */
@RestController
public class ConversacionSoporteController {

    private final SalirDeConversacionSoporteUseCase salirUseCase;
    private final RellenarConversacionesDeSoporteUseCase rellenarUseCase;
    private final ListarSoportesUseCase listarSoportesUseCase;

    public ConversacionSoporteController(SalirDeConversacionSoporteUseCase salirUseCase,
                                          RellenarConversacionesDeSoporteUseCase rellenarUseCase,
                                          ListarSoportesUseCase listarSoportesUseCase) {
        this.salirUseCase = salirUseCase;
        this.rellenarUseCase = rellenarUseCase;
        this.listarSoportesUseCase = listarSoportesUseCase;
    }

    /**
     * La sección «Soporte» de Tribu (D-249): los chats de soporte de a una página, del más reciente al más
     * viejo, con búsqueda por nombre o correo del aprendiz sin tildes ni mayúsculas. Endpoint nuevo a
     * propósito: {@code GET /chat/conversations} no cambia (la app instalada lo usa tal cual).
     */
    @RequiresPermission(Permission.VIEW_ALL_SUPPORT_CHATS)
    @GetMapping("/api/v1/chat/support-conversations")
    public SoportesPageResponse listarSoportes(@ActorAutenticado UserId actorId,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false, defaultValue = "25") int size) {
        CursorDeSoportes desde = cursor == null || cursor.isBlank() ? null : CursorDeSoportes.leer(cursor);
        return SoportesPageResponse.from(listarSoportesUseCase.listar(new PedidoDeSoportes(actorId, q, desde, size)));
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
