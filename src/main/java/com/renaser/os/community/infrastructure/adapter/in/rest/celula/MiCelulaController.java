package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** GET /api/v1/me/cell y /api/v1/me/cell/members — solo TRAINEE
 * (app/api/v1/me/cell/route.ts:26, mismo alcance que el codigo viejo: nunca hubo rama
 * MENTOR para este endpoint). Sin celula no es un error: 404 {@code {assigned:false}}
 * (app/api/v1/me/cell/route.ts:32-34). */
@RestController
@RequestMapping("/api/v1/me/cell")
public class MiCelulaController {

    private final ConsultarMiCelulaUseCase consultarUseCase;

    public MiCelulaController(ConsultarMiCelulaUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping
    public ResponseEntity<?> miCelula(@ActorAutenticado UserId traineeId) {
        return consultarUseCase.miCelula(traineeId)
                .<ResponseEntity<?>>map(mc -> ResponseEntity.ok(MiCelulaResponse.from(mc)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("assigned", false)));
    }

    @GetMapping("/members")
    public Map<String, List<CellMemberResponse>> miembros(@ActorAutenticado UserId traineeId) {
        List<CellMemberResponse> miembros = consultarUseCase.misCompaneros(traineeId).stream()
                .map(p -> CellMemberResponse.from(p, p.id().equals(traineeId)))
                .toList();
        return Map.of("members", miembros);
    }
}
