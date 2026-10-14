package com.renaser.os.shared.web.security;

import com.renaser.os.shared.domain.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra {@link FiltroDeSoloLectura} como instancia (E-307: un filtro que es bean de un módulo lo envuelve el proxy de
 * {@code spring-modulith-observability} y revienta en {@code init()}).
 *
 * <p><b>Orden −90</b>: después de la cadena de Spring Security ({@code SecurityProperties.DEFAULT_FILTER_ORDER} = −100),
 * que ya cargó la sesión de Redis y escribió las cabeceras de CORS, y antes de Spring MVC. El filtro de Spring Session
 * va mucho antes ({@code Integer.MIN_VALUE + 50}).
 */
@Configuration(proxyBeanMethods = false)
class FiltroDeSoloLecturaConfig {

    static final int ORDEN = -90;

    @Bean
    FilterRegistrationBean<FiltroDeSoloLectura> filtroDeSoloLectura(Clock clock) {
        FilterRegistrationBean<FiltroDeSoloLectura> registro = new FilterRegistrationBean<>(new FiltroDeSoloLectura(clock));
        registro.setOrder(ORDEN);
        return registro;
    }
}
