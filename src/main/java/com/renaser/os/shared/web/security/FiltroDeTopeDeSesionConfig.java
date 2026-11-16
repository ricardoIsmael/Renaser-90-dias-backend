package com.renaser.os.shared.web.security;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.TopeDeSesion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Registra {@link FiltroDeTopeDeSesion} como instancia (E-307: un filtro que es bean de un módulo lo envuelve el proxy de
 * {@code spring-modulith-observability} y revienta en {@code init()}) y le arma el {@link TopeDeSesion} desde la propiedad
 * {@code renaser.sesion.tope-absoluto}. El tope NO es un bean, por lo mismo: el proxy no puede con una clase {@code final}
 * y no hay motivo para que cada {@code vencida()} pase por una observación.
 *
 * <p><b>Orden −92</b>: después de la cadena de Spring Security ({@code SecurityProperties.DEFAULT_FILTER_ORDER} = −100),
 * que ya cargó la sesión de Redis y escribió las cabeceras de CORS, y antes de {@link FiltroDeSoloLecturaConfig#ORDEN}
 * (−90) y de Spring MVC. El filtro de Spring Session va mucho antes ({@code Integer.MIN_VALUE + 50}).
 *
 * <p><b>La propiedad</b> ({@link FiltroDeTopeDeSesion#PROPIEDAD}): {@code 90d} de partida, leída como {@link Duration} con
 * la conversión de Spring Boot ({@code 90d}, {@code 2160h}, {@code P90D}). <b>Vacía o {@code 0} = sin tope</b>: es la
 * vuelta atrás, sin código (se cambia el valor y se reinicia). Un valor que no es una duración ({@code 90 dias}), o uno
 * sin unidad ({@code 90}, que Spring lee como 90 milisegundos y cortaría a todos), hace que el backend no arranque:
 * mejor eso que un tope apagado en silencio o una sesión que muere al primer pedido.
 */
@Configuration(proxyBeanMethods = false)
class FiltroDeTopeDeSesionConfig {

    static final int ORDEN = -92;

    @Bean
    FilterRegistrationBean<FiltroDeTopeDeSesion> filtroDeTopeDeSesion(Clock clock,
            @Value(FiltroDeTopeDeSesion.PROPIEDAD) Duration tope) {
        FilterRegistrationBean<FiltroDeTopeDeSesion> registro = new FilterRegistrationBean<>(
                new FiltroDeTopeDeSesion(clock, TopeDeSesion.de(tope)));
        registro.setOrder(ORDEN);
        return registro;
    }
}
