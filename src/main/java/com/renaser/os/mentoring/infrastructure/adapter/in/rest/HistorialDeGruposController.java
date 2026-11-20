package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.mentoring.application.ports.in.ConsultarHistorialDeGruposUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarHistorialDeGruposUseCase.HistorialDeGrupos;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/mentor/me/groups/history} (spec 057, FR-020): los grupos que el mentor ya no acompaña, del más reciente al más viejo,
 * con cómo cerró cada uno. Autoconsulta pura —se arma de los tramos del actor, sin id ajeno en la petición— y solo lectura: nombres y
 * colores de las personas, sin ids ni datos de hoy.
 */
@RestController
@RequestMapping("/api/v1/mentor/me/groups/history")
@Funcionalidad("mentor.mi_grupo")
public class HistorialDeGruposController {

    private final ConsultarHistorialDeGruposUseCase historial;

    public HistorialDeGruposController(ConsultarHistorialDeGruposUseCase historial) {
        this.historial = historial;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "autoconsulta: se arma desde los tramos del actor, sin id ajeno en la peticion")
    @GetMapping
    public ResponseEntity<HistorialDeGrupos> historial(@ActorAutenticado UserId actorId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(historial.historialDe(actorId));
    }
}
