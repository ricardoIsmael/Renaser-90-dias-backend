package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.mentoring.application.ports.in.ConsultarNotasDelAprendizUseCase;
import com.renaser.os.mentoring.infrastructure.adapter.in.rest.NotasDelAprendizController.NotasResponse;
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
 * {@code GET /api/v1/admin/trainees/{traineeId}/notas} (spec 055, FR-024): Administración (ADMIN y ALCHEMIST activos) lee las notas
 * privadas de cualquier aprendiz, solo lectura ({@code puedeCorregir} siempre false). Misma forma que la del mentor. El guard es de
 * rol y vive en el servicio: el permiso declarado no cierra la puerta a un MENTOR (A-1).
 */
@RestController
@RequestMapping("/api/v1/admin/trainees/{traineeId}/notas")
@Funcionalidad("admin.personas")
public class NotasAdministrativasController {

    private final ConsultarNotasDelAprendizUseCase consultar;

    public NotasAdministrativasController(ConsultarNotasDelAprendizUseCase consultar) {
        this.consultar = consultar;
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES,
            scope = "AccesoAVistasDelSemaforo.requireAdminActivo: ADMIN/ALCHEMIST activo, sin exigir relacion con el alumno")
    @GetMapping
    public ResponseEntity<NotasResponse> notas(@ActorAutenticado UserId actorId, @PathVariable UUID traineeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new NotasResponse(consultar.notasParaAdministracion(actorId, traineeId)));
    }
}
