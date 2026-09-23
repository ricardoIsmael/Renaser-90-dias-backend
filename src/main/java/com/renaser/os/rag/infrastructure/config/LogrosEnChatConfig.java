package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.rag.domain.model.logro.LogrosEnChat;
import com.renaser.os.rag.domain.model.logro.TipoLogro;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Convierte las propiedades {@code renaser.ia.acompanante.logros-en-chat*} en el valor de dominio
 * {@link LogrosEnChat}. Mismo molde que {@link AvisosEnChatConfig}: los defaults de ACA (propiedad
 * ausente) son los seguros — interruptor apagado y plantillas vacias. Los textos provisorios viven
 * solo en {@code application.yaml}.
 */
@Configuration
class LogrosEnChatConfig {

    @Bean
    LogrosEnChat logrosEnChat(@Value("${renaser.ia.acompanante.logros-en-chat:false}") boolean activo,
                              @Value("${renaser.ia.acompanante.logros-en-chat-tipos:}") String tipos,
                              @Value("${renaser.ia.acompanante.logros-en-chat-plantilla-racha-sin-celular:}")
                              String rachaSinCelular,
                              @Value("${renaser.ia.acompanante.logros-en-chat-plantilla-roca-completada:}")
                              String rocaCompletada) {
        return new LogrosEnChat(activo, tiposConocidos(tipos), Map.of(
                TipoLogro.RACHA_SIN_CELULAR, rachaSinCelular, TipoLogro.ROCA_COMPLETADA, rocaCompletada));
    }

    /** Separa por comas como {@link AvisosEnChatConfig#separarTipos}; un nombre desconocido se ignora. */
    static Set<TipoLogro> tiposConocidos(String tipos) {
        Set<String> nombres = AvisosEnChatConfig.separarTipos(tipos);
        return Arrays.stream(TipoLogro.values())
                .filter(tipo -> nombres.contains(tipo.name()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
