package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase.ConsultaAnalisisDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase.ConsultaAnalisisDeUnaPersona;
import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
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
import java.util.Locale;
import java.util.UUID;

/**
 * {@code GET /api/v1/leadership/groups/{groupId}/people/{personId}/analisis-80-20?periodo=SEMANA|MES&inicio=YYYY-MM-DD} y
 * {@code GET /api/v1/leadership/people/{personId}/analisis-80-20?...} (sin pasar por un grupo, spec 021).
 *
 * <p>El 80/20 y el análisis de una persona para el líder de mentores, Administración y Alquimista (spec 031), con la misma respuesta que la
 * ruta del mentor. Solo lectura. El permiso declarado no alcanza solo: quien decide es {@code AccesoDeLiderazgo}, el mismo de {@code
 * …/week}, {@code …/huecos} y {@code …/cierres}; además respeta «Código Renaser en las fichas» (404 apagado) y deja constancia de la
 * consulta. Datos de personas: {@code no-store}.
 */
@RestController
@RequestMapping("/api/v1/leadership")
@Funcionalidad("lider.cumplimiento")
public class AnalisisDeLaPersonaController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1); "
            + "grupo regular con mentor y persona de ese grupo hoy (o aprendiz visible sin grupo); radar.fichas";

    private final ConsultarAnalisisDePersonaUseCase analisisDePersona;

    public AnalisisDeLaPersonaController(ConsultarAnalisisDePersonaUseCase analisisDePersona) {
        this.analisisDePersona = analisisDePersona;
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/groups/{groupId}/people/{personId}/analisis-80-20")
    public ResponseEntity<VistaDelAnalisis> deUnGrupo(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                                       @PathVariable UUID personId, @RequestParam(defaultValue = "SEMANA") String periodo,
                                                       @RequestParam(required = false)
                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio) {
        return sinCache(analisisDePersona.analisisDe(new ConsultaAnalisisDePersona(actorId, groupId, personId, tipo(periodo), inicio)));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/people/{personId}/analisis-80-20")
    public ResponseEntity<VistaDelAnalisis> sinGrupo(@ActorAutenticado UserId actorId, @PathVariable UUID personId,
                                                      @RequestParam(defaultValue = "SEMANA") String periodo,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio) {
        return sinCache(analisisDePersona.analisisDeUnaPersona(new ConsultaAnalisisDeUnaPersona(actorId, personId, tipo(periodo), inicio)));
    }

    private static Tipo tipo(String periodo) {
        try {
            return Tipo.valueOf(periodo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El periodo es SEMANA o MES");
        }
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
