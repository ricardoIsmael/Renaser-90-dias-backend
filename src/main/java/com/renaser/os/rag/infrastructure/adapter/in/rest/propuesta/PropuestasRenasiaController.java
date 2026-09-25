package com.renaser.os.rag.infrastructure.adapter.in.rest.propuesta;

import com.renaser.os.rag.application.ports.in.propuesta.ResolverPropuestaUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Los dos botones de una propuesta del acompanante (fase 2, D-153): el modelo propone, la persona
 * decide aca. Vive bajo {@code /api/v1/renasia/**}, que exige sesion real ({@code SecurityConfig}):
 * el header {@code X-Actor-Id} no alcanza para confirmar una escritura en nombre de otro.
 *
 * <p>Que la propuesta sea del actor, que la cuenta este activa y que no haya vencido lo decide
 * {@code PropuestasAgenteService}, nunca este controller.
 */
@RestController
@RequestMapping("/api/v1/renasia/propuestas")
public class PropuestasRenasiaController {

    private final ResolverPropuestaUseCase resolverUseCase;

    public PropuestasRenasiaController(ResolverPropuestaUseCase resolverUseCase) {
        this.resolverUseCase = resolverUseCase;
    }

    /** Idempotente: un segundo toque devuelve el mismo resultado sin volver a ejecutar. */
    @RequiresPermission(value = Permission.USE_APP, scope = "solo el dueno de la propuesta")
    @PostMapping("/{id}/confirmar")
    public ResultadoPropuestaResponse confirmar(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        return ResultadoPropuestaResponse.from(resolverUseCase.confirmar(actorId, id));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "solo el dueno de la propuesta")
    @PostMapping("/{id}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        resolverUseCase.cancelar(actorId, id);
    }
}
