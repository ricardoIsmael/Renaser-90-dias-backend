package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarMisCelulasUseCase;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GET /api/v1/me/cells} y {@code /me/cells/{cellId}/members} — los grupos del aprendiz en
 * PLURAL, cada uno con sus propios integrantes (D-142).
 *
 * <p><b>Por qué rutas nuevas y no ampliar {@code /me/cell}.</b> Aquellas responden por el grupo
 * principal y hay un APK en manos de probadores que las usa: cambiarles la forma desde el servidor
 * le cambiaría la pantalla a una app que no se puede actualizar al mismo tiempo. Quedan intactas y
 * siguen siendo la respuesta correcta a la pregunta que hacen —"¿cuál es mi grupo?"—; estas
 * responden otra —"¿en cuáles estoy?"—.
 *
 * <p><b>Por qué {@code /members} cuelga del grupo y no del usuario.</b> Porque el grupo es el dato
 * que faltaba: {@code /me/cell/members} no tiene forma de decir "los de ESTE", y por eso la pantalla
 * de info mostraba siempre los mismos. Pedir los integrantes de un grupo al que no perteneces es
 * {@code 403}, no una lista vacía.
 */
@RestController
@RequestMapping("/api/v1/me/cells")
public class MisCelulasController {

    private final ConsultarMisCelulasUseCase consultarUseCase;

    public MisCelulasController(ConsultarMisCelulasUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    /** Sin grupos responde {@code {"cells": []}}: no tener grupo es un estado valido, no un fallo
     * — mismo criterio que {@code /me/cell} desde 2026-09-02. */
    @RequiresPermission(value = Permission.USE_APP, scope = "mismo alcance que /me/cell: quien no es participante recibe lista vacia, no 403")
    @GetMapping
    public Map<String, List<MiCelulaResponse>> misCelulas(@ActorAutenticado UserId traineeId) {
        return Map.of("cells", consultarUseCase.misCelulas(traineeId).stream()
                .map(MiCelulaResponse::from)
                .toList());
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "el caso de uso rechaza con 403 si el actor no pertenece a ese grupo")
    @GetMapping("/{cellId}/members")
    public Map<String, List<CellMemberResponse>> integrantes(@ActorAutenticado UserId traineeId,
                                                              @PathVariable UUID cellId) {
        List<CellMemberResponse> miembros = consultarUseCase.integrantesDe(traineeId, CelulaId.of(cellId)).stream()
                .map(p -> CellMemberResponse.from(p, p.id().equals(traineeId)))
                .toList();
        return Map.of("members", miembros);
    }
}
