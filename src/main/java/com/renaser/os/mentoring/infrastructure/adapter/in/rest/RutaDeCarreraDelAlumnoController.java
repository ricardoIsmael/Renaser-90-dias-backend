package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.habits.api.RutaDeCarreraFinder.RutaLeida;
import com.renaser.os.mentoring.application.ports.in.ConsultarRutaDeCarreraDelAlumnoUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarRutaDeCarreraDelAlumnoUseCase.ConsultaRuta;
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

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/carreras/{carreraId}/ruta}: la ruta de una carrera con GPS de un alumno
 * (spec 042, FR-041), solo para quien lo acompaña —el mentor o la guía VIGENTE de su grupo—. 404 si el alumno no tiene esa carrera o
 * no guardó (o ya se borró) su ruta. Datos de una persona: nada se guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/carreras/{carreraId}/ruta")
@Funcionalidad("habitos.km.carreras")
public class RutaDeCarreraDelAlumnoController {

    private final ConsultarRutaDeCarreraDelAlumnoUseCase rutas;

    public RutaDeCarreraDelAlumnoController(ConsultarRutaDeCarreraDelAlumnoUseCase rutas) {
        this.rutas = rutas;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "RutaDeCarreraDelAlumnoService: acompanante vigente del grupo Y el alumno debe pertenecer a ese grupo")
    @GetMapping
    public ResponseEntity<RutaLeida> ruta(@ActorAutenticado UserId actorId, @PathVariable UUID groupId, @PathVariable UUID userId,
                                          @PathVariable UUID carreraId) {
        RutaLeida ruta = rutas.rutaDe(new ConsultaRuta(actorId, groupId, UserId.of(userId), carreraId))
                .orElseThrow(() -> new NoSuchElementException("Esta carrera no tiene una ruta guardada."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ruta);
    }
}
