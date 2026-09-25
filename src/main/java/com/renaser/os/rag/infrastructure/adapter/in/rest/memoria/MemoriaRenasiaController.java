package com.renaser.os.rag.infrastructure.adapter.in.rest.memoria;

import com.renaser.os.rag.application.ports.in.memoria.BorrarMemoriaUseCase;
import com.renaser.os.rag.application.ports.in.memoria.ConsultarMemoriaUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Lo que Renasia recuerda de ti" (D-167): la persona ve y borra lo que el acompanante aprendio de
 * ella. Decision del dueno: no se le pregunta en cada conversacion, a cambio de que sea visible y
 * borrable. Solo lo propio: el actor sale de la sesion, nunca de la URL.
 */
@RestController
@RequestMapping("/api/v1/renasia/memoria")
public class MemoriaRenasiaController {

    private final ConsultarMemoriaUseCase consultarUseCase;
    private final BorrarMemoriaUseCase borrarUseCase;

    public MemoriaRenasiaController(ConsultarMemoriaUseCase consultarUseCase, BorrarMemoriaUseCase borrarUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.borrarUseCase = borrarUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "solo la memoria propia")
    @GetMapping
    public MemoriaRenasiaResponse ver(@ActorAutenticado UserId actorId) {
        return MemoriaRenasiaResponse.from(consultarUseCase.paraElPerfil(actorId));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "solo la memoria propia")
    @DeleteMapping("/recuerdos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarUno(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        borrarUseCase.borrarRecuerdo(actorId, id);
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "solo la memoria propia")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarTodo(@ActorAutenticado UserId actorId) {
        borrarUseCase.borrarTodo(actorId);
    }
}
