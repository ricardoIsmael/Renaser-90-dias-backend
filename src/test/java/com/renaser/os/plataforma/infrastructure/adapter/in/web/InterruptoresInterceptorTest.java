package com.renaser.os.plataforma.infrastructure.adapter.in.web;

import com.renaser.os.plataforma.application.ports.in.MedirPedidoEnDespliegueUseCase;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.FuncionalidadNoDisponibleException;
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
    private final ObjectProvider<MedirPedidoEnDespliegueUseCase> medidorProvider = mock(ObjectProvider.class);
    private final InterruptoresFalsos interruptores = new InterruptoresFalsos();
    private final InterruptoresInterceptor interceptor = new InterruptoresInterceptor(provider, medidorProvider);

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

    @Test
    @DisplayName("laResuelveElCasoDeUso (la voz del orbe, D-267): el interceptor no corta ni consulta; responde el caso de uso")
    void loResuelveElCasoDeUso() throws Exception {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        interruptores.apagada = "ia.voz";

        assertThat(preHandle(UUID.randomUUID(), ControladorDeLaVoz.class, "preparar")).isTrue();
        assertThat(preHandle(UUID.randomUUID(), ControladorDePrueba.class, "laResuelveElServicio")).isTrue();
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

    // ── Métricas del despliegue (fase 2, contrato §3.3) ─────────────────────────────

    @Test
    @DisplayName("fase 2: al terminar, cuenta el pedido con su clave y su persona; 5xx o excepción = error")
    void cuentaAlTerminar() throws Exception {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        when(medidorProvider.getIfAvailable()).thenReturn(medidor);
        UUID actor = UUID.randomUUID();

        terminar(actor, "deClase", 200, null);
        terminar(actor, "deClase", 503, null);
        terminar(actor, "propia", 200, new IllegalStateException("se cayó"));
        terminar(actor, "deClase", 404, null);

        assertThat(medidor.pedidos).containsExactly("chat.grupos@" + actor + ":ok", "chat.grupos@" + actor + ":error",
                "chat.audio@" + actor + ":error", "chat.grupos@" + actor + ":ok");
    }

    @Test
    @DisplayName("fase 2: el 404 de quien está fuera del despliegue se cuenta al cortarlo (Spring no llama a afterCompletion)")
    void fueraDelDespliegueSeCuentaAlCortar() {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        when(medidorProvider.getIfAvailable()).thenReturn(medidor);
        interruptores.noDisponible = "chat.grupos";
        UUID actor = UUID.randomUUID();

        assertThatThrownBy(() -> preHandle(actor, ControladorDePrueba.class, "deClase"))
                .isInstanceOf(FuncionalidadNoDisponibleException.class);
        assertThat(medidor.pedidos).containsExactly("chat.grupos@" + actor + ":ok");
    }

    @Test
    @DisplayName("fase 2: no se cuenta un 503 de mantenimiento, un pedido sin persona ni un endpoint sin interruptor")
    void loQueNoSeCuenta() throws Exception {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        when(medidorProvider.getIfAvailable()).thenReturn(medidor);
        interruptores.apagada = "chat.grupos";

        assertThatThrownBy(() -> preHandle(UUID.randomUUID(), ControladorDePrueba.class, "deClase"))
                .isInstanceOf(FuncionalidadApagadaException.class);
        terminar(null, "propia", 500, null);
        terminar(UUID.randomUUID(), "antiBloqueo", 500, null);

        assertThat(medidor.pedidos).isEmpty();
    }

    @Test
    @DisplayName("fase 2: lo que resuelve el caso de uso también se cuenta; un medidor que falla no rompe el pedido")
    void casoDeUsoYMedidorQueFalla() throws Exception {
        when(provider.getIfAvailable()).thenReturn(interruptores);
        when(medidorProvider.getIfAvailable()).thenReturn(medidor);
        UUID actor = UUID.randomUUID();

        terminar(actor, "laResuelveElServicio", 204, null);
        medidor.fallar = true;
        terminar(actor, "deClase", 200, null);

        assertThat(medidor.pedidos).containsExactly("ia.voz@" + actor + ":ok");
    }

    /** Un pedido entero: preHandle (si deja pasar) y afterCompletion con ese estado. */
    private void terminar(UUID actor, String metodo, int estado, Exception ex) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (actor != null) {
            request.addHeader("X-Actor-Id", actor.toString());
        }
        HandlerMethod handler = new HandlerMethod(new ControladorDePrueba(),
                ControladorDePrueba.class.getDeclaredMethod(metodo));
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, handler);
        response.setStatus(estado);
        interceptor.afterCompletion(request, response, handler, ex);
    }

    private final MedidorFalso medidor = new MedidorFalso();

    /** Anota cada pedido contado como «clave@actor:ok|error». */
    static class MedidorFalso implements MedirPedidoEnDespliegueUseCase {

        final List<String> pedidos = new ArrayList<>();
        boolean fallar;

        @Override
        public void registrar(String clave, UserId persona, boolean error) {
            if (fallar) {
                throw new IllegalStateException("se cayó el medidor");
            }
            pedidos.add(clave + "@" + persona + (error ? ":error" : ":ok"));
        }
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

        @Funcionalidad(value = "ia.voz", laResuelveElCasoDeUso = true)
        public void laResuelveElServicio() {
        }
    }

    @Funcionalidad(value = "ia.voz", laResuelveElCasoDeUso = true)
    static class ControladorDeLaVoz {

        public void preparar() {
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
        String noDisponible;
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
            if (clave.equals(noDisponible)) {
                throw new FuncionalidadNoDisponibleException(clave);
            }
            if (clave.equals(apagada)) {
                throw new FuncionalidadApagadaException(clave, "Volvemos pronto");
            }
        }
    }
}
