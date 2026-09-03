package com.renaser.os.community.infrastructure.adapter.in.rest.testimonio;

import com.renaser.os.community.application.ports.in.testimonio.ConsultarTestimoniosUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase.CrearTestimonioCommand;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase.PromoverPublicacionCommand;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    // TODO(auth fase 4): sin clasificar. No recibe actor ni ejecuta guard. Los testimonios destacados podrian ser contenido publico de marketing, pero el codigo no lo dice en ningun lado. NO marcar publico por defecto.
    @GetMapping
    public List<TestimonioResponse> listar() {
        return consultarUseCase.listarDestacados().stream().map(TestimonioResponse::from).toList();
    }

    // TODO(auth fase 4): sin clasificar. Un solo handler con dos autorizaciones segun el body: sin wallPostId acepta actor null y no valida nada; con wallPostId exige PROMOTE_TESTIMONIAL. No es declarable hasta partirlo en dos endpoints.
    @PostMapping
    public ResponseEntity<TestimonioResponse> crear(
            @ActorAutenticado(required = false) UserId actorId,
            @RequestBody CreateTestimonioRequest request) {
        int estrellas = request.estrellas() != null ? request.estrellas() : 5;
        if (request.wallPostId() != null && !request.wallPostId().isBlank()) {
            var vista = promoverUseCase.promover(new PromoverPublicacionCommand(requireActorId(actorId),
                    PublicacionId.of(UUID.fromString(request.wallPostId())), estrellas));
            return ResponseEntity.status(HttpStatus.CREATED).body(TestimonioResponse.from(vista));
        }
        var vista = crearUseCase.crear(new CrearTestimonioCommand(actorId, request.nombre(), request.rol(),
                request.texto(), estrellas));
        return ResponseEntity.status(HttpStatus.CREATED).body(TestimonioResponse.from(vista));
    }

    private static UserId requireActorId(UserId actorId) {
        if (actorId == null) {
            throw new NotAuthorizedException("Se requiere sesion para promover");
        }
        return actorId;
    }
}
