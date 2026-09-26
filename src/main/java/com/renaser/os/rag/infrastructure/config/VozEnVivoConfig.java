package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.rag.domain.model.conversacion.CuotaDeVozEnVivo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Los limites de la voz en vivo (D-162) como valor de dominio. Existe aunque la voz en vivo este
 * apagada: el caso de uso siempre se arma, y con el interruptor apagado responde "no disponible".
 *
 * <p>Los 20 minutos por dia son decision del dueno para produccion (2026-09-25, ver
 * {@code application.yaml}); los 15 por sesion, el tope de Gemini Live para una sesion de solo
 * audio. Se cambian por entorno sin tocar codigo.
 *
 * <p>Corregido 2026-09-26 (D-171): decia "los 10 minutos por dia" (la decision del 2026-09-24, §8
 * de {@code docs/arquitectura/PROPUESTA_GEMINI_LIVE.md}) y el respaldo de {@code @Value} era 10,
 * mientras {@code application.yaml} ya fijaba 20. El respaldo pasa a 20 para que no se contradigan.
 */
@Configuration
class VozEnVivoConfig {

    @Bean
    CuotaDeVozEnVivo cuotaDeVozEnVivo(
            @Value("${renaser.ia.voz.en-vivo.minutos-por-dia:20}") long minutosPorDia,
            @Value("${renaser.ia.voz.en-vivo.minutos-maximos-por-sesion:15}") long minutosPorSesion) {
        return new CuotaDeVozEnVivo(Duration.ofMinutes(minutosPorDia), Duration.ofMinutes(minutosPorSesion));
    }
}
