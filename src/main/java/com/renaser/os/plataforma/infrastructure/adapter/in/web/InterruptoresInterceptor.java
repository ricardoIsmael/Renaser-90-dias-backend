package com.renaser.os.plataforma.infrastructure.adapter.in.web;

import com.renaser.os.plataforma.application.ports.in.MedirPedidoEnDespliegueUseCase;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.FuncionalidadNoDisponibleException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.SesionDeSoloLectura;
import com.renaser.os.shared.web.security.SinInterruptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p><b>Métricas del despliegue progresivo</b> (fase 2, contrato §3.3): al terminar cada pedido con funcionalidad y con
 * persona, {@link #afterCompletion} lo cuenta (5xx = error) para {@link MedirPedidoEnDespliegueUseCase}, que no hace
 * nada si no hay despliegues en curso. Spring NO llama a {@code afterCompletion} de un interceptor cuyo
 * {@code preHandle} lanzó, así que el 404 de quien está fuera del despliegue se cuenta al cortarlo. Un 503 por un
 * interruptor apagado no se cuenta: es mantenimiento, no uso ni falla. Contar nunca rompe el pedido. Tampoco se
 * cuentan los pedidos de una sesión «ver como» (D-273): los marca {@code FiltroDeSoloLectura}.
 */
class InterruptoresInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InterruptoresInterceptor.class);
    private static final String HEADER_ACTOR_ID = "X-Actor-Id";
    /** El pedido a contar al terminar: se guarda en el despacho REQUEST y se lee también al cerrar un stream ASYNC. */
    static final String PEDIDO_A_MEDIR = InterruptoresInterceptor.class.getName() + ".pedidoAMedir";

    private final ObjectProvider<Interruptores> interruptoresProvider;
    private final ObjectProvider<MedirPedidoEnDespliegueUseCase> medidorProvider;

    InterruptoresInterceptor(ObjectProvider<Interruptores> interruptoresProvider,
                             ObjectProvider<MedirPedidoEnDespliegueUseCase> medidorProvider) {
        this.interruptoresProvider = interruptoresProvider;
        this.medidorProvider = medidorProvider;
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
        Funcionalidad funcionalidad = funcionalidadDeclarada(handlerMethod);
        if (funcionalidad == null) {
            return true;
        }
        UserId actor = actorDelPedido(request);
        // «Ver como» (D-273): los pedidos de una sesión de solo lectura (la cuenta de prueba que mira Soporte TI) no
        // cuentan en las métricas del despliegue: no son uso real. Un pedido sin persona tampoco se cuenta.
        PedidoAMedir pedido = new PedidoAMedir(funcionalidad.value(),
                SesionDeSoloLectura.vieneDeUna(request) ? null : actor);
        if (pedido.actor() != null) {
            request.setAttribute(PEDIDO_A_MEDIR, pedido);
        }
        Interruptores interruptores = interruptoresProvider.getIfAvailable();
        if (funcionalidad.laResuelveElCasoDeUso() || interruptores == null) {
            return true;
        }
        try {
            interruptores.exigirPrendidaPara(funcionalidad.value(), actor);
        } catch (FuncionalidadNoDisponibleException fuera) {
            request.removeAttribute(PEDIDO_A_MEDIR);
            medir(pedido, false);
            throw fuera;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        if (request.getAttribute(PEDIDO_A_MEDIR) instanceof PedidoAMedir pedido) {
            request.removeAttribute(PEDIDO_A_MEDIR);
            medir(pedido, ex != null || response.getStatus() >= 500);
        }
    }

    private void medir(PedidoAMedir pedido, boolean error) {
        if (pedido.actor() == null) {
            return;
        }
        MedirPedidoEnDespliegueUseCase medidor = medidorProvider.getIfAvailable();
        if (medidor == null) {
            return;
        }
        try {
            medidor.registrar(pedido.clave(), pedido.actor(), error);
        } catch (RuntimeException e) {
            log.debug("[plataforma] no se pudo contar el pedido a {} para el despliegue", pedido.clave(), e);
        }
    }

    /** La clave que declara el handler y corta el interceptor, o {@code null} si no tiene interruptor. */
    static String funcionalidadDe(HandlerMethod handlerMethod) {
        Funcionalidad funcionalidad = funcionalidadDeclarada(handlerMethod);
        return funcionalidad == null ? null : laCortaElInterceptor(funcionalidad);
    }

    /**
     * La {@link Funcionalidad} que declara el handler (la del método gana sobre la de la clase), o {@code null} si es
     * {@link SinInterruptor} o no declara ninguna. Incluye las que resuelve el caso de uso: se cuentan igual.
     */
    static Funcionalidad funcionalidadDeclarada(HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(SinInterruptor.class)) {
            return null;
        }
        Funcionalidad enElMetodo = handlerMethod.getMethodAnnotation(Funcionalidad.class);
        if (enElMetodo != null) {
            return enElMetodo;
        }
        Class<?> clase = handlerMethod.getBeanType();
        if (clase.isAnnotationPresent(SinInterruptor.class)) {
            return null;
        }
        return clase.getAnnotation(Funcionalidad.class);
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

    /** @param actor {@code null} en un pedido sin persona (no se cuenta) */
    private record PedidoAMedir(String clave, UserId actor) {
    }
}
