package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code renaser.ia.voz.en-vivo.*} (D-162). {@code url} existe para las pruebas (un servidor falso);
 * en produccion es el WebSocket de Google, sin la key (la key viaja en el header
 * {@code x-goog-api-key}, nunca en la URL, para que no quede en ningun log). {@code timeoutMs} es lo
 * maximo que se espera a conectar y a que Gemini acepte la sesion ({@code setupComplete}, ~4 s).
 */
@ConfigurationProperties(prefix = "renaser.ia.voz.en-vivo")
record GeminiLiveProperties(String url, String modelo, String voz, int timeoutMs) {
}
