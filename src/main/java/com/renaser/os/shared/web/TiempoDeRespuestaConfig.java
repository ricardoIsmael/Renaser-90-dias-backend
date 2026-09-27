package com.renaser.os.shared.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra {@link TiempoDeRespuestaFilter} como instancia propia y no como bean del modulo (E-307):
 * {@code spring-modulith-observability} envuelve en un proxy CGLIB los beans de cada modulo, y un
 * {@code GenericFilterBean} proxiado revienta en {@code init()} con {@code logger} null, tumbando
 * el arranque de Tomcat. {@code FilterRegistrationBean} es una clase de Spring, fuera de los modulos.
 */
@Configuration(proxyBeanMethods = false)
class TiempoDeRespuestaConfig {

    @Bean
    FilterRegistrationBean<TiempoDeRespuestaFilter> tiempoDeRespuestaFilter() {
        FilterRegistrationBean<TiempoDeRespuestaFilter> registro =
                new FilterRegistrationBean<>(new TiempoDeRespuestaFilter());
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}
