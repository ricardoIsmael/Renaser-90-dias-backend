package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarGrupoDelLiderUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarGrupoDelLiderUseCase.ConsultaGrupoDelLider;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.ConsultaSemanaDePersona;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
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
 * El cumplimiento de cada persona por grupo para el líder de mentores, Administración y Alquimista (spec 005):
 * <ul>
 *   <li>{@code GET /api/v1/leadership/groups/{groupId}/semaforo?semanaHasta=YYYY-MM-DD} — la lista del grupo.</li>
 *   <li>{@code GET /api/v1/leadership/groups/{groupId}/people/{personId}/week?semanaHasta=YYYY-MM-DD} — la semana
 *       de una persona, día por día, con sus evidencias. Registra la consulta (FR-016).</li>
 * </ul>
 *
 * <p>Solo lectura sobre la persona (FR-003). El permiso declarado no alcanza solo: MENTOR pasa el interceptor sin que
 * se le mire nada (A-1) y MENTOR_LEAD está en modo sombra. Quien decide es {@code AccesoDeLiderazgo}: MENTOR_LEAD,
 * ADMIN o ALCHEMIST con la cuenta activa. Datos de personas: nada se guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/leadership")
public class CumplimientoDelLiderController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1); "
            + "grupo regular con mentor y, para la ficha, persona de ese grupo hoy";

    private final ConsultarGrupoDelLiderUseCase grupoDelLider;
    private final ConsultarSemanaDePersonaUseCase semanaDePersona;

    public CumplimientoDelLiderController(ConsultarGrupoDelLiderUseCase grupoDelLider,
                                          ConsultarSemanaDePersonaUseCase semanaDePersona) {
        this.grupoDelLider = grupoDelLider;
        this.semanaDePersona = semanaDePersona;
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/groups/{groupId}/semaforo")
    public ResponseEntity<GrupoDelLiderResponse> grupo(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                      LocalDate semanaHasta) {
        return sinCache(GrupoDelLiderResponse.from(
                grupoDelLider.grupoDe(new ConsultaGrupoDelLider(actorId, groupId, semanaHasta))));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/groups/{groupId}/people/{personId}/week")
    public ResponseEntity<SemanaDeLaPersonaResponse> semana(@ActorAutenticado UserId actorId,
                                                            @PathVariable UUID groupId, @PathVariable UUID personId,
                                                            @RequestParam(required = false)
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                            LocalDate semanaHasta) {
        return sinCache(SemanaDeLaPersonaResponse.from(
                semanaDePersona.semanaDe(new ConsultaSemanaDePersona(actorId, groupId, personId, semanaHasta))));
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
