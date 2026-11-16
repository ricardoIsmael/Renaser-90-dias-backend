package com.renaser.os.shared.web.security;

import com.renaser.os.shared.application.PoliticaDeLimites;
import com.renaser.os.shared.domain.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Registra {@link FiltroDeLimitePorCuenta} como instancia (E-307: un filtro que es bean de un módulo lo envuelve el proxy de
 * {@code spring-modulith-observability} y revienta en {@code init()}), solo para {@code /api/v1/*}: el WebSocket, la salud y la
 * documentación quedan fuera (spec 050, FR-013).
 *
 * <p><b>Orden −85</b>: después de la cadena de Spring Security ({@code SecurityProperties.DEFAULT_FILTER_ORDER} = −100), que ya cargó
 * la sesión y escribió las cabeceras de CORS, y de {@code FiltroDeSoloLectura} (−90), y antes de Spring MVC (FR-021).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LimitesDeProteccion.class)
class FiltroDeLimitePorCuentaConfig {

    static final int ORDEN = -85;

    @Bean
    FilterRegistrationBean<FiltroDeLimitePorCuenta> filtroDeLimitePorCuenta(PoliticaDeLimites politica, Clock clock) {
        FilterRegistrationBean<FiltroDeLimitePorCuenta> registro = new FilterRegistrationBean<>(new FiltroDeLimitePorCuenta(politica, clock));
        registro.setUrlPatterns(List.of("/api/v1/*"));
        registro.setOrder(ORDEN);
        return registro;
    }
}
