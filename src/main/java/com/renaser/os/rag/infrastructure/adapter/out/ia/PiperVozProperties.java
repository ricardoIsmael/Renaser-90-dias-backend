package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code renaser.ia.voz.*} (ver application.yaml). {@code proveedor} tambien vive bajo ese
 * prefijo, pero lo lee {@code @ConditionalOnProperty}, no este record.
 *
 * <p>Los tres ultimos son los parametros de Piper con el mismo nombre en snake_case:
 * {@code length_scale} (mayor = habla mas lento), {@code noise_scale} (variacion de entonacion) y
 * {@code noise_w_scale} (variacion del ritmo entre fonemas).
 */
@ConfigurationProperties(prefix = "renaser.ia.voz")
record PiperVozProperties(String url, int timeoutMs, double lengthScale, double noiseScale, double noiseWScale) {
}
