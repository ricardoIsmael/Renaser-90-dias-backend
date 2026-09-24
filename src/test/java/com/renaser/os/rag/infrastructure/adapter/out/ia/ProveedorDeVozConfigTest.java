package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.SintetizarVozPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E-228: con {@code IA_VOZ_PROVEEDOR=google} (el mismo valor que {@code IA_PROVEEDOR}) el backend no
 * arrancaba, porque el adaptador de Gemini esperaba "gemini" y no quedaba ningun adaptador de voz.
 */
class ProveedorDeVozConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ProveedorDeVozConfig.class, NoOpVozAdapter.class, PiperVozAdapter.class,
                    GeminiVozAdapter.class)
            .withBean(org.springframework.web.client.RestClient.Builder.class,
                    org.springframework.web.client.RestClient::builder);

    @Test
    @DisplayName("google elige la voz de Gemini, igual que IA_PROVEEDOR=google elige el chat de Gemini")
    void googleEligeGemini() {
        contexto.withPropertyValues("renaser.ia.voz.proveedor=google", "spring.ai.google.genai.api-key=k",
                        "renaser.ia.voz.gemini.url=https://x", "renaser.ia.voz.gemini.timeout-ms=1000")
                .run(ctx -> assertThat(ctx.getBean(SintetizarVozPort.class)).isInstanceOf(GeminiVozAdapter.class));
    }

    @Test
    @DisplayName("sin valor queda noop: el backend arranca sin voz del servidor")
    void sinValorEsNoop() {
        contexto.run(ctx -> assertThat(ctx.getBean(SintetizarVozPort.class)).isInstanceOf(NoOpVozAdapter.class));
    }

    @Test
    @DisplayName("un valor desconocido falla al arrancar con un mensaje que nombra la variable y los validos")
    void valorDesconocidoFallaClaro() {
        contexto.withPropertyValues("renaser.ia.voz.proveedor=gemini")
                .run(ctx -> assertThat(ctx).hasFailed());
        assertThatThrownBy(() -> ProveedorDeVozConfig.validar("gemini"))
                .hasMessageContaining("IA_VOZ_PROVEEDOR").hasMessageContaining("google (voz Kore de Gemini)");
    }
}
