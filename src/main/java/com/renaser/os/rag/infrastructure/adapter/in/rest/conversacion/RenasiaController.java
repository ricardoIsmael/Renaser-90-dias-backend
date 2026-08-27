package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.rag.application.ports.in.conversacion.ObtenerHistorialUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase;
import com.renaser.os.rag.application.ports.in.conversacion.PreguntarRenasiaUseCase.PreguntarRenasiaCommand;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Instant;

/**
 * Chat de Renasia. {@code preguntar} devuelve {@code Flux<String>} con
 * {@code text/event-stream}: Spring MVC lo adapta a streaming sobre el
 * {@code HttpServletResponse} sin necesidad de WebFlux (docs/MODULO_RAG.md §4.bis,
 * verificado contra el bytecode real de {@code spring-webmvc:7.0.9}).
 */
@RestController
@RequestMapping("/api/v1/renasia/mensajes")
public class RenasiaController {

    private final PreguntarRenasiaUseCase preguntarUseCase;
    private final ObtenerHistorialUseCase obtenerHistorialUseCase;

    public RenasiaController(PreguntarRenasiaUseCase preguntarUseCase, ObtenerHistorialUseCase obtenerHistorialUseCase) {
        this.preguntarUseCase = preguntarUseCase;
        this.obtenerHistorialUseCase = obtenerHistorialUseCase;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> preguntar(@ActorAutenticado UserId actorId,
                                   @RequestBody @Valid PreguntarRenasiaRequest request) {
        return preguntarUseCase.preguntar(new PreguntarRenasiaCommand(actorId, request.question()));
    }

    @GetMapping
    public HistorialRenasiaPageResponse historial(@ActorAutenticado UserId actorId,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false, defaultValue = "30") int limit) {
        Instant cursorInstant = cursor != null ? Instant.parse(cursor) : null;
        var pagina = obtenerHistorialUseCase.obtenerHistorial(actorId, cursorInstant, limit);
        return HistorialRenasiaPageResponse.from(pagina);
    }
}
