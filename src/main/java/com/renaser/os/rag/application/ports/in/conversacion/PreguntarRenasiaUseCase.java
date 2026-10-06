package com.renaser.os.rag.application.ports.in.conversacion;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.CanalConversacion;
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
     * {@code agente}: a cual de los dos asistentes le habla el cliente (obligatorio). Desde D-255
     * (2026-10-06) responde SER en los dos casos ({@link #paraQuienResponde()}).
     *
     * <p>{@code ambito} y {@code cursoId}: desde donde pregunta la persona cuando abre el chat
     * dentro de un curso de Classroom. {@code ambito} es el texto "el curso X, leccion Y" que va al
     * prompt de sistema (nunca se guarda como parte de la pregunta, D-100) y {@code cursoId} acota
     * el material recuperado a las lecciones visibles de ese curso — la misma busqueda que tenia
     * Sparkie. Hasta D-255 se descartaban para el acompanante, porque solo el tutor los usaba; ahora
     * SER atiende tambien el chat del curso y los necesita.
     *
     * <p>{@code canal} (2026-09-23): si la respuesta se va a leer o a escuchar. Nulo es
     * {@link CanalConversacion#TEXTO}, el comportamiento de siempre.
     */
    record PreguntarRenasiaCommand(@NotNull UserId actorId, @NotNull AgenteConversacional agente,
                                   @NotBlank String pregunta, String ambito, String cursoId,
                                   CanalConversacion canal) {
        public PreguntarRenasiaCommand {
            SelfValidating.validateConstructorArgs(PreguntarRenasiaCommand.class, actorId, agente, pregunta,
                    ambito, cursoId, canal);
            if (canal == null) {
                canal = CanalConversacion.TEXTO;
            }
        }

        /** El mismo turno, dirigido a quien de verdad responde (D-255: {@link AgenteConversacional#queResponde()}). */
        public PreguntarRenasiaCommand paraQuienResponde() {
            return new PreguntarRenasiaCommand(actorId, agente.queResponde(), pregunta, ambito, cursoId, canal);
        }
    }
}
