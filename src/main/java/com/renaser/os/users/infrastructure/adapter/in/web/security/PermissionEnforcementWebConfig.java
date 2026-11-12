package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.out.matriz.AnotarDenegacionDeLaMatrizPort;
import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatrizFijadoPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

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

    /**
     * El mismo interruptor para MENTOR y TRAINEE_GUIDE (spec 050, tramo H, FR-041; D-349) y su registro. Por defecto
     * <b>false</b>: modo sombra. Lo que el dueño fije en Redis con {@code scripts/despliegue/permisos-modo.sh} manda sobre
     * este valor y se aplica sin reiniciar.
     */
    private final ControlDeLaMatrizDeMentor controlDeMentor;

    /**
     * Las propiedades se leen del {@link Environment} y no con {@code @Value}: son tres y el constructor ya recibe tres
     * proveedores. {@code mentor-enforcement-releer-cada} (30 s) es cada cuánto se mira Redis; las pruebas de integración lo
     * ponen en cero para cambiar el modo entre dos pedidos.
     */
    PermissionEnforcementWebConfig(ObjectProvider<UserSummaryFinder> userSummaryFinderProvider,
                                    ObjectProvider<ModoDeLaMatrizFijadoPort> modoFijado,
                                    ObjectProvider<AnotarDenegacionDeLaMatrizPort> anotador,
                                    Environment entorno) {
        this.userSummaryFinderProvider = userSummaryFinderProvider;
        this.cumplimientoMentorLead = entorno.getProperty("renaser.security.mentor-lead-enforcement", Boolean.class, false);
        this.controlDeMentor = new ControlDeLaMatrizDeMentor(
                entorno.getProperty("renaser.security.mentor-enforcement", Boolean.class, false), modoFijado, anotador,
                entorno.getProperty("renaser.security.mentor-enforcement-releer-cada", Duration.class,
                        ControlDeLaMatrizDeMentor.CADA_LECTURA));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PermissionEnforcementInterceptor(userSummaryFinderProvider, cumplimientoMentorLead,
                        controlDeMentor))
                .addPathPatterns("/api/v1/**");
    }
}
