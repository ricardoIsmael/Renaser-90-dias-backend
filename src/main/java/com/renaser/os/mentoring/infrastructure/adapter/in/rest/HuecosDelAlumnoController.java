package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.application.ports.in.ConsultarHuecosDelAlumnoUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarHuecosDelAlumnoUseCase.ConsultaHuecos;
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
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/huecos?semana=YYYY-MM-DD}
 *
 * <p>Los huecos de horario de un alumno (spec 015): lo que le queda libre de lunes a domingo, los tramos que se
 * repiten y lo cumplido frente a lo planificado. Solo para quien lo acompaña —el mentor o la guía VIGENTE de su
 * grupo—, en solo lectura; la persona no los ve en esta primera versión. Es el mismo guardián que el Código Renaser
 * del alumno ({@code …/radar}).
 *
 * <p>{@code semana} es el lunes; sin ella, la semana en curso, que decide el SERVICIO en la zona del alumno (este
 * adaptador no puede resolver «hoy»). Una semana futura o anterior a su primera semana es 400. Datos de una persona:
 * nada se guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/huecos")
@Funcionalidad("mentor.mi_grupo")
public class HuecosDelAlumnoController {

    private final ConsultarHuecosDelAlumnoUseCase consultarHuecos;

    public HuecosDelAlumnoController(ConsultarHuecosDelAlumnoUseCase consultarHuecos) {
        this.consultarHuecos = consultarHuecos;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "HuecosDelAlumnoService: acompanante vigente del grupo Y el alumno debe pertenecer a ese grupo")
    @GetMapping
    public ResponseEntity<HuecosDeLaSemana> huecos(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                                   @PathVariable UUID userId,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana) {
        HuecosDeLaSemana huecos = consultarHuecos.semanaDe(new ConsultaHuecos(actorId, groupId, UserId.of(userId), semana));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(huecos);
    }
}
