package com.renaser.os.shared.web.security;

import com.renaser.os.shared.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resuelve {@code @ActorAutenticado UserId} contra la sesion (via
 * {@code SecurityContextHolder}, poblado por {@code SesionWebAdapter} en el login) y, si no hay
 * sesion, cae al header {@code X-Actor-Id} — el mecanismo que usan hoy los 54 controllers
 * existentes. La caida no esta restringida a un perfil todavia (docs/MODULO_AUTH.md §8
 * planeaba limitarla a {@code local}): mientras {@code SecurityConfig} siga en
 * {@code permitAll()} en TODOS los perfiles (nada exige sesion todavia), restringir el
 * respaldo por perfil no cambiaria nada real — esa restriccion recien tiene sentido cuando se
 * active {@code authenticated()}, que es un paso deliberado y posterior, no parte de esto.
 */
@Component
public class ActorAutenticadoArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_ACTOR_ID = "X-Actor-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ActorAutenticado.class)
                && parameter.getParameterType().equals(UserId.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        // El token anonimo que Spring Security instala cuando NO hay sesion reporta
        // isAuthenticated()==true y se llama "anonymousUser". Sin descartarlo, este metodo lo
        // tomaria por un actor valido, jamas caeria al header, y devolveria esa cadena a los 54
        // controladores al migrarlos — rompiendolos justamente en el caso mas comun (sin sesion).
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return UserId.of(authentication.getName());
        }
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String header = request == null ? null : request.getHeader(HEADER_ACTOR_ID);
        if (header == null || header.isBlank()) {
            ActorAutenticado anotacion = parameter.getParameterAnnotation(ActorAutenticado.class);
            if (anotacion != null && !anotacion.required()) {
                return null;
            }
            throw new IllegalArgumentException(
                    "No hay sesion activa ni header " + HEADER_ACTOR_ID + " — no se puede resolver el actor");
        }
        return UserId.of(header);
    }
}
