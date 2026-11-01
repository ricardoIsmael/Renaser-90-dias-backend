package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.CambiarVisibilidadDelCodigoRenaserUseCase;
import com.renaser.os.plataforma.application.ports.in.ConsultarVisibilidadDelCodigoRenaserUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET/PUT /api/v1/admin/codigo-renaser-en-fichas} (spec 034, D-299): el interruptor que decide si el mentor, la guía
 * y el líder ven el Código Renaser en las fichas de los aprendices. Lo usan Administración y Alquimista desde Yo →
 * Administración, y también Soporte TI (que además lo tiene en su consola, rol por rol).
 *
 * <p>El controller solo deserializa, autoriza e invoca un caso de uso. La guardia de roles es del servicio: el interceptor de
 * permisos falla abierto para algunos roles (A-1). Responde sin caché: es una decisión de privacidad y se lee recién cambiada.
 */
@RestController
@RequestMapping("/api/v1/admin/codigo-renaser-en-fichas")
@Funcionalidad("admin")
class CodigoRenaserEnFichasController {

    private static final String SCOPE = "VisibilidadDelCodigoRenaserService: solo ADMIN, ALCHEMIST e IT_SUPPORT activos; "
            + "el servicio rechaza a los demás roles y a las cuentas suspendidas";

    private final ConsultarVisibilidadDelCodigoRenaserUseCase consultar;
    private final CambiarVisibilidadDelCodigoRenaserUseCase cambiar;

    CodigoRenaserEnFichasController(ConsultarVisibilidadDelCodigoRenaserUseCase consultar,
                                    CambiarVisibilidadDelCodigoRenaserUseCase cambiar) {
        this.consultar = consultar;
        this.cambiar = cambiar;
    }

    @RequiresPermission(value = Permission.MANAGE_RADAR_VISIBILITY, scope = SCOPE)
    @GetMapping
    public ResponseEntity<CodigoRenaserEnFichasResponse> ver(@ActorAutenticado UserId actorId) {
        return sinCache(CodigoRenaserEnFichasResponse.from(consultar.consultar(actorId)));
    }

    @RequiresPermission(value = Permission.MANAGE_RADAR_VISIBILITY, scope = SCOPE)
    @PutMapping
    public ResponseEntity<CodigoRenaserEnFichasResponse> cambiar(@ActorAutenticado UserId actorId,
                                                                 @Valid @RequestBody CodigoRenaserEnFichasRequest request) {
        return sinCache(CodigoRenaserEnFichasResponse.from(cambiar.cambiar(actorId, request.visible())));
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
