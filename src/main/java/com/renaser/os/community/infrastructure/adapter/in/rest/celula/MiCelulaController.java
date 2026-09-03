package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** GET /api/v1/me/cell y /api/v1/me/cell/members — solo TRAINEE
 * (app/api/v1/me/cell/route.ts:26, mismo alcance que el codigo viejo: nunca hubo rama
 * MENTOR para este endpoint). Sin celula no es un error: 200 {@code {assigned:false}}.
 *
 * <p>Corregido (comunidad-mentor-y-tribu, 2026-09-02): antes este caso devolvia 404, lo que
 * un cliente no puede distinguir de un error real de red/ruta. El endpoint hermano,
 * {@code /members}, ya devolvia 200 con lista vacia para el mismo caso — este cambio alinea
 * {@code /me/cell} a ese mismo criterio (sin celula es un estado valido, no un fallo). */
@RestController
@RequestMapping("/api/v1/me/cell")
public class MiCelulaController {

    private final ConsultarMiCelulaUseCase consultarUseCase;

    public MiCelulaController(ConsultarMiCelulaUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "el javadoc dice TRAINEE pero no hay guard de rol: quien no es participante recibe lista vacia, no 403")
    @GetMapping
    public ResponseEntity<?> miCelula(@ActorAutenticado UserId traineeId) {
        return consultarUseCase.miCelula(traineeId)
                .<ResponseEntity<?>>map(mc -> ResponseEntity.ok(MiCelulaResponse.from(mc)))
                .orElseGet(() -> ResponseEntity.ok(Map.of("assigned", false)));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "el javadoc dice TRAINEE pero no hay guard de rol: quien no es participante recibe lista vacia, no 403")
    @GetMapping("/members")
    public Map<String, List<CellMemberResponse>> miembros(@ActorAutenticado UserId traineeId) {
        List<CellMemberResponse> miembros = consultarUseCase.misCompaneros(traineeId).stream()
                .map(p -> CellMemberResponse.from(p, p.id().equals(traineeId)))
                .toList();
        return Map.of("members", miembros);
    }
}
