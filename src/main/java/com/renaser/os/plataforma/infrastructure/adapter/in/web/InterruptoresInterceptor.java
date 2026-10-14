package com.renaser.os.plataforma.infrastructure.adapter.in.web;

import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.SinInterruptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Hace cumplir {@link Funcionalidad} en toda la API (spec 002, FR-009; plan.md D2): si la funcionalidad del handler
 * está apagada para quien pide, lanza {@code FuncionalidadApagadaException} y {@code GlobalExceptionHandler} responde
 * 503 con el aviso — antes de deserializar el cuerpo y de entrar al caso de uso, así que no se escribe nada (spec
 * US1, escenario 3).
 *
 * <p><b>Corre después de {@code PermissionEnforcementInterceptor}</b> (se registra con orden mayor): una cuenta
 * suspendida o sin permiso sigue recibiendo su 403 de siempre, y un aprendiz no se entera de que existe una
 * funcionalidad de Administración porque esté apagada.
 *
 * <p>La anotación del método gana sobre la de la clase; {@link SinInterruptor} deja pasar siempre (anti-bloqueo). Sin
 * actor resoluble (un endpoint público, como el alta) se evalúa el estado global: las excepciones por rol no le
 * aplican a quien todavía no tiene cuenta.
 *
 * <p>Un pedido en vuelo termina (spec 002): el despacho {@code ASYNC} de un stream ya abierto no se vuelve a evaluar
 * (E-612).
 *
 * <p>Recibe {@link Interruptores} por {@link ObjectProvider} por el mismo motivo que el interceptor de permisos: este
 * interceptor entra en todos los {@code @WebMvcTest} del repo, y en esos contextos reducidos no hay quien lo
 * implemente; ahí deja pasar el pedido como antes de existir.
 */
class InterruptoresInterceptor implements HandlerInterceptor {

    private static final String HEADER_ACTOR_ID = "X-Actor-Id";

    private final ObjectProvider<Interruptores> interruptoresProvider;

    InterruptoresInterceptor(ObjectProvider<Interruptores> interruptoresProvider) {
        this.interruptoresProvider = interruptoresProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod) || request.getDispatcherType() == DispatcherType.ASYNC) {
            // E-612: el despacho ASYNC de un stream ya abierto (el chat de SER por SSE) vuelve a pasar por aca solo para
            // escribir el resultado. Lo que habia que decidir se decidio al entrar el pedido: si la funcionalidad se
            // apago en el medio, cortar aca rompia la conexion (la respuesta ya estaba en text/event-stream) en vez de
            // dejar que termine con su evento de siempre (spec 002, «pedido en vuelo»).
            return true;
        }
        String clave = funcionalidadDe(handlerMethod);
        Interruptores interruptores = interruptoresProvider.getIfAvailable();
        if (clave == null || interruptores == null) {
            return true;
        }
        interruptores.exigirPrendidaPara(clave, actorDelPedido(request));
        return true;
    }

    /** La clave que declara el handler, o {@code null} si no tiene interruptor. */
    static String funcionalidadDe(HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(SinInterruptor.class)) {
            return null;
        }
        Funcionalidad enElMetodo = handlerMethod.getMethodAnnotation(Funcionalidad.class);
        if (enElMetodo != null) {
            return laCortaElInterceptor(enElMetodo);
        }
        Class<?> clase = handlerMethod.getBeanType();
        if (clase.isAnnotationPresent(SinInterruptor.class)) {
            return null;
        }
        Funcionalidad enLaClase = clase.getAnnotation(Funcionalidad.class);
        return enLaClase == null ? null : laCortaElInterceptor(enLaClase);
    }

    /** Con {@code laResuelveElCasoDeUso}, el caso de uso responde lo suyo (la voz del orbe: 204, D-267). */
    private static String laCortaElInterceptor(Funcionalidad funcionalidad) {
        return funcionalidad.laResuelveElCasoDeUso() ? null : funcionalidad.value();
    }

    /** Misma precedencia que {@code ActorAutenticadoArgumentResolver}: sesión primero, header de respaldo. */
    private static UserId actorDelPedido(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return idSeguro(authentication.getName());
        }
        return idSeguro(request.getHeader(HEADER_ACTOR_ID));
    }

    private static UserId idSeguro(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UserId.of(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
