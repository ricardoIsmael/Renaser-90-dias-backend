package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.habits.api.RutaDeCarreraFinder.RutaLeida;
import com.renaser.os.leadership.application.ports.in.ConsultarRutaDeCarreraDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarRutaDeCarreraDePersonaUseCase.ConsultaRutaDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarRutaDeCarreraDePersonaUseCase.ConsultaRutaDeUnaPersona;
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
import java.util.Optional;
import java.util.UUID;

/**
 * La ruta de una carrera con GPS de una persona (spec 042, FR-041), en solo lectura, para el líder de mentores, Administración y
 * Alquimista: {@code GET /api/v1/leadership/groups/{groupId}/people/{personId}/carreras/{carreraId}/ruta} y, sin grupo,
 * {@code GET /api/v1/leadership/people/{personId}/carreras/{carreraId}/ruta}. El permiso declarado no alcanza solo (A-1): decide
 * {@code AccesoDeLiderazgo}. 404 si esa persona no tiene esa carrera o no guardó (o ya se borró) su ruta. Datos de una persona: nada
 * se guarda en caché.
 */
@RestController
@RequestMapping("/api/v1/leadership")
@Funcionalidad("habitos.km.carreras")
public class RutaDeCarreraDelLiderController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1); "
            + "persona de un grupo regular con mentor, o aprendiz que se muestra";

    private final ConsultarRutaDeCarreraDePersonaUseCase rutas;

    public RutaDeCarreraDelLiderController(ConsultarRutaDeCarreraDePersonaUseCase rutas) {
        this.rutas = rutas;
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/groups/{groupId}/people/{personId}/carreras/{carreraId}/ruta")
    public ResponseEntity<RutaLeida> deUnGrupo(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                               @PathVariable UUID personId, @PathVariable UUID carreraId) {
        return responder(rutas.rutaDe(new ConsultaRutaDePersona(actorId, groupId, personId, carreraId)));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/people/{personId}/carreras/{carreraId}/ruta")
    public ResponseEntity<RutaLeida> sinGrupo(@ActorAutenticado UserId actorId, @PathVariable UUID personId,
                                              @PathVariable UUID carreraId) {
        return responder(rutas.rutaDeUnaPersona(new ConsultaRutaDeUnaPersona(actorId, personId, carreraId)));
    }

    private static ResponseEntity<RutaLeida> responder(Optional<RutaLeida> ruta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ruta.orElseThrow(() -> new NoSuchElementException("Esta carrera no tiene una ruta guardada.")));
    }
}
