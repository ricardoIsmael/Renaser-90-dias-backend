package com.renaser.os.chat.infrastructure.adapter.in.rest.miembro;

import com.renaser.os.chat.application.ports.in.miembro.ListarDirectorioMiembrosUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Directorio de usuarios para iniciar un DM (#27). El roster del grupo GLOBAL (#28)
 * vive en {@code ConversacionController} — es informacion de esa conversacion
 * puntual, no un directorio general. */
@RestController
@RequestMapping("/api/v1/chat/members")
public class MiembroController {

    private final ListarDirectorioMiembrosUseCase listarUseCase;

    public MiembroController(ListarDirectorioMiembrosUseCase listarUseCase) {
        this.listarUseCase = listarUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "hoy NO exige ser participante del grupo GLOBAL, a diferencia de /chat/conversations/global/members que lee el mismo roster")
    @GetMapping
    public MiembrosPageResponse listar(@ActorAutenticado UserId actorId,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false, defaultValue = "30") int limit) {
        UserId cursorId = cursor != null && !cursor.isBlank() ? UserId.of(cursor) : null;
        var pagina = listarUseCase.listar(actorId, q, cursorId, limit);
        return MiembrosPageResponse.from(pagina);
    }
}
