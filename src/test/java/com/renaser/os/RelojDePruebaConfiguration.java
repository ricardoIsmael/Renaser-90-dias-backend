package com.renaser.os;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Registra UNA vez el {@link RelojDePrueba} en la configuración común de las pruebas de integración
 * ({@link TestcontainersConfiguration} la importa). Una prueba NO declara su propio
 * {@code @Primary Clock}: partiría el caché de contextos de Spring (E-649).
 */
@TestConfiguration(proxyBeanMethods = false)
public class RelojDePruebaConfiguration {

    @Bean
    @Primary
    RelojDePrueba relojDePrueba() {
        return new RelojDePrueba();
    }
}
