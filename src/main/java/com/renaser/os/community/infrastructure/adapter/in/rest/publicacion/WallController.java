package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarReaccionesUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EditarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EditarPublicacionUseCase.EditarPublicacionCommand;
import com.renaser.os.community.application.ports.in.publicacion.EliminarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EliminarPublicacionUseCase.EliminarPublicacionCommand;
import com.renaser.os.community.application.ports.in.publicacion.OcultarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.OcultarPublicacionUseCase.OcultarPublicacionCommand;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase.PublicarCommand;
import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase.ReaccionarCommand;
import com.renaser.os.community.application.ports.in.publicacion.RestaurarPublicacionUseCase;
import com.renaser.os.community.application.ports.in.publicacion.RestaurarPublicacionUseCase.RestaurarPublicacionCommand;
import com.renaser.os.community.application.ports.in.publicacion.SolicitarUrlSubidaMediaUseCase;
import com.renaser.os.community.application.ports.in.publicacion.SolicitarUrlSubidaMediaUseCase.SolicitarUrlSubidaMediaCommand;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El Muro (feed). Mismas rutas que el codigo viejo (CLAUDE.MD sec. 8 — el contrato con
 * la app RN no cambia): GET/POST /api/v1/wall, PATCH/DELETE /api/v1/wall/:id, restore,
 * permanent, hidden, mine, latest-author. Los comentarios viven en
 * {@link WallCommentController} (ruta anidada, misma razon que el codigo viejo:
 * "el muro es para publicar, los comentarios para responder").
 */
@RestController
@RequestMapping("/api/v1/wall")
public class WallController {

    private final ConsultarFeedUseCase consultarFeedUseCase;
    private final PublicarUseCase publicarUseCase;
    private final EditarPublicacionUseCase editarUseCase;
    private final OcultarPublicacionUseCase ocultarUseCase;
    private final RestaurarPublicacionUseCase restaurarUseCase;
    private final EliminarPublicacionUseCase eliminarUseCase;
    private final ReaccionarUseCase reaccionarUseCase;
    private final ConsultarReaccionesUseCase consultarReaccionesUseCase;
    private final SolicitarUrlSubidaMediaUseCase solicitarUrlUseCase;

    public WallController(ConsultarFeedUseCase consultarFeedUseCase, PublicarUseCase publicarUseCase,
                           EditarPublicacionUseCase editarUseCase, OcultarPublicacionUseCase ocultarUseCase,
                           RestaurarPublicacionUseCase restaurarUseCase, EliminarPublicacionUseCase eliminarUseCase,
                           ReaccionarUseCase reaccionarUseCase, ConsultarReaccionesUseCase consultarReaccionesUseCase,
                           SolicitarUrlSubidaMediaUseCase solicitarUrlUseCase) {
        this.consultarFeedUseCase = consultarFeedUseCase;
        this.publicarUseCase = publicarUseCase;
        this.editarUseCase = editarUseCase;
        this.ocultarUseCase = ocultarUseCase;
        this.restaurarUseCase = restaurarUseCase;
        this.eliminarUseCase = eliminarUseCase;
        this.reaccionarUseCase = reaccionarUseCase;
        this.consultarReaccionesUseCase = consultarReaccionesUseCase;
        this.solicitarUrlUseCase = solicitarUrlUseCase;
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public WallFeedPageResponse feed(@ActorAutenticado UserId actorId,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) String category) {
        Instant cursorInstant = parseCursor(cursor);
        return WallFeedPageResponse.from(consultarFeedUseCase.feed(actorId, cursorInstant, category));
    }

    @RequiresPermission(Permission.PUBLISH_ON_WALL)
    @PostMapping
    public ResponseEntity<WallPostResponse> publicar(@ActorAutenticado UserId actorId,
                                                       @RequestBody @Valid CreateWallPostRequest request) {
        var vista = publicarUseCase.publicar(new PublicarCommand(actorId, request.text(),
                request.media().stream().map(MediaItemRequest::aArchivoEntrada).toList(), request.category()));
        return ResponseEntity.status(HttpStatus.CREATED).body(WallPostResponse.from(vista));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "solo el autor de la publicacion")
    @PatchMapping("/{id}")
    public WallPostResponse actualizar(@ActorAutenticado UserId actorId, @PathVariable UUID id,
                                        @RequestBody @Valid UpdateWallPostRequest request) {
        var vista = editarUseCase.editar(new EditarPublicacionCommand(actorId, PublicacionId.of(id),
                request.text(), request.media().stream().map(MediaItemRequest::aArchivoEntrada).toList()));
        return WallPostResponse.from(vista);
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "el autor de la publicacion, o quien tenga MODERATE_WALL")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> ocultar(@ActorAutenticado UserId actorId,
                                                         @PathVariable UUID id) {
        ocultarUseCase.ocultar(new OcultarPublicacionCommand(actorId, PublicacionId.of(id)));
        return ResponseEntity.ok(Map.of("id", id.toString()));
    }

