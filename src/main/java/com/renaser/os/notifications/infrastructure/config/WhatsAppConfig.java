package com.renaser.os.notifications.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra {@link WhatsAppProperties} para el adaptador de YCloud y para la puerta del webhook (spec 041). */
@Configuration
@EnableConfigurationProperties(WhatsAppProperties.class)
class WhatsAppConfig {
}
