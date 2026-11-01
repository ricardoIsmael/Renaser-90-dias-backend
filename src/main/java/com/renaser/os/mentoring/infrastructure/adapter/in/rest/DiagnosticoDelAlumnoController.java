package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.mentoring.application.ports.in.ConsultarDiagnosticoDelAlumnoUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarDiagnosticoDelAlumnoUseCase.ConsultaDiagnostico;
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
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/diagnostico}: el diagnóstico de un alumno (spec 026, 026-11), solo para quien lo acompaña —el
 * mentor o la guía VIGENTE de su grupo—, en solo lectura. Datos de una persona: nada se guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/diagnostico")
@Funcionalidad("mentor.mi_grupo")
public class DiagnosticoDelAlumnoController {

    private final ConsultarDiagnosticoDelAlumnoUseCase diagnostico;

    public DiagnosticoDelAlumnoController(ConsultarDiagnosticoDelAlumnoUseCase diagnostico) {
        this.diagnostico = diagnostico;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "DiagnosticoDelAlumnoService: acompanante vigente del grupo Y el alumno debe pertenecer a ese grupo")
    @GetMapping
    public ResponseEntity<LecturaDelDiagnostico> diagnostico(@ActorAutenticado UserId actorId, @PathVariable UUID groupId, @PathVariable UUID userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(diagnostico.diagnosticoDe(new ConsultaDiagnostico(actorId, groupId, UserId.of(userId))));
    }
}
