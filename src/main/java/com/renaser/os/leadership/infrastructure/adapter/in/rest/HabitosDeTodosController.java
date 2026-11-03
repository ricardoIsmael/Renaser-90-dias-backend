package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.ConsultaHabitosDeTodos;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.ConsultaPersonasDeUnHabito;
import com.renaser.os.shared.domain.Formacion;
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
 * «Hábitos de todos» (spec 029, decisión del dueño del 2026-10-31): el líder de mentores (y Administración y Alquimista) ve, hábito por hábito, quién cumplió
 * y quién no entre todos los alumnos, un día a la vez (hoy y hasta 7 atrás):
 * <ul>
 *   <li>{@code GET /api/v1/leadership/habits?date=&group=&formation=&category=} — un resumen del día y la lista de hábitos, los de menos cumplimiento primero.</li>
 *   <li>{@code GET /api/v1/leadership/habits/{habitId}/people?date=&group=&formation=&q=&page=&size=} — las personas de ese hábito con su estado.</li>
 * </ul>
 *
 * <p>Solo lectura y sin registro de consulta. El permiso declarado no alcanza solo: MENTOR pasa el interceptor sin que se le mire nada (A-1) y MENTOR_LEAD está
 * en modo sombra; decide {@code AccesoDeLiderazgo}. Misma funcionalidad de Soporte TI que el resto del cumplimiento del líder ({@code lider.cumplimiento}).
 * Datos de personas: nada se guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/leadership/habits")
@Funcionalidad("lider.cumplimiento")
public class HabitosDeTodosController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1); "
            + "solo alumnos de las formaciones de quien pide";

    private final ConsultarHabitosDeTodosUseCase habitos;

    public HabitosDeTodosController(ConsultarHabitosDeTodosUseCase habitos) {
        this.habitos = habitos;
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping
    public ResponseEntity<HabitosDeTodosResponse> resumen(@ActorAutenticado UserId actorId,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                          @RequestParam(required = false) String group,
                                                          @RequestParam(required = false) String formation,
                                                          @RequestParam(required = false) String category) {
        return sinCache(HabitosDeTodosResponse.from(habitos.resumen(
                new ConsultaHabitosDeTodos(actorId, date, group, formacion(formation), category))));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/{habitId}/people")
    public ResponseEntity<PersonasDeUnHabitoResponse> personas(@ActorAutenticado UserId actorId, @PathVariable UUID habitId,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @RequestParam(required = false) String group,
                                                               @RequestParam(required = false) String formation,
                                                               @RequestParam(required = false) String q,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "30") int size) {
        return sinCache(PersonasDeUnHabitoResponse.from(habitos.personas(
                new ConsultaPersonasDeUnHabito(actorId, habitId, date, group, formacion(formation), q, page, size))));
    }

    private static Formacion formacion(String codigo) {
        return codigo == null || codigo.isBlank() ? null : Formacion.desde(codigo);
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
