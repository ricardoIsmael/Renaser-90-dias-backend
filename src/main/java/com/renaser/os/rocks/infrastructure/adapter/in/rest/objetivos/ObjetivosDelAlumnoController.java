package com.renaser.os.rocks.infrastructure.adapter.in.rest.objetivos;

import com.renaser.os.rocks.api.ObjetivosDelAprendizResponse;
import com.renaser.os.rocks.application.ports.in.objetivos.ConsultarObjetivosDelAlumnoUseCase;
import com.renaser.os.rocks.application.ports.in.objetivos.ConsultarObjetivosDelAlumnoUseCase.ConsultaObjetivos;
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
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/objetivos} (spec 057, FR-001): los objetivos del alumno con la misma forma que
 * los del líder, más {@code duracionDias} y {@code fase}. Solo para quien lo acompaña hoy, en solo lectura. Sin programa activo: 404
 * {@code SIN_PROGRAMA_ACTIVO}. Datos de una persona: nada se guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/objetivos")
@Funcionalidad("mentor.mi_grupo")
public class ObjetivosDelAlumnoController {

    private final ConsultarObjetivosDelAlumnoUseCase consultar;

    public ObjetivosDelAlumnoController(ConsultarObjetivosDelAlumnoUseCase consultar) {
        this.consultar = consultar;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "ObjetivosDelAlumnoService: acompanante vigente del grupo Y el alumno debe pertenecer a ese grupo")
    @GetMapping
    public ResponseEntity<ObjetivosDelAprendizResponse> objetivos(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                                                  @PathVariable UUID userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ObjetivosDelAprendizResponse.from(consultar.objetivosDe(new ConsultaObjetivos(actorId, groupId, UserId.of(userId)))));
    }
}
