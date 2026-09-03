package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase.CrearConversacionDirectaCommand;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.MarcarLeidoUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.MarcarLeidoUseCase.MarcarLeidoCommand;
import com.renaser.os.chat.application.ports.in.conversacion.RenombrarConversacionGlobalUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.RenombrarConversacionGlobalUseCase.RenombrarConversacionGlobalCommand;
import com.renaser.os.chat.application.ports.in.miembro.ListarMiembrosGlobalUseCase;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.infrastructure.adapter.in.rest.miembro.MiembrosPageResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Conversaciones de chat: crear/obtener directa, listar las mias, marcar leido, y la
 * ficha del grupo GLOBAL (miembros + rename, #28). Los mensajes viven en
 * {@code MensajeController} (ruta anidada). */
@RestController
@RequestMapping("/api/v1/chat/conversations")
public class ConversacionController {

    private final CrearConversacionDirectaUseCase crearDirectaUseCase;
    private final ListarConversacionesUseCase listarUseCase;
    private final MarcarLeidoUseCase marcarLeidoUseCase;
    private final ListarMiembrosGlobalUseCase listarMiembrosGlobalUseCase;
    private final RenombrarConversacionGlobalUseCase renombrarConversacionGlobalUseCase;

    public ConversacionController(CrearConversacionDirectaUseCase crearDirectaUseCase,
                                   ListarConversacionesUseCase listarUseCase,
                                   MarcarLeidoUseCase marcarLeidoUseCase,
                                   ListarMiembrosGlobalUseCase listarMiembrosGlobalUseCase,
                                   RenombrarConversacionGlobalUseCase renombrarConversacionGlobalUseCase) {
        this.crearDirectaUseCase = crearDirectaUseCase;
        this.listarUseCase = listarUseCase;
        this.marcarLeidoUseCase = marcarLeidoUseCase;
        this.listarMiembrosGlobalUseCase = listarMiembrosGlobalUseCase;
        this.renombrarConversacionGlobalUseCase = renombrarConversacionGlobalUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "el destinatario tambien tiene que estar activo")
    @PostMapping("/direct")
    public ResponseEntity<ConversacionResponse> obtenerOCrearDirecta(
            @ActorAutenticado UserId actorId,
            @RequestBody @Valid CrearConversacionDirectaRequest request) {
        var conversacion = crearDirectaUseCase.obtenerOCrear(new CrearConversacionDirectaCommand(
                actorId, UserId.of(request.otherUserId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversacionResponse.from(conversacion));
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public List<ConversacionResumenResponse> listar(@ActorAutenticado UserId actorId) {
        return listarUseCase.listar(actorId).stream().map(ConversacionResumenResponse::from).toList();
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "participante de la conversacion")
    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, String>> marcarLeido(@ActorAutenticado UserId actorId,
                                                             @PathVariable UUID id) {
        marcarLeidoUseCase.marcarLeido(new MarcarLeidoCommand(actorId, ConversacionId.of(id)));
        return ResponseEntity.ok(Map.of("id", id.toString()));
    }

    /** Ficha del grupo GLOBAL (#28): todos sus miembros, los cinco roles. */
    @RequiresPermission(value = Permission.USE_APP, scope = "participante del grupo GLOBAL")
    @GetMapping("/global/members")
    public MiembrosPageResponse listarMiembrosGlobal(@ActorAutenticado UserId actorId,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false, defaultValue = "30") int limit) {
        UserId cursorId = cursor != null && !cursor.isBlank() ? UserId.of(cursor) : null;
        var pagina = listarMiembrosGlobalUseCase.listar(actorId, cursorId, limit);
        return MiembrosPageResponse.from(pagina);
    }

    /** Renombrar el grupo GLOBAL (#28) — solo ADMIN/ALCHEMIST, el caso de uso rechaza
     * con 403 a cualquier otro rol. */
    @RequiresPermission(Permission.RENAME_GLOBAL_CHAT)
    @PatchMapping("/global/name")
    public ResponseEntity<Map<String, String>> renombrarGlobal(
            @ActorAutenticado UserId actorId,
            @RequestBody @Valid RenombrarConversacionGlobalRequest request) {
        Conversacion global = renombrarConversacionGlobalUseCase.renombrar(
                new RenombrarConversacionGlobalCommand(actorId, request.name()));
        return ResponseEntity.ok(Map.of("id", global.id().toString(), "name", global.nombre()));
    }
}
