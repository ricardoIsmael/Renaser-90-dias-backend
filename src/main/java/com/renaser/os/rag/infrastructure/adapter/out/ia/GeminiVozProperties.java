package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code renaser.ia.voz.gemini.*} (D-159). {@code url} existe para las pruebas (un servidor falso);
 * en produccion es la API de Google. {@code estilo} es la instruccion de como hablar: es lo que
 * hace que la voz suene fluida y no leida. {@code timeoutMs} es lo maximo que se espera a que
 * Gemini empiece a responder.
 */
@ConfigurationProperties(prefix = "renaser.ia.voz.gemini")
record GeminiVozProperties(String url, String modelo, String voz, String estilo, int timeoutMs) {
}
