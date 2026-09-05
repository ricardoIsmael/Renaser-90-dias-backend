package com.renaser.os.rag.application.ports.out.ia;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * Le pregunta al modelo y devuelve la respuesta en streaming.
 *
 * <p>D-102 (2026-09-04): la firma pasa a recibir una {@link Consulta} que dice QUE agente habla.
 * Son dos asistentes distintos con dos prompts de sistema distintos (ver
 * {@link AgenteConversacional}); el adaptador elige el prompt segun el agente. Lo que D-100 habia
 * agregado como "un asistente con dos modos" queda repartido asi:
 * <ul>
 *   <li>{@code ambito}: sobre QUE curso/leccion esta hablando la persona ("el curso X, leccion Y").
 *   Solo tiene sentido para {@link AgenteConversacional#COURSE_TUTOR}; para el acompanante llega
 *   siempre nulo. Viaja al prompt de SISTEMA, nunca dentro de la pregunta: el primer intento lo
 *   mandaba concatenado al texto del aprendiz y el backend lo guardaba como si lo hubiera escrito
 *   el.</li>
 *   <li>{@code historial}: los ultimos turnos DEL MISMO AGENTE, en orden cronologico y SIN la
 *   pregunta actual. Sin esto el modelo no recordaba nada.</li>
 * </ul>
 *
 * <p>El adaptador nunca emite {@link EventoRenasia.Fuentes} ni {@link EventoRenasia.Error}: las
 * fuentes las arma el caso de uso a partir de lo recuperado, y el error lo traduce el caso de uso
 * cuando este Flux termina en error.
 */
public interface ChatIAPort {

    Flux<EventoRenasia> responder(Consulta consulta);

    /**
     * Todo lo que el modelo necesita para una respuesta. {@code ambito} es nulo para el
     * acompanante y opcional para el tutor de cursos; el resto es obligatorio.
     */
    record Consulta(AgenteConversacional agente, String pregunta, List<String> contexto, String ambito,
                    List<MensajeRenasia> historial) {

        public Consulta {
            Objects.requireNonNull(agente, "agente no puede ser null");
            Objects.requireNonNull(pregunta, "pregunta no puede ser null");
            contexto = List.copyOf(Objects.requireNonNull(contexto, "contexto no puede ser null"));
            historial = List.copyOf(Objects.requireNonNull(historial, "historial no puede ser null"));
        }
    }
}
