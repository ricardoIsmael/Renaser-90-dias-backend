package com.renaser.os.rag.application.ports.in.conversacion;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;

/**
 * Preguntarle a uno de los dos asistentes (D-102). La respuesta viaja en streaming
 * (docs/MODULO_RAG.md §4.bis: un {@code @RestController} normal que devuelve {@code Flux<...>}
 * alcanza sin WebFlux, porque {@code spring-webmvc} ya trae {@code ReactiveTypeHandler}).
 *
 * <p>Devuelve {@link EventoRenasia}, no {@code String}: es el mismo tipo con el que habla
 * {@code ChatIAPort}, con {@code Fuentes} inyectado antes del {@code Fin} — ver
 * {@code ConversacionRenasiaService#preguntar}. {@code RenasiaController} lo traduce a las
 * formas fijas del contrato SSE (docs/MODULO_RAG.md §4.bis).
 */
public interface PreguntarRenasiaUseCase {

    Flux<EventoRenasia> preguntar(PreguntarRenasiaCommand command);

    /**
     * {@code agente}: cual de los dos asistentes responde (obligatorio). {@code ambito} y
     * {@code cursoId} solo tienen sentido para {@link AgenteConversacional#COURSE_TUTOR}:
     * {@code ambito} es el texto "el curso X, leccion Y" que va al prompt de sistema (nunca se
     * guarda como parte de la pregunta, D-100) y {@code cursoId} acota el contexto recuperado a
     * las lecciones de ese curso. Para el acompanante se descartan aca mismo: un cliente viejo
     * (anterior a D-102) que mande {@code scope} sin {@code agent} cae en el acompanante y no
     * arrastra el ambito a un prompt que ya no lo tiene.
     */
    record PreguntarRenasiaCommand(@NotNull UserId actorId, @NotNull AgenteConversacional agente,
                                   @NotBlank String pregunta, String ambito, String cursoId) {
        public PreguntarRenasiaCommand {
            SelfValidating.validateConstructorArgs(PreguntarRenasiaCommand.class, actorId, agente, pregunta,
                    ambito, cursoId);
            if (agente != AgenteConversacional.COURSE_TUTOR) {
                ambito = null;
                cursoId = null;
            }
        }
    }
}
