package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.rag.domain.model.aviso.AvisosEnChat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Convierte las propiedades {@code renaser.ia.acompanante.avisos-en-chat*} en el valor de dominio
 * {@link AvisosEnChat}. Mismo molde que {@link MensajeDeApoyoConfig}: la redaccion es una decision
 * del dueno que todavia no esta tomada (§5.1 de la propuesta del acompanante).
 *
 * <p>Los defaults de ACA (propiedad ausente) son los seguros: interruptor apagado y plantillas
 * vacias. Los textos provisorios viven solo en {@code application.yaml}, que es donde se leen y se
 * cambian sin tocar codigo.
 */
@Configuration
class AvisosEnChatConfig {

    /** Nombres de {@code habits.TipoAvisoHabito}, como viajan en {@code AvisoHabitoDebidoEvent.tipoAviso}. */
    static final String INICIO = "INICIO";
    static final String POR_VENCER = "POR_VENCER";

    @Bean
    AvisosEnChat avisosEnChat(@Value("${renaser.ia.acompanante.avisos-en-chat:false}") boolean activo,
                              @Value("${renaser.ia.acompanante.avisos-en-chat-tipos:}") String tipos,
                              @Value("${renaser.ia.acompanante.avisos-en-chat-plantilla-inicio:}") String inicio,
                              @Value("${renaser.ia.acompanante.avisos-en-chat-plantilla-por-vencer:}")
                              String porVencer) {
        return new AvisosEnChat(activo, separarTipos(tipos), Map.of(INICIO, inicio, POR_VENCER, porVencer));
    }

    /** "INICIO, POR_VENCER" → {INICIO, POR_VENCER}; los espacios y las comas de mas no cuentan. */
    static Set<String> separarTipos(String tipos) {
        return Arrays.stream(tipos.split(","))
                .map(String::strip)
                .filter(tipo -> !tipo.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
