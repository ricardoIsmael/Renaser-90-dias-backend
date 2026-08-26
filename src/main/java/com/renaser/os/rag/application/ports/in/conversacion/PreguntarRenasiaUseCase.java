package com.renaser.os.rag.application.ports.in.conversacion;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;

/**
 * Preguntarle a Renasia. La respuesta viaja en streaming (docs/MODULO_RAG.md §4.bis: un
 * {@code @RestController} normal que devuelve {@code Flux<String>} alcanza sin WebFlux,
 * porque {@code spring-webmvc} ya trae {@code ReactiveTypeHandler}).
 */
public interface PreguntarRenasiaUseCase {

    Flux<String> preguntar(PreguntarRenasiaCommand command);

    record PreguntarRenasiaCommand(@NotNull UserId actorId, @NotBlank String pregunta) {

        public PreguntarRenasiaCommand {
            SelfValidating.validateConstructorArgs(PreguntarRenasiaCommand.class, actorId, pregunta);
        }
    }
}
