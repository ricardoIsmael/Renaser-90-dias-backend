package com.renaser.os.calendar.infrastructure.adapter.out.google;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** El cliente HTTP con que Renaser habla con Google: cada llamada espera como mucho 5 segundos (spec 040, FR-006). */
@Configuration
class ClienteDeGoogleConfig {

    static final String NOMBRE = "clienteDeGoogle";
    static final Duration TIEMPO_MAXIMO_POR_LLAMADA = Duration.ofSeconds(5);

    @Bean
    @Qualifier(NOMBRE)
    RestClient clienteDeGoogle(RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(TIEMPO_MAXIMO_POR_LLAMADA).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(TIEMPO_MAXIMO_POR_LLAMADA);
        return builder.clone().requestFactory(fabrica).build();
    }
}
