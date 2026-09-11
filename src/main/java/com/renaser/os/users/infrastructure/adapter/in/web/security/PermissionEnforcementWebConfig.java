package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra {@link PermissionEnforcementInterceptor} para toda la API (CLAUDE.MD §5.1: cierre
 * de A-1). Vive en `users` — no en {@code shared/web/WebMvcConfig} — porque el interceptor
 * necesita {@link UserSummaryFinder}, que es la API publica de ESTE modulo; ponerlo en
 * `shared` crearia una dependencia ciclica (`shared` -> `users` -> `shared`, ya que `users` ya
 * depende de `shared` para {@code UserId}/{@code Permission}), que
 * {@code ApplicationModules.verify()} rechaza. Registrar un segundo {@code WebMvcConfigurer}
 * es valido: Spring MVC combina los interceptores de todos los {@code WebMvcConfigurer} del
 * contexto, no exige uno solo.
 *
 * <p>El interceptor se instancia a mano (no {@code @Component}) para que no quede sujeto al
 * filtro de tipos de {@code @WebMvcTest} por su cuenta — igual lo alcanza por ser
 * {@code WebMvcConfigurer} (eso es necesario e intencional, ver el javadoc de
 * {@link PermissionEnforcementInterceptor}), pero de esta forma la unica superficie que
 * {@code @WebMvcTest} necesita resolver es esta clase, con una unica dependencia siempre
 * segura ({@link ObjectProvider}, nunca ausente ni obligatoria).
 */
@Configuration
class PermissionEnforcementWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<UserSummaryFinder> userSummaryFinderProvider;

    /**
     * Interruptor del paso 3 de DL-08 (SDD 002). Por defecto <b>false</b>: MENTOR_LEAD se evalua
     * en modo sombra. Se pone en true recien cuando el registro del modo sombra este limpio en
     * uso real, y volver atras es cambiar esta propiedad — no desplegar codigo.
     */
    private final boolean cumplimientoMentorLead;

    PermissionEnforcementWebConfig(ObjectProvider<UserSummaryFinder> userSummaryFinderProvider,
                                    @Value("${renaser.security.mentor-lead-enforcement:false}")
                                    boolean cumplimientoMentorLead) {
        this.userSummaryFinderProvider = userSummaryFinderProvider;
        this.cumplimientoMentorLead = cumplimientoMentorLead;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PermissionEnforcementInterceptor(userSummaryFinderProvider, cumplimientoMentorLead))
                .addPathPatterns("/api/v1/**");
    }
}
