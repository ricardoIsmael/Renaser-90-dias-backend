package com.renaser.os.community.infrastructure.adapter.in.rest.testimonio;

import com.renaser.os.community.application.ports.in.testimonio.ConsultarTestimoniosUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase.CrearTestimonioCommand;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase.PromoverPublicacionCommand;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * GET/POST /api/v1/testimonios. Un {@code wallPostId} en el body de POST activa la
 * promocion (solo ADMIN/ALCHEMIST) en vez del registro manual — mismo dispatch que
 * testimonios/route.ts:22-32.
 */
@RestController
@RequestMapping("/api/v1/testimonios")
public class TestimonioController {

    private final ConsultarTestimoniosUseCase consultarUseCase;
    private final CrearTestimonioUseCase crearUseCase;
    private final PromoverPublicacionATestimonioUseCase promoverUseCase;

    public TestimonioController(ConsultarTestimoniosUseCase consultarUseCase, CrearTestimonioUseCase crearUseCase,
                                 PromoverPublicacionATestimonioUseCase promoverUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.crearUseCase = crearUseCase;
        this.promoverUseCase = promoverUseCase;
    }

    @GetMapping
    public List<TestimonioResponse> listar() {
        return consultarUseCase.listarDestacados().stream().map(TestimonioResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<TestimonioResponse> crear(
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @RequestBody CreateTestimonioRequest request) {
        int estrellas = request.estrellas() != null ? request.estrellas() : 5;
        if (request.wallPostId() != null && !request.wallPostId().isBlank()) {
            UserId actor = requireActorId(actorId);
            var vista = promoverUseCase.promover(new PromoverPublicacionCommand(actor,
                    PublicacionId.of(UUID.fromString(request.wallPostId())), estrellas));
            return ResponseEntity.status(HttpStatus.CREATED).body(TestimonioResponse.from(vista));
        }
        UserId actor = (actorId == null || actorId.isBlank()) ? null : UserId.of(actorId);
        var vista = crearUseCase.crear(new CrearTestimonioCommand(actor, request.nombre(), request.rol(),
                request.texto(), estrellas));
        return ResponseEntity.status(HttpStatus.CREATED).body(TestimonioResponse.from(vista));
    }

    private static UserId requireActorId(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new NotAuthorizedException("Se requiere sesion para promover");
        }
        return UserId.of(actorId);
    }
}
