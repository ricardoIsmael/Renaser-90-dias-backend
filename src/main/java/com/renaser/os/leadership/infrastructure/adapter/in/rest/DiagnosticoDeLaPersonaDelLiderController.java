package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarDiagnosticoDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarDiagnosticoDePersonaUseCase.ConsultaDiagnosticoDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarDiagnosticoDePersonaUseCase.ConsultaDiagnosticoDeUnaPersona;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * El diagnóstico de una persona, en solo lectura, para el líder de mentores, Administración y Alquimista (spec 026, 026-11):
 * {@code GET /api/v1/leadership/groups/{groupId}/people/{personId}/diagnostico} y, sin grupo,
 * {@code GET /api/v1/leadership/people/{personId}/diagnostico}. El permiso declarado no alcanza solo (A-1): decide {@code AccesoDeLiderazgo}. Datos de una
 * persona: nada se guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/leadership")
@Funcionalidad("lider.cumplimiento")
public class DiagnosticoDeLaPersonaDelLiderController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1); "
            + "persona de un grupo regular con mentor, o aprendiz que se muestra";

    private final ConsultarDiagnosticoDePersonaUseCase diagnostico;

    public DiagnosticoDeLaPersonaDelLiderController(ConsultarDiagnosticoDePersonaUseCase diagnostico) {
        this.diagnostico = diagnostico;
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/groups/{groupId}/people/{personId}/diagnostico")
    public ResponseEntity<LecturaDelDiagnostico> deUnGrupo(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                                           @PathVariable UUID personId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(diagnostico.diagnosticoDe(new ConsultaDiagnosticoDePersona(actorId, groupId, personId)));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/people/{personId}/diagnostico")
    public ResponseEntity<LecturaDelDiagnostico> sinGrupo(@ActorAutenticado UserId actorId, @PathVariable UUID personId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(diagnostico.diagnosticoDeUnaPersona(new ConsultaDiagnosticoDeUnaPersona(actorId, personId)));
    }
}
