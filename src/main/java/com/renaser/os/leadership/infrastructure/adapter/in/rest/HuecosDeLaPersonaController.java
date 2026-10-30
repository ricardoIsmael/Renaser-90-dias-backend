package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarHuecosDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarHuecosDePersonaUseCase.ConsultaHuecosDePersona;
import com.renaser.os.mentoring.api.HuecosDeLaSemana;
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
 * {@code GET /api/v1/leadership/groups/{groupId}/people/{personId}/huecos?semana=YYYY-MM-DD}
 *
 * <p>Los huecos de horario de una persona de un grupo (spec 015) para el líder de mentores, Administración y
 * Alquimista, con la misma respuesta que la ruta del mentor. Solo lectura; la persona no los ve en esta primera
 * versión.
 *
 * <p>El permiso declarado no alcanza solo: MENTOR pasa el interceptor sin que se le mire nada (A-1) y MENTOR_LEAD está
 * en modo sombra. Quien decide es {@code AccesoDeLiderazgo}, el mismo de {@code …/week}. Datos de personas: nada se
 * guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/leadership")
@Funcionalidad("lider.cumplimiento")
public class HuecosDeLaPersonaController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1); "
            + "grupo regular con mentor y persona de ese grupo hoy";

    private final ConsultarHuecosDePersonaUseCase huecosDePersona;

    public HuecosDeLaPersonaController(ConsultarHuecosDePersonaUseCase huecosDePersona) {
        this.huecosDePersona = huecosDePersona;
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/groups/{groupId}/people/{personId}/huecos")
    public ResponseEntity<HuecosDeLaSemana> huecos(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                                   @PathVariable UUID personId,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana) {
        HuecosDeLaSemana huecos = huecosDePersona.huecosDe(
                new ConsultaHuecosDePersona(actorId, groupId, personId, semana));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(huecos);
    }
}
