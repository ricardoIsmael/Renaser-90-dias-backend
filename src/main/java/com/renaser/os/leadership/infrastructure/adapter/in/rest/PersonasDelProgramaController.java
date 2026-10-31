package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarCierresDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarCierresDePersonaUseCase.ConsultaCierresDeUnaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarHuecosDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarHuecosDePersonaUseCase.ConsultaHuecosDeUnaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarPersonasDelProgramaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarPersonasDelProgramaUseCase.ConsultaPersonasDelPrograma;
import com.renaser.os.leadership.application.ports.in.ConsultarRadarDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarRadarDePersonaUseCase.ConsultaRadarDeUnaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.ConsultaSemanaDeUnaPersona;
import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * El líder de mentores (y Administración y Alquimista) ve a TODOS los aprendices, estén o no en un grupo (spec 021,
 * decisión del dueño del 2026-10-31):
 * <ul>
 *   <li>{@code GET /api/v1/leadership/people?q=&page=0&size=30} — la lista de los aprendices que se muestran, por nombre.</li>
 *   <li>{@code GET /api/v1/leadership/people/{personId}/week?semanaHasta=YYYY-MM-DD} — su semana, día por día, con sus
 *       evidencias: el MISMO cuerpo que {@code /groups/{g}/people/{p}/week}. Registra la consulta (FR-016).</li>
 *   <li>{@code GET …/radar?cursor=}, {@code …/huecos?semana=} y {@code …/cierres?semana=} — los mismos cuerpos que sus
 *       versiones por grupo. Huecos y cierres registran la consulta; el Radar no, como en el grupo.</li>
 * </ul>
 *
 * <p>Solo lectura. El permiso declarado no alcanza solo: MENTOR pasa el interceptor sin que se le mire nada (A-1) y
 * MENTOR_LEAD está en modo sombra. Quien decide es {@code AccesoDeLiderazgo}, el mismo de las rutas por grupo. Misma
 * funcionalidad de Soporte TI que ellas ({@code lider.cumplimiento}). Datos de personas: nada se guarda en caché
 * ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/leadership/people")
@Funcionalidad("lider.cumplimiento")
public class PersonasDelProgramaController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1); "
            + "aprendiz del programa que se muestra, con o sin grupo";

    private final ConsultarPersonasDelProgramaUseCase personas;
    private final ConsultarSemanaDePersonaUseCase semanaDePersona;
    private final ConsultarRadarDePersonaUseCase radarDePersona;
    private final ConsultarHuecosDePersonaUseCase huecosDePersona;
    private final ConsultarCierresDePersonaUseCase cierresDePersona;

    public PersonasDelProgramaController(ConsultarPersonasDelProgramaUseCase personas,
                                         ConsultarSemanaDePersonaUseCase semanaDePersona,
                                         ConsultarRadarDePersonaUseCase radarDePersona,
                                         ConsultarHuecosDePersonaUseCase huecosDePersona,
                                         ConsultarCierresDePersonaUseCase cierresDePersona) {
        this.personas = personas;
        this.semanaDePersona = semanaDePersona;
        this.radarDePersona = radarDePersona;
        this.huecosDePersona = huecosDePersona;
        this.cierresDePersona = cierresDePersona;
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping
    public ResponseEntity<PersonasDelProgramaResponse> lista(@ActorAutenticado UserId actorId,
                                                             @RequestParam(required = false) String q,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "30") int size) {
        return sinCache(PersonasDelProgramaResponse.from(
                personas.listar(new ConsultaPersonasDelPrograma(actorId, q, page, size))));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/{personId}/week")
    public ResponseEntity<SemanaDeLaPersonaResponse> semana(@ActorAutenticado UserId actorId,
                                                            @PathVariable UUID personId,
                                                            @RequestParam(required = false)
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                            LocalDate semanaHasta,
                                                            @RequestParam(required = false)
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                            LocalDate hasta) {
        // `semanaHasta` es el nombre de la ruta por grupo y manda aquí también (ajuste del contrato del 2026-10-31: la app
        // ya arma esa URL). `hasta`, el primer nombre del contrato, se acepta como alias: un parámetro desconocido se
        // ignoraría en silencio y la ficha abriría en otra ventana sin avisar.
        LocalDate viernes = semanaHasta != null ? semanaHasta : hasta;
        return sinCache(SemanaDeLaPersonaResponse.from(
                semanaDePersona.semanaDeUnaPersona(new ConsultaSemanaDeUnaPersona(actorId, personId, viernes))));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/{personId}/radar")
    public ResponseEntity<RadarDeLaPersonaResponse> radar(@ActorAutenticado UserId actorId, @PathVariable UUID personId,
                                                          @RequestParam(required = false) Instant cursor) {
        return sinCache(RadarDeLaPersonaResponse.from(
                radarDePersona.radarDeUnaPersona(new ConsultaRadarDeUnaPersona(actorId, personId, cursor))));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/{personId}/huecos")
    public ResponseEntity<HuecosDeLaSemana> huecos(@ActorAutenticado UserId actorId, @PathVariable UUID personId,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana) {
        return sinCache(huecosDePersona.huecosDeUnaPersona(new ConsultaHuecosDeUnaPersona(actorId, personId, semana)));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/{personId}/cierres")
    public ResponseEntity<CierresDeLaSemana> cierres(@ActorAutenticado UserId actorId, @PathVariable UUID personId,
                                                     @RequestParam(required = false)
                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana) {
        return sinCache(cierresDePersona.cierresDeUnaPersona(new ConsultaCierresDeUnaPersona(actorId, personId, semana)));
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
