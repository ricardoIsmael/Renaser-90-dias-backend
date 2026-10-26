package com.renaser.os.plataforma.infrastructure.adapter.in.web;

import com.renaser.os.plataforma.application.ports.in.MedirPedidoEnDespliegueUseCase;
import com.renaser.os.shared.application.Interruptores;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra {@link InterruptoresInterceptor} sobre {@code /api/v1/**}, DESPUÉS de {@code PermissionEnforcementInterceptor}
 * (que se registra con el orden por defecto, 0). Spring MVC ordena los interceptores de todos los
 * {@code WebMvcConfigurer} por su {@code order}.
 */
@Configuration
class InterruptoresWebConfig implements WebMvcConfigurer {

    /** Mayor que el 0 del interceptor de permisos: corre después. */
    static final int ORDEN = 100;

    private final ObjectProvider<Interruptores> interruptoresProvider;
    private final ObjectProvider<MedirPedidoEnDespliegueUseCase> medidorProvider;

    InterruptoresWebConfig(ObjectProvider<Interruptores> interruptoresProvider,
                           ObjectProvider<MedirPedidoEnDespliegueUseCase> medidorProvider) {
        this.interruptoresProvider = interruptoresProvider;
        this.medidorProvider = medidorProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InterruptoresInterceptor(interruptoresProvider, medidorProvider))
                .addPathPatterns("/api/v1/**")
                .order(ORDEN);
    }
}
