package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase.ConsultaResumenPorGrupos;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * {@code GET /api/v1/semaforo/groups?semanaHasta=YYYY-MM-DD} — el semáforo de todos los grupos sin
 * nombres de aprendices, para el líder de mentores, el administrador y el alquimista
 * (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.4).
 *
 * <p>{@code USE_APP} es el permiso más chico que tienen los tres roles, y por eso NO alcanza: lo
 * tiene también TRAINEE, y MENTOR pasa el interceptor sin que se le mire nada (A-1). Quien decide
 * es el guard del servicio: MENTOR_LEAD, ADMIN o ALCHEMIST, con la cuenta activa.
 */
@RestController
@RequestMapping("/api/v1/semaforo/groups")
public class SemaforoPorGruposController {

    private final ConsultarSemaforoPorGruposUseCase consultarResumen;

    public SemaforoPorGruposController(ConsultarSemaforoPorGruposUseCase consultarResumen) {
        this.consultarResumen = consultarResumen;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "AccesoAVistasDelSemaforo.requireLiderazgoActivo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1)")
    @GetMapping
    public ResumenPorGruposResponse resumen(@ActorAutenticado UserId actorId,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semanaHasta) {
        return ResumenPorGruposResponse.from(consultarResumen.resumenDe(
                new ConsultaResumenPorGrupos(actorId, semanaHasta)));
    }
}
