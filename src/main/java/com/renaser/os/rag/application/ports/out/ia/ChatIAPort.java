package com.renaser.os.rag.application.ports.out.ia;

import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Puerto de chat con streaming para Renasia. {@code reactor-core} ya está en el
 * classpath como dependencia directa de {@code spring-ai-client-chat} — no hace falta
 * WebFlux: un {@code @RestController} normal devolviendo {@code Flux<String>} con
 * {@code produces = TEXT_EVENT_STREAM_VALUE} alcanza (docs/MODULO_RAG.md sec. 4.bis,
 * verificado contra el bytecode del JAR).
 *
 * <p><b>Por qué {@link EventoRenasia} y no {@code Flux<String>}.</b> Un flujo de cadenas
 * sueltas no puede expresar que el modelo terminó, ni de dónde salió cada fragmento. La
 * implementación de este puerto solo emite {@link EventoRenasia.Texto} (uno por fragmento
 * generado) y, al final, exactamente un {@link EventoRenasia.Fin} — nunca
 * {@link EventoRenasia.Fuentes}: eso lo arma {@code ConversacionRenasiaService} a partir de
 * lo que {@code VectorStorePort} ya recuperó, porque este puerto solo ve texto de contexto,
 * no qué lección lo originó.
 *
 * <p>Implementaciones: {@code NoOpRenasiaChatAdapter} (activa mientras
 * {@code renaser.ia.proveedor=noop}, su default) y {@code GoogleGenAiRenasiaChatAdapter}
 * (activa con {@code renaser.ia.proveedor=google}, sobre el {@code ChatClient} de Spring AI).
 *
 * <p><b>CONTRATO COMPARTIDO — firma congelada</b> (la usa el agregado
 * {@code conversacion}, de otro agente de este mismo módulo).
 */
public interface ChatIAPort {

    Flux<EventoRenasia> responder(String prompt, List<String> contexto);
}
