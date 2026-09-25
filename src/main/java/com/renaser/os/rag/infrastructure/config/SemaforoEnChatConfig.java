package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.rag.domain.model.semaforo.ColorDeLaSemana;
import com.renaser.os.rag.domain.model.semaforo.SemaforoEnChat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Convierte las propiedades {@code renaser.ia.acompanante.semaforo-en-chat*} en el valor de dominio
 * {@link SemaforoEnChat}. Mismo molde que {@link LogrosEnChatConfig}: los defaults de ACÁ (propiedad
 * ausente) son los seguros — interruptor apagado y plantillas vacías. El interruptor encendido
 * (desde el 2026-09-25) y los textos viven solo en {@code application.yaml}.
 */
@Configuration
class SemaforoEnChatConfig {

    @Bean
    SemaforoEnChat semaforoEnChat(@Value("${renaser.ia.acompanante.semaforo-en-chat:false}") boolean activo,
                                  @Value("${renaser.ia.acompanante.semaforo-en-chat-plantilla-verde:}") String verde,
                                  @Value("${renaser.ia.acompanante.semaforo-en-chat-plantilla-amarillo:}")
                                  String amarillo,
                                  @Value("${renaser.ia.acompanante.semaforo-en-chat-plantilla-rojo:}") String rojo,
                                  @Value("${renaser.ia.acompanante.semaforo-en-chat-plantilla-sin-datos:}")
                                  String sinDatos) {
        return new SemaforoEnChat(activo, Map.of(ColorDeLaSemana.VERDE, verde, ColorDeLaSemana.AMARILLO, amarillo,
                ColorDeLaSemana.ROJO, rojo, ColorDeLaSemana.SIN_DATOS, sinDatos));
    }
}
