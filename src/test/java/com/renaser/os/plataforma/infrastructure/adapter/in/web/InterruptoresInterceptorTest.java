package com.renaser.os.plataforma.infrastructure.adapter.in.web;

import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.SinInterruptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code @Funcionalidad} se hace cumplir; {@code @SinInterruptor} pasa siempre (spec 002, FR-009, FR-004). */
@SuppressWarnings("unchecked")
class InterruptoresInterceptorTest {

    private final ObjectProvider<Interruptores> provider = mock(ObjectProvider.class);
    private final InterruptoresFalsos interruptores = new InterruptoresFalsos();
    private final InterruptoresInterceptor interceptor = new InterruptoresInterceptor(provider);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("una funcionalidad apagada para quien pide corta con FuncionalidadApagadaException (503 con el aviso)")
    void apagadaCorta() {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        interruptores.apagada = "chat.grupos";
        UUID actor = UUID.randomUUID();

        assertThatThrownBy(() -> preHandle(actor, ControladorDePrueba.class, "deClase"))
                .isInstanceOf(FuncionalidadApagadaException.class).hasMessage("Volvemos pronto");
        assertThat(interruptores.consultas).containsExactly("chat.grupos@" + actor);
    }

    @Test
    @DisplayName("prendida: deja pasar")
    void prendidaPasa() throws Exception {
        when(provider.getIfAvailable()).thenReturn(interruptores);

        assertThat(preHandle(UUID.randomUUID(), ControladorDePrueba.class, "deClase")).isTrue();
    }

    @Test
    @DisplayName("la anotación del método gana sobre la de la clase")
    void elMetodoGana() throws Exception {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        interruptores.apagada = "chat.grupos";

        assertThat(preHandle(UUID.randomUUID(), ControladorDePrueba.class, "propia")).isTrue();
        assertThat(interruptores.consultas).singleElement().asString().startsWith("chat.audio@");
    }

    @Test
    @DisplayName("@SinInterruptor pasa sin consultar nada, en el método o en la clase (anti-bloqueo)")
    void sinInterruptorPasa() throws Exception {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        interruptores.apagada = "chat.grupos";

        assertThat(preHandle(UUID.randomUUID(), ControladorDePrueba.class, "antiBloqueo")).isTrue();
        assertThat(preHandle(UUID.randomUUID(), ControladorSinInterruptor.class, "cualquiera")).isTrue();
        assertThat(interruptores.consultas).isEmpty();
    }

    /**
     * E-612: el chat de SER abría el stream con la funcionalidad prendida; si se apagaba en el medio (el circuito se
     * abría con esa misma falla), el despacho ASYNC que escribe el final volvía a pasar por el interceptor, lanzaba, y
     * la respuesta ya en {@code text/event-stream} no podía llevar el 503: la conexión se cortaba en el cliente con
     * «chunked transfer encoding, state: READING_LENGTH». Contra el código anterior esta prueba lanza.
     */
    @Test
    @DisplayName("E-612: el despacho ASYNC de un stream ya abierto no se vuelve a evaluar (un pedido en vuelo termina)")
    void elDespachoAsyncNoSeEvalua() throws Exception {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        interruptores.apagada = "chat.grupos";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
        ControladorDePrueba bean = new ControladorDePrueba();

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(),
                new HandlerMethod(bean, ControladorDePrueba.class.getDeclaredMethod("deClase")))).isTrue();
        assertThat(interruptores.consultas).isEmpty();
    }

    @Test
    @DisplayName("sin actor (endpoint público) se evalúa el estado global")
    void sinActorEsGlobal() {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        interruptores.apagada = "chat.grupos";

        assertThatThrownBy(() -> preHandle(null, ControladorDePrueba.class, "deClase"))
                .isInstanceOf(FuncionalidadApagadaException.class);
        assertThat(interruptores.consultas).containsExactly("chat.grupos@null");
    }

    @Test
    @DisplayName("el actor sale de la sesión antes que del header")
    void actorDeLaSesion() throws Exception {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        UUID deLaSesion = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(deLaSesion.toString(), null, List.of()));

        preHandle(UUID.randomUUID(), ControladorDePrueba.class, "deClase");

        assertThat(interruptores.consultas).containsExactly("chat.grupos@" + deLaSesion);
    }

    @Test
    @DisplayName("sin Interruptores en el contexto (otro @WebMvcTest) deja pasar")
    void sinInterruptoresPasa() throws Exception {
        assertThat(preHandle(UUID.randomUUID(), ControladorDePrueba.class, "deClase")).isTrue();
    }

    private boolean preHandle(UUID actor, Class<?> controlador, String metodo) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (actor != null) {
            request.addHeader("X-Actor-Id", actor.toString());
        }
        Object bean = controlador.getDeclaredConstructor().newInstance();
        return interceptor.preHandle(request, new MockHttpServletResponse(),
                new HandlerMethod(bean, controlador.getDeclaredMethod(metodo)));
    }

    @Funcionalidad("chat.grupos")
    static class ControladorDePrueba {

        public void deClase() {
        }

        @Funcionalidad("chat.audio")
        public void propia() {
        }

        @SinInterruptor(motivo = "prueba")
        public void antiBloqueo() {
        }
    }

    @SinInterruptor(motivo = "prueba")
    static class ControladorSinInterruptor {

        public void cualquiera() {
        }
    }

    /** Apaga una sola clave y anota cada consulta como «clave@actor». */
    static class InterruptoresFalsos implements Interruptores {

        String apagada;
        final List<String> consultas = new ArrayList<>();

        @Override
        public boolean prendida(String clave) {
            return !clave.equals(apagada);
        }

        @Override
        public boolean prendidaPara(String clave, UserId actor) {
            return prendida(clave);
        }

        @Override
        public void exigirPrendidaPara(String clave, UserId actor) {
            consultas.add(clave + "@" + actor);
            if (clave.equals(apagada)) {
                throw new FuncionalidadApagadaException(clave, "Volvemos pronto");
            }
        }
    }
}
