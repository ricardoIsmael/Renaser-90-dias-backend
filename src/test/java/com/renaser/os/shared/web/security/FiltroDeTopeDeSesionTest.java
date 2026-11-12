package com.renaser.os.shared.web.security;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.TopeDeSesion;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El tope absoluto de la sesión en el servidor (spec 050, tramo E; FR-028 a FR-031). Sin Spring. */
class FiltroDeTopeDeSesionTest {

    /** 02:30 UTC: en Lima todavía es el día anterior (FR-031). */
    private static final Instant AHORA = Instant.parse("2026-11-20T02:30:00Z");
    private static final TopeDeSesion NOVENTA_DIAS = TopeDeSesion.de(Duration.ofDays(90));

    private final FiltroDeTopeDeSesion filtro = new FiltroDeTopeDeSesion(FixedClock.at(AHORA), NOVENTA_DIAS);

    @AfterEach
    void limpiarElContexto() {
        SecurityContextHolder.clearContext();
    }

    /** Una sesión que dice haberse creado en ese instante (el {@code MockHttpSession} siempre dice «ahora mismo»). */
    private static final class SesionCreadaEn extends MockHttpSession {
        private final long creada;

        SesionCreadaEn(Instant creada) {
            this.creada = creada.toEpochMilli();
        }

        @Override
        public long getCreationTime() {
            return creada;
        }
    }

    private static MockHttpServletRequest pedido(String metodo, String ruta, MockHttpSession sesion) {
        MockHttpServletRequest pedido = new MockHttpServletRequest(metodo, ruta);
        if (sesion != null) {
            pedido.setSession(sesion);
        }
        return pedido;
    }

    private record Resultado(MockHttpServletResponse respuesta, HttpServletRequest pasoAlSiguiente) {
    }

