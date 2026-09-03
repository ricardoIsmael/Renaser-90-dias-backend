package com.renaser.os.rag.application.ports.in.conversacion;

import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;

/**
 * Preguntarle a Renasia. La respuesta viaja en streaming (docs/MODULO_RAG.md §4.bis: un
 * {@code @RestController} normal que devuelve {@code Flux<...>} alcanza sin WebFlux,
 * porque {@code spring-webmvc} ya trae {@code ReactiveTypeHandler}).
 *
 * <p>Devuelve {@link EventoRenasia}, no {@code String}: es el mismo tipo con el que habla
 * {@code ChatIAPort}, con {@code Fuentes} inyectado antes del {@code Fin} — ver
 * {@code ConversacionRenasiaService#preguntar}. {@code RenasiaController} lo traduce a las
 * tres formas fijas del contrato SSE (docs/MODULO_RAG.md §4.bis).
 */
public interface PreguntarRenasiaUseCase {

    Flux<EventoRenasia> preguntar(PreguntarRenasiaCommand command);

    record PreguntarRenasiaCommand(@NotNull UserId actorId, @NotBlank String pregunta) {

        public PreguntarRenasiaCommand {
            SelfValidating.validateConstructorArgs(PreguntarRenasiaCommand.class, actorId, pregunta);
        }
    }
}
