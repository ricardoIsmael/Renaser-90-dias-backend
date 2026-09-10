package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.ApiErrorResponse;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * Cierra el hueco A-1: hace que {@link RequiresPermission} <b>se ejecute</b>, no solo que se
 * declare. Antes de este interceptor, {@code EndpointAuthorizationDeclarationTest} verificaba
 * que los 219 endpoints DIJERAN que permiso exigen, pero ningun filtro ni interceptor lo
 * hacia cumplir — cualquiera con cualquier rol podia llamar cualquier endpoint.
 *
 * <p><b>Alcance.</b> TRAINEE se verifica de verdad desde 2026-09-01. MENTOR_LEAD se verifica
 * desde 2026-09-09, pero <b>en modo sombra por defecto</b> (SDD 002, decision DL-08). MENTOR,
 * ADMIN y ALCHEMIST siguen sin verificarse —ni siquiera el chequeo de cuenta suspendida— y su
 * request sigue el mismo camino de siempre, resuelto por los guards de cada servicio. Ese
 * falla-abierto esta documentado en {@code UserRole.can(Permission)} y en
 * {@code docs/ENDPOINTS_FALTANTES.md} fila A-1: definir que puede hacer cada uno de esos 3
 * roles es una regla de negocio que el dueño del proyecto todavia no dicto (CLAUDE.MD §0.6).
 *
 * <p><b>Que es el modo sombra y por que existe.</b> Hasta hoy MENTOR_LEAD pasaba por aca sin
 * que se le mirara un solo permiso. Encender el cumplimiento de golpe convierte en 403 todo
 * endpoint que use y que falte en su matriz — incluidos los de su propio programa de 90 dias.
 * Por eso el paso intermedio: con {@code renaser.security.mentor-lead-enforcement=false} (el
 * valor por defecto) el interceptor <b>evalua y registra</b> lo que denegaria, y deja pasar.
 * Cuando el registro este limpio en uso real, se pone en {@code true} y el cumplimiento es
 * real. Volver atras es cambiar la propiedad, sin tocar la matriz ni desplegar codigo nuevo.
 *
 * <p><b>No reemplaza al guard del servicio, lo adelanta.</b> Los guards existentes
 * ({@code requireAdminActivo}, {@code requireActorPuedePublicar}, etc.) siguen ahi — son la
 * segunda linea de defensa si algun caso de uso se invoca desde otro lugar (un
 * {@code @Scheduled}, un listener de evento) que no pasa por este interceptor.
 *
 * <p><b>Por que {@link UserSummaryFinder} llega envuelto en {@link ObjectProvider}:</b> este
 * interceptor se registra via un {@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer},
 * y Spring Boot incluye automaticamente cualquier {@code WebMvcConfigurer} en TODOS los
 * {@code @WebMvcTest} del repo (es del mecanismo de esos tests, no algo que este modulo pida).
 * Un {@code @WebMvcTest} de un controller de OTRO modulo (ej. {@code TestimonioControllerTest})
 * no carga el servicio real de `users` que implementa {@code UserSummaryFinder} — solo mockea
 * los casos de uso del controller bajo prueba. Con una dependencia obligatoria, agregar este
 * interceptor global hubiera roto todos esos tests con {@code NoSuchBeanDefinitionException}
 * al arrancar el contexto. Con {@code ObjectProvider}, la ausencia del colaborador en esos
 * contextos reducidos hace que el interceptor deje pasar la request tal cual pasaba antes de
 * este cambio (ver {@link #preHandle}) — el contexto de produccion SI lo tiene, siempre.
 */
class PermissionEnforcementInterceptor implements HandlerInterceptor {

    private static final String HEADER_ACTOR_ID = "X-Actor-Id";

    private static final Logger log = LoggerFactory.getLogger(PermissionEnforcementInterceptor.class);

    private final ObjectProvider<UserSummaryFinder> userSummaryFinderProvider;

    /** false = modo sombra para MENTOR_LEAD (registra, no deniega). Ver el javadoc de la clase. */
    private final boolean cumplimientoMentorLead;

    PermissionEnforcementInterceptor(ObjectProvider<UserSummaryFinder> userSummaryFinderProvider,
                                      boolean cumplimientoMentorLead) {
        this.userSummaryFinderProvider = userSummaryFinderProvider;
        this.cumplimientoMentorLead = cumplimientoMentorLead;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod) || esPublico(handlerMethod)) {
            return true;
        }
        Permission requerido = permisoRequerido(handlerMethod);
        if (requerido == null) {
            // Sin @RequiresPermission ni @PublicEndpoint: los pocos handlers que
            // EndpointAuthorizationDeclarationTest todavia lista en HANDLERS_SIN_CLASIFICAR.
            // Este interceptor no decide por ellos — fase 4 los sigue teniendo pendientes.
            return true;
        }

        UserSummaryFinder userSummaryFinder = userSummaryFinderProvider.getIfAvailable();
        if (userSummaryFinder == null) {
            return true; // contexto reducido de un @WebMvcTest de otro modulo (ver javadoc de la clase)
        }

        Optional<UserId> actorId = resolverActorId(request);
        if (actorId.isEmpty()) {
            // Sin sesion y sin header: se deja que el binding normal del controller (casi
            // siempre un @RequestHeader("X-Actor-Id") obligatorio) produzca el 400 de siempre.
            return true;
        }

        Optional<UserSummary> actor = userSummaryFinder.findById(actorId.get());
        if (actor.isEmpty()) {
            // Actor inexistente: se deja que el guard/caso de uso de siempre lo resuelva (hoy,
            // tipicamente un 404 "Usuario no encontrado"). Este interceptor no reemplaza esa capa.
            return true;
        }

        UserSummary resumen = actor.get();
        if (resumen.role() == UserRole.MENTOR_LEAD) {
            return verificarMentorLead(request, response, resumen, requerido);
        }
        if (resumen.role() != UserRole.TRAINEE) {
            return true; // ver javadoc de la clase: falla-abierto deliberado para MENTOR/ADMIN/ALCHEMIST
        }

        if (resumen.status() == UserStatus.SUSPENDED && !requerido.toleraCuentaSuspendida()) {
            denegar(response, "Cuenta suspendida");
            return false;
        }
        if (!resumen.role().can(requerido)) {
            denegar(response, "No autorizado");
            return false;
        }
        return true;
    }

    /**
     * Misma evaluacion que para TRAINEE, pero el desenlace depende de
     * {@code renaser.security.mentor-lead-enforcement} (SDD 002, DL-08). En modo sombra se
     * registra <b>que endpoint y que permiso</b> habrian denegado — nunca quien es el usuario ni
     * su rol: es el mismo criterio del cuerpo del 403, que no nombra el permiso que falto porque
     * eso le sirve a quien esta sondeando el API. El id del actor no se loguea; con el metodo,
     * la ruta y el permiso alcanza para corregir la matriz.
     */
    private boolean verificarMentorLead(HttpServletRequest request, HttpServletResponse response,
                                         UserSummary resumen, Permission requerido) throws IOException {
        boolean suspendidoSinTolerancia =
                resumen.status() == UserStatus.SUSPENDED && !requerido.toleraCuentaSuspendida();
        String motivo = suspendidoSinTolerancia ? "Cuenta suspendida"
                : (resumen.role().can(requerido) ? null : "No autorizado");
        if (motivo == null) {
            return true;
        }
        if (!cumplimientoMentorLead) {
            log.warn("MENTOR_LEAD modo sombra: se habria denegado {} {} por {} (permiso {})",
                    request.getMethod(), request.getRequestURI(), motivo, requerido);
            return true;
        }
        denegar(response, motivo);
        return false;
    }

    private boolean esPublico(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(PublicEndpoint.class)
                || handlerMethod.getBeanType().isAnnotationPresent(PublicEndpoint.class);
    }

    private Permission permisoRequerido(HandlerMethod handlerMethod) {
        RequiresPermission enElMetodo = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (enElMetodo != null) {
            return enElMetodo.value();
        }
        RequiresPermission enLaClase = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        return enLaClase == null ? null : enLaClase.value();
    }

    /**
     * Misma precedencia que {@code ActorAutenticadoArgumentResolver} (sesion primero, header
     * {@code X-Actor-Id} como respaldo — CLAUDE.MD §5.3.5), pero sin lanzar: a diferencia de
     * ese resolver, que guarda un parametro obligatorio de un controller ya elegido, este
     * interceptor corre ANTES de saber si el endpoint en verdad necesita el actor. Ante
     * cualquier ambiguedad (sin sesion, sin header, o un header que no es un UUID valido)
     * devuelve {@code Optional.empty()} y deja pasar la request tal cual: el 400/404 que
     * corresponda lo sigue generando el binding del controller o el guard del servicio, igual
     * que antes de este cambio. Se mantiene separado de
     * {@code ActorAutenticadoArgumentResolver} a proposito, para no arriesgar el
     * comportamiento (y las pruebas) de esa clase ya en uso.
     */
    private Optional<UserId> resolverActorId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return actorIdSeguro(authentication.getName());
        }
        String header = request.getHeader(HEADER_ACTOR_ID);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        return actorIdSeguro(header);
    }

    private Optional<UserId> actorIdSeguro(String valor) {
        try {
            return Optional.of(UserId.of(valor));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Mismo formato que {@code GlobalExceptionHandler} ({@link ApiErrorResponse}), nunca
     * {@code ProblemDetail} (CLAUDE.MD §5.4.4/§8: el contrato que consume la app movil no
     * cambia). El mensaje NUNCA nombra el permiso que falto ni el rol del actor — es
     * informacion util para quien esta sondeando el API.
     *
     * <p>Serializa a mano en vez de pedir un {@code ObjectMapper} por inyeccion: este
     * interceptor lo alcanza cualquier {@code @WebMvcTest} del repo (ver el javadoc de la
     * clase), y varios de esos contextos reducidos no traen configurado el modulo
     * {@code JavaTimeModule} que {@code Instant} necesita para serializar — pedirlo hubiera
     * significado el mismo riesgo de romper tests ajenos que ya se evito con
     * {@link ObjectProvider} para {@link UserSummaryFinder}. El cuerpo es fijo y de dos campos:
     * no hace falta Jackson para escribirlo.
     */
    private void denegar(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse cuerpo = ApiErrorResponse.of(mensaje);
        String mensajeEscapado = cuerpo.message().replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write(
                "{\"message\":\"" + mensajeEscapado + "\",\"timestamp\":\"" + cuerpo.timestamp() + "\"}");
    }
}
