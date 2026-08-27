package com.renaser.os.shared.web.security;

import com.renaser.os.shared.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActorAutenticadoArgumentResolverTest {

    private final ActorAutenticadoArgumentResolver resolver = new ActorAutenticadoArgumentResolver();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void conSesionActivaResuelveDesdeElContextoSinMirarElHeader() {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id.toString(), null, List.of()));
        NativeWebRequest webRequest = mock(NativeWebRequest.class);

        UserId resuelto = (UserId) resolver.resolveArgument(mock(MethodParameter.class), null, webRequest, null);

        assertThat(resuelto).isEqualTo(UserId.of(id));
    }

    @Test
    void sinSesionCaeAlHeaderXActorId() {
        UUID id = UUID.randomUUID();
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader("X-Actor-Id")).thenReturn(id.toString());
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(servletRequest);

        UserId resuelto = (UserId) resolver.resolveArgument(mock(MethodParameter.class), null, webRequest, null);

        assertThat(resuelto).isEqualTo(UserId.of(id));
    }

    /**
     * Sin sesion, Spring Security instala un token anonimo que reporta isAuthenticated()==true.
     * Si el resolver lo tomara por valido devolveria la cadena "anonymousUser" y nunca caeria al
     * header — rompiendo todo controlador migrado en el caso mas comun. Encontrado probando la
     * app real: ningun test previo lo detecto porque todos inyectan un token autenticado de verdad.
     */
    @Test
    void unTokenAnonimoNoCuentaComoSesionYCaeAlHeader() {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("clave", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader("X-Actor-Id")).thenReturn(id.toString());
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(servletRequest);

        UserId resuelto = (UserId) resolver.resolveArgument(mock(MethodParameter.class), null, webRequest, null);

        assertThat(resuelto).isEqualTo(UserId.of(id));
    }

    @Test
    void sinSesionYSinHeaderFalla() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader("X-Actor-Id")).thenReturn(null);
        NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(servletRequest);

        assertThatThrownBy(() -> resolver.resolveArgument(mock(MethodParameter.class), null, webRequest, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void soportaSoloParametrosUserIdAnotadosConActorAutenticado() throws NoSuchMethodException {
        MethodParameter anotado = new MethodParameter(
                MetodosDePrueba.class.getDeclaredMethod("conAnotacion", UserId.class), 0);
        MethodParameter sinAnotar = new MethodParameter(
                MetodosDePrueba.class.getDeclaredMethod("sinAnotacion", UserId.class), 0);

        assertThat(resolver.supportsParameter(anotado)).isTrue();
        assertThat(resolver.supportsParameter(sinAnotar)).isFalse();
    }

    private static final class MetodosDePrueba {
        void conAnotacion(@ActorAutenticado UserId actorId) {
        }

        void sinAnotacion(UserId actorId) {
        }
    }
}
