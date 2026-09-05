package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Que el adaptador real se pueda CONSTRUIR dentro de un contexto de Spring.
 *
 * <p><b>Por que existe esta prueba.</b> El 2026-09-05 la suite entera paso en verde (2403 pruebas)
 * y la aplicacion no arrancaba:
 *
 * <pre>
 * Parameter 2 of constructor in ...GoogleGenAiRenasiaChatAdapter required a bean of type
 * 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
 * </pre>
 *
 * Ninguna prueba lo cubria porque este adaptador solo se instancia con
 * {@code renaser.ia.proveedor=google}, y las pruebas corren con {@code noop}: el bean nunca se
 * creaba, asi que un error de inyeccion era invisible hasta levantar la app a mano. Esta prueba
 * cierra ese hueco — resuelve el constructor de verdad, contra un contexto que tiene lo mismo que
 * tendria en produccion.
 *
 * <p>La causa de fondo esta documentada como E-33: Spring Boot 4.1 autoconfigura el
 * {@code ObjectMapper} de Jackson 3 ({@code tools.jackson.databind}), no el clasico
 * {@code com.fasterxml} que usa este codigo. Cualquier clase que lo pida por constructor deja la
 * app sin arrancar; hay que construirlo.
 *
 * <p><b>Verificada contra el codigo viejo:</b> con el {@code ObjectMapper} de vuelta en el
 * constructor, esta prueba falla con el mismo mensaje que mostro la app.
 */
class GoogleGenAiRenasiaChatAdapterContextTest {

    @Test
    @DisplayName("el adaptador real se construye sin pedir beans que Spring Boot 4.1 no expone")
    void seConstruyeEnUnContextoDeSpring() {
        new ApplicationContextRunner()
                // Sin esto el bean ni se crea: la clase es @ConditionalOnProperty(havingValue =
                // "google"). Que la prueba lo tenga que encender es exactamente el motivo por el
                // que el defecto fue invisible — las pruebas corren con el proveedor en `noop`.
                .withPropertyValues("renaser.ia.proveedor=google")
                // Lo unico que el contexto real le ofrece: el modelo y el ejecutor de herramientas.
                // Deliberadamente NO se registra un ObjectMapper — si el adaptador lo pidiera, esta
                // prueba fallaria igual que la app.
                .withBean(ChatModel.class, () -> mock(ChatModel.class))
                .withBean(EjecutarHerramientaAgenteUseCase.class, () -> mock(EjecutarHerramientaAgenteUseCase.class))
                .withBean(GoogleGenAiRenasiaChatAdapter.class)
                .run(contexto -> assertThat(contexto)
                        .hasNotFailed()
                        .hasSingleBean(GoogleGenAiRenasiaChatAdapter.class));
    }
}
