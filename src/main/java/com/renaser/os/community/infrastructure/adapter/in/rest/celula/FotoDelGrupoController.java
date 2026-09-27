package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.CambiarFotoDelGrupoUseCase;
import com.renaser.os.community.application.ports.in.celula.CambiarFotoDelGrupoUseCase.CambiarFotoDelGrupoCommand;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * {@code /api/v1/admin/cells/{id}/photo} — la foto propia de un grupo (D-212). La cambian el ADMIN y el
 * Alquimista (de cualquier grupo) y el mentor que acompaña ese grupo (solo el suyo); lo decide el caso de
 * uso. <i>Corregido 2026-09-27: el Alquimista se sumó por decisión del dueño.</i>
 *
 * <p><b>Por qué bajo {@code /admin/cells} aunque la use el mentor.</b> Es el mismo árbol y el mismo permiso
 * que {@code GET /api/v1/admin/cells/{id}}, que un mentor ya consulta sobre su grupo; el filtro de
 * seguridad ya lo cubre ({@code /api/v1/admin/**} exige sesión). {@code MANAGE_CELLS} deja afuera a un
 * aprendiz en el interceptor; al mentor lo deja pasar (su rol todavía no tiene matriz, A-1) y el caso de
 * uso lo acota a su grupo.
 *
 * <p><b>El único multipart de la API.</b> El resto de los archivos del teléfono sube por URL prefirmada
 * directo a S3; esta foto pasa por el servidor porque el servidor la lee, la recorta y la reescribe
 * antes de guardarla ({@code PrepararFotoDelGrupoPort}). Tope: 2 MB y JPEG o PNG.
 */
@RestController
@RequestMapping("/api/v1/admin/cells/{id}/photo")
public class FotoDelGrupoController {

    private static final String ALCANCE = "ADMIN y ALCHEMIST, cualquier grupo; el mentor que lo acompaña hoy, solo el suyo";

    private final CambiarFotoDelGrupoUseCase fotoUseCase;

    public FotoDelGrupoController(CambiarFotoDelGrupoUseCase fotoUseCase) {
        this.fotoUseCase = fotoUseCase;
    }

    /** Si el grupo tiene foto propia y desde cuándo ({@code photoChangedAt} en {@code null}: la de Renaser). */
    @RequiresPermission(value = Permission.MANAGE_CELLS, scope = ALCANCE)
    @GetMapping
    public FotoDelGrupoResponse actual(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        return FotoDelGrupoResponse.de(CelulaId.of(id), fotoUseCase.actual(actorId, CelulaId.of(id)).orElse(null));
    }

    /** La parte {@code foto} del multipart: JPEG o PNG de hasta 2 MB. */
    @RequiresPermission(value = Permission.MANAGE_CELLS, scope = ALCANCE)
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FotoDelGrupoResponse cambiar(@ActorAutenticado UserId actorId, @PathVariable UUID id,
                                        @RequestPart("foto") MultipartFile foto) throws IOException {
        return FotoDelGrupoResponse.de(CelulaId.of(id), fotoUseCase.cambiar(
                new CambiarFotoDelGrupoCommand(actorId, CelulaId.of(id), foto.getBytes(), foto.getContentType())));
    }

    /** El grupo vuelve a la foto de Renaser (la tarjeta que trae la app). Sin foto propia, no cambia nada. */
    @RequiresPermission(value = Permission.MANAGE_CELLS, scope = ALCANCE)
    @DeleteMapping
    public ResponseEntity<Void> volverALaDeRenaser(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        fotoUseCase.volverALaDeRenaser(actorId, CelulaId.of(id));
        return ResponseEntity.noContent().build();
    }
}
