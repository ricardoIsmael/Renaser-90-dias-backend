package com.renaser.os.rag.application.ports.out.ia;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Puerto de chat con streaming para Renasia. {@code reactor-core} ya está en el
 * classpath como dependencia directa de {@code spring-ai-client-chat} — no hace falta
 * WebFlux: un {@code @RestController} normal devolviendo {@code Flux<String>} con
 * {@code produces = TEXT_EVENT_STREAM_VALUE} alcanza (docs/MODULO_RAG.md sec. 4.bis,
 * verificado contra el bytecode del JAR).
 *
 * <p><b>SIN IA en este alcance</b>: la única implementación es
 * {@code NoOpRenasiaChatAdapter} — la integración real con Gemini vía Spring AI
 * {@code ChatClient} es una fase futura (D-39, faltan credenciales).
 *
 * <p><b>CONTRATO COMPARTIDO — firma congelada</b> (la usa el agregado
 * {@code conversacion}, de otro agente de este mismo módulo).
 */
public interface ChatIAPort {

    Flux<String> responder(String prompt, List<String> contexto);
}
