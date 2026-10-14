package com.renaser.os.rag.infrastructure.config;

import com.renaser.os.rag.domain.model.circuito.ReglasDelCircuito;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Los números del circuito de la IA (spec 002, FR-016), por propiedad {@code renaser.ia.circuito.*}. Lo que no se
 * configura toma el valor de la spec ({@link ReglasDelCircuito#POR_DEFECTO}: 5 fallas en 2 min; esperas de 2, 5 y
 * 15 min; 2 min para el pedido de prueba). Un valor inválido hace fallar el arranque con el motivo.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CircuitoDeLaIaConfig.Propiedades.class)
class CircuitoDeLaIaConfig {

    @Bean
    ReglasDelCircuito reglasDelCircuito(Propiedades propiedades) {
        ReglasDelCircuito defecto = ReglasDelCircuito.POR_DEFECTO;
        return new ReglasDelCircuito(
                propiedades.fallasParaAbrir() == null ? defecto.fallasParaAbrir() : propiedades.fallasParaAbrir(),
                propiedades.ventana() == null ? defecto.ventana() : propiedades.ventana(),
                propiedades.esperas() == null || propiedades.esperas().isEmpty() ? defecto.esperas()
                        : propiedades.esperas(),
                propiedades.plazoDeLaPrueba() == null ? defecto.plazoDeLaPrueba() : propiedades.plazoDeLaPrueba());
    }

    @ConfigurationProperties(prefix = "renaser.ia.circuito")
    record Propiedades(Integer fallasParaAbrir, Duration ventana, List<Duration> esperas, Duration plazoDeLaPrueba) {
    }
}
