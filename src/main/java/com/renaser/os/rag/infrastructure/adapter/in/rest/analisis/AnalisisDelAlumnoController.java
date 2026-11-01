package com.renaser.os.rag.infrastructure.adapter.in.rest.analisis;

import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisisDeAlumnoUseCase;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisisDeAlumnoUseCase.ConsultaDeAnalisisDeAlumno;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
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
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/analisis-80-20?periodo=SEMANA|MES&inicio=YYYY-MM-DD}
 *
 * <p>El 80/20 y el análisis de un alumno para su mentor o su guía VIGENTE (spec 031, 031-2 A), en solo lectura, con la forma de las demás
 * secciones de la ficha. El mismo guardián que el Código Renaser, los huecos y los cierres del alumno; además respeta el interruptor
 * «Código Renaser en las fichas» (404 si está apagado) y la formación (404 si la persona es de otra). Datos de una persona: {@code no-store}.
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/analisis-80-20")
@Funcionalidad("mentor.mi_grupo")
public class AnalisisDelAlumnoController {

    private final ConsultarAnalisisDeAlumnoUseCase consultar;

    public AnalisisDelAlumnoController(ConsultarAnalisisDeAlumnoUseCase consultar) {
        this.consultar = consultar;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "AnalisisDelAlumnoService: acompañante vigente del grupo, el alumno es de ese grupo, su formación y radar.fichas")
    @GetMapping
    public ResponseEntity<VistaDelAnalisis> analisis(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                                      @PathVariable UUID userId, @RequestParam(defaultValue = "SEMANA") String periodo,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio) {
        VistaDelAnalisis vista = consultar.deAlumno(new ConsultaDeAnalisisDeAlumno(actorId, groupId, UserId.of(userId),
                TipoDePeriodo.desde(periodo), inicio));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vista);
    }
}