    private static Resultado filtrar(FiltroDeTopeDeSesion filtro, MockHttpServletRequest pedido) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(pedido, respuesta, cadena);
        return new Resultado(respuesta, (HttpServletRequest) cadena.getRequest());
    }

    private Resultado filtrar(MockHttpServletRequest pedido) throws Exception {
        return filtrar(filtro, pedido);
    }

    // spec 050 FR-028, FR-031
    @Test
    @DisplayName("una sesión de 91 días, aunque se usó ayer, es 401 SESION_VENCIDA, no llega al controller y se invalida")
    void pasadaDelTopeEs401() throws Exception {
        MockHttpSession sesion = new SesionCreadaEn(AHORA.minus(Duration.ofDays(91)));
        sesion.setAttribute("usadaAyer", true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("la-persona", null, List.of()));

        Resultado resultado = filtrar(pedido("GET", "/api/v1/home", sesion));

        assertThat(resultado.pasoAlSiguiente()).as("no llega al controller").isNull();
        assertThat(resultado.respuesta().getStatus()).isEqualTo(401);
        assertThat(resultado.respuesta().getContentType()).startsWith("application/json");
        assertThat(resultado.respuesta().getContentAsString())
                .contains("\"codigo\":\"SESION_VENCIDA\"")
                .contains("\"message\":\"Tu sesión venció. Vuelve a iniciar sesión.\"")
                .contains("\"timestamp\":\"" + AHORA + "\"");
        assertThat(sesion.isInvalid()).as("la sesión queda invalidada").isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).as("y no deja identidad en el hilo").isNull();
    }

    // spec 050 FR-028
    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "GET"})
    @DisplayName("con cualquier verbo, la sesión pasada del tope es 401")
    void concualquierVerbo(String metodo) throws Exception {
        Resultado resultado = filtrar(pedido(metodo, "/api/v1/habit-tracks/abc/complete",
                new SesionCreadaEn(AHORA.minus(Duration.ofDays(120)))));

        assertThat(resultado.pasoAlSiguiente()).isNull();
        assertThat(resultado.respuesta().getStatus()).isEqualTo(401);
    }

    // spec 050 FR-029
    @Test
    @DisplayName("una sesión de 20 días sigue vigente y el pedido pasa tal cual")
    void deVeinteDiasPasa() throws Exception {
        MockHttpSession sesion = new SesionCreadaEn(AHORA.minus(Duration.ofDays(20)));

        Resultado resultado = filtrar(pedido("GET", "/api/v1/home", sesion));

        assertThat(resultado.pasoAlSiguiente()).isNotNull();
        assertThat(resultado.respuesta().getStatus()).isEqualTo(200);
        assertThat(sesion.isInvalid()).isFalse();
    }

    // spec 050 FR-028
    @Test
    @DisplayName("justo en el tope pasa; un segundo después ya no")
    void exactitudDelTope() throws Exception {
        assertThat(filtrar(pedido("GET", "/api/v1/home",
                new SesionCreadaEn(AHORA.minus(Duration.ofDays(90))))).pasoAlSiguiente()).isNotNull();
        assertThat(filtrar(pedido("GET", "/api/v1/home",
                new SesionCreadaEn(AHORA.minus(Duration.ofDays(90)).minusSeconds(1)))).respuesta().getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("un pedido sin sesión (el login, la salud) pasa sin tocar nada")
    void sinSesionPasa() throws Exception {
        Resultado resultado = filtrar(pedido("POST", "/api/v1/auth/login", null));

        assertThat(resultado.pasoAlSiguiente()).isNotNull();
        assertThat(resultado.respuesta().getStatus()).isEqualTo(200);
    }

    // spec 050 FR-028
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/social", "/api/v1/auth/password/request",
            "/api/v1/auth/email-verification/confirm"})
    @DisplayName("las rutas públicas pasan aunque traigan una sesión vencida (para poder volver a entrar), y la sesión "
            + "se invalida igual")
    void lasPublicasPasan(String ruta) throws Exception {
        MockHttpSession sesion = new SesionCreadaEn(AHORA.minus(Duration.ofDays(200)));

        Resultado resultado = filtrar(pedido("POST", ruta, sesion));

        assertThat(resultado.pasoAlSiguiente()).as("sigue hacia el controller").isNotNull();
        assertThat(resultado.respuesta().getStatus()).isEqualTo(200);
        assertThat(sesion.isInvalid()).as("la sesión vieja ya no sirve").isTrue();
    }

    @Test
    @DisplayName("la ruta pública se mira con su verbo: GET /account-requests (la bandeja del ADMIN) NO es pública")
    void laBandejaDelAdminNoEsPublica() throws Exception {
        Resultado resultado = filtrar(pedido("GET", "/api/v1/account-requests",
                new SesionCreadaEn(AHORA.minus(Duration.ofDays(200)))));

        assertThat(resultado.pasoAlSiguiente()).isNull();
        assertThat(resultado.respuesta().getStatus()).isEqualTo(401);
    }

    // spec 050 FR-028
    @Test
    @DisplayName("sin tope (propiedad vacía o en cero) ninguna sesión vence, ni la de mil días: la vuelta atrás")
    void sinTopeNoHaceNada() throws Exception {
        FiltroDeTopeDeSesion apagado = new FiltroDeTopeDeSesion(FixedClock.at(AHORA), TopeDeSesion.sinTope());
        MockHttpSession sesion = new SesionCreadaEn(AHORA.minus(Duration.ofDays(1000)));

        Resultado resultado = filtrar(apagado, pedido("GET", "/api/v1/home", sesion));

        assertThat(resultado.pasoAlSiguiente()).isNotNull();
        assertThat(sesion.isInvalid()).isFalse();
    }

    // spec 050 FR-031
    @ParameterizedTest
    @ValueSource(strings = {"2026-11-20T00:00:00Z", "2026-11-20T02:59:59Z", "2026-11-20T04:59:59Z"})
    @DisplayName("con el reloj entre las 00:00 y las 05:00 UTC la cuenta es la misma: el tope no mira el día de Lima")
    void conElRelojDeLaMadrugada(String ahoraTexto) throws Exception {
        Instant ahora = Instant.parse(ahoraTexto);
        FiltroDeTopeDeSesion enLaMadrugada = new FiltroDeTopeDeSesion(FixedClock.at(ahora), NOVENTA_DIAS);

        assertThat(filtrar(enLaMadrugada, pedido("GET", "/api/v1/home",
                new SesionCreadaEn(ahora.minus(Duration.ofDays(90)).plusSeconds(60)))).pasoAlSiguiente()).isNotNull();
        assertThat(filtrar(enLaMadrugada, pedido("GET", "/api/v1/home",
                new SesionCreadaEn(ahora.minus(Duration.ofDays(90)).minusSeconds(60)))).respuesta().getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("la propiedad se lee como duración con la conversión de Spring Boot: 90d, 2160h, P90D; vacía o 0 es sin "
            + "tope; un valor que no es una duración impide arrancar")
    void laPropiedadSeLee() {
        ConversionService conversion = ApplicationConversionService.getSharedInstance();

        assertThat(conversion.convert("90d", Duration.class)).isEqualTo(Duration.ofDays(90));
        assertThat(conversion.convert("2160h", Duration.class)).isEqualTo(Duration.ofDays(90));
        assertThat(conversion.convert("P90D", Duration.class)).isEqualTo(Duration.ofDays(90));
        assertThat(TopeDeSesion.de(conversion.convert("", Duration.class)).activo()).as("vacía").isFalse();
        assertThat(TopeDeSesion.de(conversion.convert("0", Duration.class)).activo()).as("0").isFalse();
        assertThat(TopeDeSesion.de(conversion.convert("0d", Duration.class)).activo()).as("0d").isFalse();
        assertThatThrownBy(() -> conversion.convert("noventa dias", Duration.class))
                .isInstanceOf(ConversionFailedException.class);
        assertThat(FiltroDeTopeDeSesion.PROPIEDAD).as("el valor de partida son 90 días").endsWith(":90d}");
    }

    @Test
    @DisplayName("corre después de Spring Security (−100) y antes del solo lectura (−90)")
    void suOrden() {
        assertThat(FiltroDeTopeDeSesionConfig.ORDEN).isGreaterThan(-100).isLessThan(FiltroDeSoloLecturaConfig.ORDEN);
    }
}
