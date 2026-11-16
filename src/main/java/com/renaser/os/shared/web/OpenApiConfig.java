package com.renaser.os.shared.web;

import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.RequiresPermission;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

/**
 * Documentacion de la API con springdoc-openapi (OpenAPI 3.1), CLAUDE.MD §8.
 *
 * <p>Se lee en {@code /swagger-ui.html} (navegable) y en {@code /v3/api-docs} (el JSON, para
 * regenerar tipos o pasarlo a otro equipo). Cubre todo lo que vive bajo {@code /api/v1/**}
 * ({@code springdoc.paths-to-match} en application.yaml): los controllers no necesitan ninguna
 * anotacion para aparecer. Lo que esta clase agrega encima, sin tocar ningun controller:
 *
 * <ul>
 *   <li><b>La sesion.</b> La API no usa {@code Authorization: Bearer}: el login responde la
 *       cabecera {@code X-Auth-Token} y el cliente la reenvia en cada pedido (ver
 *       {@link SecurityConfig#httpSessionIdResolver()}). Se declara como esquema {@code sesion}
 *       y se exige en toda operacion, salvo las marcadas {@link PublicEndpoint}.</li>
 *   <li><b>El actor no es un parametro.</b> {@link ActorAutenticado} sale de la sesion
 *       ({@code ActorAutenticadoArgumentResolver}); sin esto springdoc lo dibujaria como un
 *       parametro que el cliente tiene que mandar.</li>
 *   <li><b>El permiso.</b> Si la operacion (o su controller) declara {@link RequiresPermission},
 *       se escribe en la descripcion y en la extension {@code x-permiso}.</li>
 *   <li><b>Agrupado por modulo.</b> Cada operacion lleva la etiqueta
 *       {@code <modulo> · <controller>} (p. ej. {@code habits · HabitTrack}), asi Swagger UI
 *       ordena los ~170 controllers por modulo en vez de mezclarlos.</li>
 * </ul>
 *
 * <p><b>Cuando se publica.</b> Las rutas de springdoc estan fuera de {@code /api/v1/**}, asi que
 * {@link SecurityConfig} no las protege: publicarlas es publicar el mapa entero de la API. Por eso
 * nace encendida en local y APAGADA en prod ({@code OPENAPI_HABILITADO}, application-prod.yaml).
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad en el documento; lo referencian todas las operaciones. */
    static final String ESQUEMA_SESION = "sesion";

    static final String CABECERA_SESION = "X-Auth-Token";

    private static final String PAQUETE_RAIZ = "com.renaser.os.";

    static {
        // El actor sale de la sesion: no es algo que el cliente mande.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(ActorAutenticado.class);
    }

    @Bean
    OpenAPI renaserOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Renaser 90 días — API")
                        .version("v1")
                        .contact(new Contact().name("Renaser").email("renaserlab@gmail.com"))
                        .description("""
                                API del backend de Renaser 90 días (app móvil y web).

                                **Sesión.** `POST /api/v1/auth/login` responde la cabecera `X-Auth-Token`. \
                                Ese valor se manda en la cabecera `X-Auth-Token` de cada pedido \
                                (botón *Authorize* de esta página). No es un JWT: es el id de una sesión \
                                guardada en el servidor; `POST /api/v1/auth/logout` la cierra.

                                **Permisos.** Que una cuenta pueda llamar a una ruta lo decide el servidor \
                                por rol (`x-permiso` en cada operación). Sin sesión: 401; sin permiso: 403.

                                **Errores.** El cuerpo de error trae `message` y, cuando aplica, `codigo` \
                                (p. ej. `FUNCIONALIDAD_APAGADA` con 503, `MODO_SOLO_LECTURA` con 403)."""))
                .components(new Components().addSecuritySchemes(ESQUEMA_SESION, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(CABECERA_SESION)
                        .description("Id de sesión que devuelve el login en la cabecera X-Auth-Token.")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_SESION));
    }

    @Bean
    OperationCustomizer etiquetaPermisoYSesion() {
        return (operation, handlerMethod) -> {
            etiquetarPorModulo(operation, handlerMethod);
            marcarPublico(operation, handlerMethod);
            anotarPermiso(operation, handlerMethod);
            return operation;
        };
    }

    static void etiquetarPorModulo(Operation operation, HandlerMethod handlerMethod) {
        Class<?> controller = handlerMethod.getBeanType();
        operation.setTags(List.of(moduloDe(controller) + " · " + nombreCorto(controller)));
    }

    /** {@code com.renaser.os.habits.infrastructure...} → {@code habits}. */
    static String moduloDe(Class<?> controller) {
        String paquete = controller.getPackageName();
        if (!paquete.startsWith(PAQUETE_RAIZ)) return "otros";
        String resto = paquete.substring(PAQUETE_RAIZ.length());
        int punto = resto.indexOf('.');
        return punto < 0 ? resto : resto.substring(0, punto);
    }

    /** {@code HabitTrackController} → {@code HabitTrack}. */
    static String nombreCorto(Class<?> controller) {
        String nombre = controller.getSimpleName();
        return nombre.endsWith("Controller") && nombre.length() > "Controller".length()
                ? nombre.substring(0, nombre.length() - "Controller".length())
                : nombre;
    }

    static void marcarPublico(Operation operation, HandlerMethod handlerMethod) {
        PublicEndpoint publico = anotacion(handlerMethod, PublicEndpoint.class);
        if (publico == null) return;
        // Lista vacia = esta operacion no exige ningun esquema (pisa el global).
        operation.setSecurity(List.of());
        agregarALaDescripcion(operation, "**Pública** (sin sesión): " + publico.value());
    }

    static void anotarPermiso(Operation operation, HandlerMethod handlerMethod) {
        RequiresPermission permiso = anotacion(handlerMethod, RequiresPermission.class);
        if (permiso == null) return;
        String nombre = permiso.value().name();
        operation.addExtension("x-permiso", nombre);
        String alcance = permiso.scope().isBlank() ? "" : " (alcance: " + permiso.scope() + ")";
        agregarALaDescripcion(operation, "**Permiso:** `" + nombre + "`" + alcance);
    }

    /** La del metodo gana; si no tiene, la del controller. */
    private static <A extends java.lang.annotation.Annotation> A anotacion(HandlerMethod handlerMethod, Class<A> tipo) {
        A delMetodo = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), tipo);
        return delMetodo != null ? delMetodo : AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), tipo);
    }

    private static void agregarALaDescripcion(Operation operation, String linea) {
        String actual = operation.getDescription();
        operation.setDescription(actual == null || actual.isBlank() ? linea : actual + "\n\n" + linea);
    }
}
