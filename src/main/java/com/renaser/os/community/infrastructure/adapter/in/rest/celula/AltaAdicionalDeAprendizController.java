package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase;
import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase.SumarAprendizAGrupoCommand;
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
 * {@code POST /api/v1/admin/cells/{id}/additional-trainees} — suma un aprendiz a este grupo
 * <b>sin sacarlo de los que ya tiene</b> (D-139).
 *
 * <p><b>Por qué una ruta nueva y no un parámetro de {@code POST /{id}/trainees}.</b> Ese endpoint
 * es un TRASLADO: cierra la pertenencia anterior del aprendiz y abre una sola en el destino. Es lo
 * que el panel viene usando y sigue funcionando exactamente igual. Agregar sumar/mover como
 * variante del mismo POST habría dejado el efecto destructivo —sacarlo de su grupo— dependiendo de
 * un campo del body que un cliente viejo no manda: el default decidiría por él. Dos efectos
 * distintos, dos rutas.
 *
 * <p><b>Por qué un controller propio y no un método más en {@code CelulaAdminController}.</b> Ese
 * ya tiene trece métodos públicos contra el techo de siete de {@code .claude/rules/01}; no se le
 * suma el catorce. Mismo criterio que {@code AprendicesDelGrupoController} y
 * {@code ContextoMentorController}, que también cuelgan de rutas vecinas sin compartir clase.
 */
@RestController
@RequestMapping("/api/v1/admin/cells/{id}/additional-trainees")
public class AltaAdicionalDeAprendizController {

    private final SumarAprendizAGrupoUseCase sumarUseCase;

    public AltaAdicionalDeAprendizController(SumarAprendizAGrupoUseCase sumarUseCase) {
        this.sumarUseCase = sumarUseCase;
    }

    /** Devuelve el detalle del grupo DESTINO, igual que el alta por traslado: la respuesta ya trae
     * el cupo y el conteo recalculados dentro de la misma transaccion. */
    @RequiresPermission(Permission.MANAGE_CELLS)
    @PostMapping
    public CelulaDetalleResponse sumar(@ActorAutenticado UserId actorId, @PathVariable UUID id,
                                        @RequestBody @Valid AsignarAprendizRequest request) {
        return CelulaDetalleResponse.from(sumarUseCase.sumar(new SumarAprendizAGrupoCommand(actorId,
                CelulaId.of(id), UserId.of(request.traineeId()))));
    }
}