    @RequiresPermission(Permission.MODERATE_WALL)
    @PostMapping("/{id}/restore")
    public ResponseEntity<Map<String, String>> restaurar(@ActorAutenticado UserId actorId,
                                                           @PathVariable UUID id) {
        restaurarUseCase.restaurar(new RestaurarPublicacionCommand(actorId, PublicacionId.of(id)));
        return ResponseEntity.ok(Map.of("id", id.toString()));
    }

    @RequiresPermission(Permission.MODERATE_WALL)
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Map<String, String>> eliminarPermanente(@ActorAutenticado UserId actorId,
                                                                    @PathVariable UUID id) {
        eliminarUseCase.eliminarPermanente(new EliminarPublicacionCommand(actorId, PublicacionId.of(id)));
        return ResponseEntity.ok(Map.of("id", id.toString()));
    }

    @RequiresPermission(Permission.MODERATE_WALL)
    @GetMapping("/hidden")
    public WallFeedPageResponse hidden(@ActorAutenticado UserId actorId,
                                        @RequestParam(required = false) String cursor) {
        return WallFeedPageResponse.from(consultarFeedUseCase.feedOculto(actorId, parseCursor(cursor)));
    }

    // TODO(auth fase 4): sin clasificar. Recibe actor pero contarMisPublicaciones no ejecuta ningun guard: con el respaldo de X-Actor-Id devuelve el conteo de cualquier userId que el cliente declare. NO marcar publico por defecto.
    @GetMapping("/mine")
    public Map<String, Integer> mine(@ActorAutenticado UserId actorId) {
        return Map.of("count", consultarFeedUseCase.contarMisPublicaciones(actorId));
    }

    // TODO(auth fase 4): sin clasificar. Declara actor pero ultimoAutor() ni lo recibe: el parametro se ignora, asi que hoy es publico de hecho sin que nadie lo haya decidido. NO marcar publico por defecto.
    @GetMapping("/latest-author")
    public Map<String, String> latestAuthor(@ActorAutenticado UserId actorId) {
        Map<String, String> body = new HashMap<>();
        body.put("authorName", consultarFeedUseCase.ultimoAutor(actorId).orElse(null));
        return body;
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping("/{id}/react")
    public WallReactionToggleResponse reaccionar(@ActorAutenticado UserId actorId, @PathVariable UUID id,
                                                  @RequestBody @Valid ReactToWallPostRequest request) {
        var resultado = reaccionarUseCase.reaccionar(new ReaccionarCommand(actorId, PublicacionId.of(id),
                parseTipoReaccion(request.type())));
        return WallReactionToggleResponse.from(resultado);
    }

    /** Quien puede ver la publicacion puede ver sus reacciones — mismo permiso que
     * {@link #feed} y {@link #reaccionar}, no el criterio "sin clasificar" de
     * {@code WallCommentController#listar} (TODO auth fase 4, ese es un hueco declarado, no
     * un patron a copiar). */
    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/{id}/reactions")
    public WallReactionsResponse reacciones(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        return WallReactionsResponse.from(consultarReaccionesUseCase.reacciones(actorId, PublicacionId.of(id)));
    }

    @RequiresPermission(Permission.PUBLISH_ON_WALL)
    @PostMapping("/media/upload-url")
    public UrlSubidaMediaResponse urlDeSubida(@ActorAutenticado UserId actorId,
                                               @RequestBody SolicitarUrlSubidaMediaRequest request) {
        var url = solicitarUrlUseCase.solicitarUrl(new SolicitarUrlSubidaMediaCommand(actorId,
                request.tipoContenido()));
        return UrlSubidaMediaResponse.from(url);
    }

    private static Instant parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(cursor);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor");
        }
    }

    private static TipoReaccion parseTipoReaccion(String type) {
        return switch (type) {
            case "LIKE" -> TipoReaccion.ME_GUSTA;
            case "DISLIKE" -> TipoReaccion.NO_ME_GUSTA;
            default -> throw new IllegalArgumentException("type invalido: " + type);
        };
    }
}
