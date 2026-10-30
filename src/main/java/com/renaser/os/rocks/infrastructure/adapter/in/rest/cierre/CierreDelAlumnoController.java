package com.renaser.os.rocks.infrastructure.adapter.in.rest.cierre;

import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierresDelAlumnoUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierresDelAlumnoUseCase.ConsultaCierres;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/cierres?semana=YYYY-MM-DD}
 *
 * <p>Lo que un alumno dijo al cerrar cada día de la semana (spec 017): cerrado o sin cerrar, las acciones que le quedaron pendientes con
 * su motivo y su plan de mañana, y sus notas. Solo para quien lo acompaña —el mentor o la guía VIGENTE de su grupo—, en solo lectura.
 * Es el mismo guardián que el Radar y los huecos del alumno.
 *
 * <p>{@code semana} es el lunes; sin ella, la semana en curso, que decide el SERVICIO en la zona del alumno. Una semana futura, anterior a
 * su primera semana o que no es lunes es 400. Datos de una persona: nada se guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/cierres")
@Funcionalidad("mentor.mi_grupo")
public class CierreDelAlumnoController {

    private final ConsultarCierresDelAlumnoUseCase consultar;

    public CierreDelAlumnoController(ConsultarCierresDelAlumnoUseCase consultar) {
        this.consultar = consultar;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "CierresDelAlumnoService: acompanante vigente del grupo Y el alumno debe pertenecer a ese grupo")
    @GetMapping
    public ResponseEntity<CierresDeLaSemana> cierres(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                                     @PathVariable UUID userId,
                                                     @RequestParam(required = false)
                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana) {
        CierresDeLaSemana cierres = consultar.semanaDe(new ConsultaCierres(actorId, groupId, UserId.of(userId), semana));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cierres);
    }
}
