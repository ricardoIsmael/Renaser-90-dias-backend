package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarAprendicesDelGrupoUseCase;
import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarAprendicesDelGrupoUseCase.ConsultaAprendices;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code GET /api/v1/mentor/groups/{groupId}/learners} — los aprendices de un grupo que el
 * actor acompaña hoy.
 *
 * <p>El {@code groupId} viene del cliente, así que el servicio comprueba la relación vigente
 * antes de devolver nada. Un exmentor con un token todavía válido recibe 403, no la lista.
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners")
public class AprendicesDelGrupoController {

    private static final int LIMITE_POR_DEFECTO = 25;

    private final ConsultarAprendicesDelGrupoUseCase consultarAprendices;

    public AprendicesDelGrupoController(ConsultarAprendicesDelGrupoUseCase consultarAprendices) {
        this.consultarAprendices = consultarAprendices;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "AcompanamientoService.requireAcompanaVigente: solo un acompanante VIGENTE de ese grupo")
    @GetMapping
    public AprendicesDelGrupoResponse aprendices(@ActorAutenticado UserId actorId,
                                                  @PathVariable UUID groupId,
                                                  @RequestParam(required = false) UUID cursor,
                                                  @RequestParam(required = false) Integer limit) {
        return AprendicesDelGrupoResponse.from(consultarAprendices.aprendices(
                new ConsultaAprendices(actorId, groupId, cursor, limit != null ? limit : LIMITE_POR_DEFECTO)));
    }
}
