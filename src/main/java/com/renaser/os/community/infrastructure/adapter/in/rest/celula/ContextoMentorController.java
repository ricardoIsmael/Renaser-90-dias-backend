package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarContextoAcompanamientoUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/mentor/context} — qué acompaña y qué cursa el actor, en una lectura.
 *
 * <p>Sustituye la cadena {@code /admin/cohorts} → {@code /admin/cells?cohortId} →
 * {@code /admin/cells/{id}} que el cliente venía usando: además de ser superficie de
 * administración, se quedaba con el primer elemento de cada lista y podía devolver una
 * célula que no era la del mentor.
 *
 * <p>Sin asignaciones no es un error: 200 con {@code assignments: []} y
 * {@code canAccompany: false}. Un 404 no se distingue de un problema de red, y el cliente
 * ya tuvo ese bug.
 */
@RestController
@RequestMapping("/api/v1/mentor/context")
public class ContextoMentorController {

    private final ConsultarContextoAcompanamientoUseCase consultarContexto;

    public ContextoMentorController(ConsultarContextoAcompanamientoUseCase consultarContexto) {
        this.consultarContexto = consultarContexto;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "autoconsulta: la respuesta se arma desde las asignaciones del actor, no hay id de grupo que manipular")
    @GetMapping
    public ContextoMentorResponse contexto(@ActorAutenticado UserId actorId) {
        return ContextoMentorResponse.from(consultarContexto.contexto(actorId));
    }
}
