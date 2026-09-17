package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.SumarMentorAGrupoUseCase;
import com.renaser.os.community.application.ports.in.celula.SumarMentorAGrupoUseCase.SumarMentorAGrupoCommand;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code POST /api/v1/admin/cells/{id}/additional-mentor} — pone a un mentor al frente de este
 * grupo <b>sin sacarlo de los que ya lidera</b> (D-141).
 *
 * <p><b>Por qué una ruta nueva y no un parámetro de {@code PUT /{id}/mentor}.</b> Ese endpoint es
 * un TRASLADO, y uno con efectos que no se ven desde acá: además de mover al mentor, deja sin
 * mentor a cada grupo que abandona y repunta a los aprendices de esos grupos. Colgar "sumar" de un
 * campo del body habría dejado ese efecto destructivo dependiendo de un campo que un cliente viejo
 * no manda — el default decidiría por él, y el día que alguien se equivocara nadie encontraría por
 * qué un grupo se quedó sin mentor. Dos efectos distintos, dos rutas.
 *
 * <p>Mismo reparto y mismo razonamiento que {@code AltaAdicionalDeAprendizController}, que resolvió
 * esto para el aprendiz un día antes.
 *
 * <p><b>Por qué un controller propio y no un método más en {@code CelulaAdminController}.</b> Ese
 * ya pasa el techo de siete métodos públicos de {@code .claude/rules/01}; no se le suma otro.
 */
@RestController
@RequestMapping("/api/v1/admin/cells/{id}/additional-mentor")
public class AltaAdicionalDeMentorController {

    private final SumarMentorAGrupoUseCase sumarUseCase;

    public AltaAdicionalDeMentorController(SumarMentorAGrupoUseCase sumarUseCase) {
        this.sumarUseCase = sumarUseCase;
    }

    /** Devuelve el detalle del grupo DESTINO, igual que el alta por traslado: la respuesta ya trae
     * el mentor y el conteo recalculados dentro de la misma transaccion. */
    @RequiresPermission(Permission.MANAGE_CELLS)
    @PostMapping
    public CelulaDetalleResponse sumar(@ActorAutenticado UserId actorId, @PathVariable UUID id,
                                        @RequestBody @Valid AsignarMentorRequest request) {
        return CelulaDetalleResponse.from(sumarUseCase.sumar(new SumarMentorAGrupoCommand(actorId,
                CelulaId.of(id), UserId.of(request.leaderUserId()))));
    }
}
