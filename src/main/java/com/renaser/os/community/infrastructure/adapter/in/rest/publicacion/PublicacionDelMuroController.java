package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarPublicacionUseCase;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
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

import java.util.UUID;

/**
 * {@code GET /api/v1/wall/{id}} — UNA publicación del Muro, para «Ver publicación» en las fichas del líder, del mentor
 * y de administración (spec 005, R-6; decisión del 2026-10-19: el recurso es del Muro, no de {@code /leadership/}).
 *
 * <p>Va en su propio controller y no en {@link WallController} para no tocar los suyos: es una lectura con una regla de
 * acceso distinta de la del feed. El permiso declarado ({@code USE_APP}) lo tienen todos los roles, así que no protege
 * nada: quien decide es {@code ConsultarPublicacionService} (su autor, el liderazgo, o el mentor que acompaña hoy al
 * autor; cuenta suspendida, 403). Lleva las URL firmadas de la media: no se guarda en caché.
 */
@RestController
@RequestMapping("/api/v1/wall")
@Funcionalidad("muro")
public class PublicacionDelMuroController {

    private final ConsultarPublicacionUseCase consultarPublicacion;

    public PublicacionDelMuroController(ConsultarPublicacionUseCase consultarPublicacion) {
        this.consultarPublicacion = consultarPublicacion;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "ConsultarPublicacionService: su autor, MENTOR_LEAD/ADMIN/ALCHEMIST activos, o el mentor/guia que acompana hoy al autor; el permiso no alcanza")
    @GetMapping("/{id}")
    public ResponseEntity<PublicacionParaLeerResponse> publicacion(@ActorAutenticado UserId actorId,
                                                                   @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PublicacionParaLeerResponse.from(
                consultarPublicacion.publicacionDe(actorId, PublicacionId.of(id))));
    }
}
