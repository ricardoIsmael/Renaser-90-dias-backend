package com.renaser.os.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Mantiene {@code /actuator/health} en el puerto de la aplicacion (8080) despues de que todo
 * actuator se mudo al puerto de administracion (D-237, {@code actuator.yaml}).
 *
 * <p>El CD, el despliegue sin corte ({@code desplegar-backend.sh}, D-235) y los scripts locales
 * esperan {@code GET :8080/actuator/health -> {"status":"UP"}}. Con {@code management.server.port}
 * distinto, Boot 4.1 saca ese endpoint del 8080; lo unico que permite publicar health en el puerto
 * de la aplicacion es un grupo con {@code additional-path}, y solo con un segmento
 * ({@code server:/salud}). Este forward interno (no un redirect: el cliente ve un 200 o un 503 en la
 * misma URL) devuelve el endpoint a la ruta de siempre. No expone nada mas: el resto de
 * {@code /actuator/**} sigue sin existir en el 8080.
 */
@Configuration
public class SaludEnElPuertoPublicoConfig implements WebMvcConfigurer {

    /** El {@code additional-path} del grupo {@code publico} de {@code actuator.yaml}. */
    static final String RUTA_SALUD = "/salud";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/actuator/health").setViewName("forward:" + RUTA_SALUD);
    }
}
