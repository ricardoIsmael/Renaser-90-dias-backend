package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.renaser.os.shared.domain.SesionNoIniciadaException;
import com.renaser.os.shared.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Unico lugar del sistema que toca {@code SecurityContextHolder}/{@code HttpSession}
 * directamente. Existe para que {@code AutenticacionController} se quede exactamente en la
 * forma que exige CLAUDE.MD §5.4.6 (deserializar, invocar UN caso de uso, mapear salida) sin
 * lógica de transporte metida inline en la clase del controller. Esto NO es un caso de uso: no
 * decide ninguna regla de negocio, no importa un puerto {@code out}, no toca el dominio — es
 * transporte puro, la misma categoria que la configuracion de CORS/CSRF de
 * {@code SecurityConfig}.
 */
@Component
public class SesionWebAdapter {

    private final SecurityContextRepository securityContextRepository;

    public SesionWebAdapter(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void establecer(UserId actorId, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(actorId.value().toString(), null,
                List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /** Invalidar la {@code HttpSession} borra la fila en Redis (Spring Session la administra). */
    public void cerrar(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * El descarte de {@link AnonymousAuthenticationToken} no es defensivo de mas: cuando NO hay
     * sesion, Spring Security instala un token anonimo cuyo {@code isAuthenticated()} devuelve
     * <b>true</b> y cuyo nombre es la cadena "anonymousUser". Sin esta condicion, ese texto
     * llegaba a {@code UserId.of(...)} y salia un 400 ("no es un UUID valido") en vez del 401
     * que corresponde — con el detalle de que el mensaje de error delataba el mecanismo interno.
     */
    public UserId actorActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new SesionNoIniciadaException();
        }
        return UserId.of(authentication.getName());
    }
}
