package com.renaser.os.shared.web.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.shared.application.PoliticaDeLimites;
import com.renaser.os.shared.application.ports.out.AnotarCuentaQueHabriaBloqueadoPort;
import com.renaser.os.shared.application.ports.out.RegistrarMetricaDeLimitesPort;
import com.renaser.os.shared.application.ports.out.TopesDeLimites;
import com.renaser.os.shared.domain.ContadorDeVentanas;
import com.renaser.os.shared.domain.ContadorDeVentanas.Resultado;
import com.renaser.os.shared.domain.ContadorDeVentanas.Uso;
import com.renaser.os.shared.domain.ContadorDeVentanas.Ventana;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ModoDeLimite;
import com.renaser.os.shared.domain.SujetoDeLimite;
import jakarta.servlet.GenericServlet;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El filtro del límite por cuenta, sin Spring ni Redis (spec 050, FR-013 a FR-017, FR-019 y FR-021): el 429 con su cuerpo y su
 * {@code Retry-After}, que {@code REGISTRAR} pasa, que lo exento no se cuenta, a quién se cuenta y que ninguna falla del límite
 * cambia una respuesta.
 */
class FiltroDeLimitePorCuentaTest {

    private static final Instant AHORA = Instant.parse("2026-11-12T03:00:00Z");
    private static final String CUENTA = "3f2a9c1e-7b4d-4e0a-9d5c-1a2b3c4d5e6f";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<String> sujetosContados = new ArrayList<>();
    private final List<GrupoDeLimite> gruposContados = new ArrayList<>();
    private ModoDeLimite modo = ModoDeLimite.APLICAR;
    private Resultado loQueDiceElContador = new Resultado(false, "1m", 42_000, List.of(new Uso("1m", 11, 10)));
    private FiltroDeLimitePorCuenta filtro;
    private ListAppender<ILoggingEvent> registro;
    private Level nivelAnterior;

    @BeforeEach
    void armar() {
        filtro = new FiltroDeLimitePorCuenta(politicaConContador(), FixedClock.at(AHORA));
        Logger logger = (Logger) LoggerFactory.getLogger(FiltroDeLimitePorCuenta.class);
        nivelAnterior = logger.getLevel();
        logger.setLevel(Level.INFO);
        registro = new ListAppender<>();
        registro.start();
        logger.addAppender(registro);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        Logger logger = (Logger) LoggerFactory.getLogger(FiltroDeLimitePorCuenta.class);
        logger.detachAppender(registro);
        logger.setLevel(nivelAnterior);
    }

    private PoliticaDeLimites politicaConContador() {
        ContadorDeVentanas contador = (grupo, sujeto, ventanas) -> {
            gruposContados.add(grupo);
            sujetosContados.add(sujeto.clave());
            return loQueDiceElContador;
        };
        TopesDeLimites topes = new TopesDeLimites() {
            @Override
            public boolean habilitado() {
                return true;
            }

            @Override
            public Duration cacheDelModo() {
                return Duration.ofSeconds(30);
            }

            @Override
            public ModoDeLimite modoPorDefecto(GrupoDeLimite grupo) {
                return modo;
            }

            @Override
            public List<Ventana> ventanasDe(GrupoDeLimite grupo) {
                return List.of(new Ventana("1m", Duration.ofMinutes(1), 10));
            }
        };
        return new PoliticaDeLimites(topes, contador, java.util.Map::of, mock(RegistrarMetricaDeLimitesPort.class),
                mock(AnotarCuentaQueHabriaBloqueadoPort.class), FixedClock.at(AHORA));
    }

    private static void conSesionDe(String cuenta) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(cuenta, null, AuthorityUtils.NO_AUTHORITIES));
    }

    private record Salida(MockHttpServletResponse respuesta, boolean llegoAlControlador) {
    }

    private Salida filtrar(MockHttpServletRequest pedido) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(pedido, respuesta, cadena);
        return new Salida(respuesta, cadena.getRequest() != null);
    }

    // spec 050 FR-015
    @Test
    @DisplayName("APLICAR y se pasó: 429 con Retry-After, el formato de error de siempre con codigo DEMASIADOS_PEDIDOS, y no llega al controller")
    void rechazaConElContratoDelPlan() throws Exception {
        conSesionDe(CUENTA);

        Salida salida = filtrar(new MockHttpServletRequest("POST", "/api/v1/wall"));

        MockHttpServletResponse respuesta = salida.respuesta();
        assertThat(salida.llegoAlControlador()).isFalse();
        assertThat(respuesta.getStatus()).isEqualTo(429);
        assertThat(respuesta.getHeader("Retry-After")).isEqualTo("42");
        assertThat(respuesta.getContentType()).startsWith("application/json");
        JsonNode cuerpo = JSON.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("message").asText()).isEqualTo("Espera un momento y vuelve a intentarlo.");
        assertThat(cuerpo.get("codigo").asText()).isEqualTo("DEMASIADOS_PEDIDOS");
        assertThat(Instant.parse(cuerpo.get("timestamp").asText())).isEqualTo(AHORA);
        assertThat(cuerpo.fieldNames()).toIterable().containsExactlyInAnyOrder("message", "timestamp", "codigo");
    }

    // spec 050 FR-015
    @Test
    @DisplayName("el 429 no revela el nombre del grupo ni el tope, y conserva las cabeceras de CORS que ya puso Spring Security")
    void noRevelaElGrupoNiElTopeYConservaElCors() throws Exception {
        conSesionDe(CUENTA);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        respuesta.setHeader("Access-Control-Allow-Origin", "http://localhost:8081");
        respuesta.setHeader("Access-Control-Allow-Credentials", "true");

        filtro.doFilter(new MockHttpServletRequest("POST", "/api/v1/wall"), respuesta, new MockFilterChain());

        assertThat(respuesta.getStatus()).isEqualTo(429);
        assertThat(respuesta.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:8081");
        assertThat(respuesta.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        String todo = respuesta.getContentAsString() + respuesta.getHeaderNames();
        assertThat(todo).doesNotContain("muro", "MURO", "tope", "ventana");
    }

    // spec 050 FR-016
    @Test
    @DisplayName("REGISTRAR: aunque se pase, el pedido llega al controller, sin 429 y sin Retry-After")
    void registrarNuncaDevuelve429() throws Exception {
        modo = ModoDeLimite.REGISTRAR;
        conSesionDe(CUENTA);

        Salida salida = filtrar(new MockHttpServletRequest("POST", "/api/v1/wall"));

        assertThat(salida.llegoAlControlador()).isTrue();
        assertThat(salida.respuesta().getStatus()).isEqualTo(200);
        assertThat(salida.respuesta().getHeader("Retry-After")).isNull();
        assertThat(gruposContados).as("cuenta igual: si no, no habría qué medir").containsExactly(GrupoDeLimite.MURO);
    }

    // spec 050 FR-019
    @Test
    @DisplayName("una línea de registro por «habría bloqueado» y por bloqueo: grupo, método, ruta sin ids y cuenta; nada de cabeceras ni token")
    void laLineaDelRegistro() throws Exception {
        modo = ModoDeLimite.REGISTRAR;
        conSesionDe(CUENTA);
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/api/v1/wall/" + UUID.randomUUID() + "/comments");
        pedido.addHeader("X-Auth-Token", "token-secreto-de-la-sesion");
        pedido.addHeader("Authorization", "Bearer otro-secreto");
        filtrar(pedido);
        modo = ModoDeLimite.APLICAR;
        filtrar(new MockHttpServletRequest("POST", "/api/v1/wall"));

        assertThat(registro.list).hasSize(2);
        String habria = registro.list.get(0).getFormattedMessage();
        assertThat(registro.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(habria).isEqualTo("limite_por_cuenta resultado=habria_bloqueado grupo=muro metodo=POST ruta=/api/v1/wall/{id}/comments "
                + "ventana=1m cuenta=" + CUENTA);
        String bloqueo = registro.list.get(1).getFormattedMessage();
        assertThat(registro.list.get(1).getLevel()).isEqualTo(Level.WARN);
        assertThat(bloqueo).startsWith("limite_por_cuenta resultado=bloqueado grupo=muro metodo=POST ruta=/api/v1/wall ");
        assertThat(habria + bloqueo).doesNotContain("secreto");
    }

    // spec 050 FR-019
    @Test
    @DisplayName("un pedido que pasa sin tocar el tope no deja ninguna línea en el registro")
    void loQuePasaNoHaceRuido() throws Exception {
        loQueDiceElContador = new Resultado(true, "", 0, List.of(new Uso("1m", 1, 10)));
        conSesionDe(CUENTA);

        assertThat(filtrar(new MockHttpServletRequest("GET", "/api/v1/habits")).llegoAlControlador()).isTrue();
        assertThat(registro.list).isEmpty();
    }

    // spec 050 FR-013
    @ParameterizedTest(name = "{0} {1} no se cuenta")
    @DisplayName("lo exento por escrito no se cuenta: autenticación y alta públicas, el WebSocket, la salud y el OPTIONS del CORS")
    @CsvSource({
            "POST,/api/v1/auth/login",
            "POST,/api/v1/auth/password/reset",
            "POST,/api/v1/account-requests",
            "GET,/ws",
            "GET,/salud",
            "OPTIONS,/api/v1/wall",
    })
    void loExentoNoSeCuenta(String metodo, String ruta) throws Exception {
        Salida salida = filtrar(new MockHttpServletRequest(metodo, ruta));

        assertThat(salida.llegoAlControlador()).isTrue();
        assertThat(gruposContados).isEmpty();
    }

    // spec 050 FR-012
    @Test
    @DisplayName("se cuenta a la cuenta de la sesión; sin sesión, a su origen (la IP de CloudFront, no la de la conexión)")
    void aQuienSeCuenta() throws Exception {
        modo = ModoDeLimite.REGISTRAR;
        conSesionDe(CUENTA);
        filtrar(new MockHttpServletRequest("GET", "/api/v1/habits"));
        SecurityContextHolder.clearContext();

        MockHttpServletRequest anonimo = new MockHttpServletRequest("POST", "/api/v1/webhooks/whatsapp");
        anonimo.setRemoteAddr("172.18.0.5");
        anonimo.addHeader("X-Forwarded-For", "6.6.6.6, 198.51.100.7");
        filtrar(anonimo);

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("clave", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        MockHttpServletRequest conTokenAnonimo = new MockHttpServletRequest("GET", "/api/v1/habits");
        conTokenAnonimo.setRemoteAddr("203.0.113.9");
        filtrar(conTokenAnonimo);

        assertThat(sujetosContados).containsExactly("c:" + CUENTA, "ip:198.51.100.7", "ip:203.0.113.9");
        assertThat(gruposContados).containsExactly(GrupoDeLimite.LECTURA, GrupoDeLimite.WEBHOOK_WHATSAPP, GrupoDeLimite.LECTURA);
    }

    // spec 050 FR-017
    @Test
    @DisplayName("si la política lanza cualquier excepción, el pedido pasa: nunca un 500 por el límite")
    void unaFallaDelLimiteNoCambiaLaRespuesta() throws Exception {
        PoliticaDeLimites rota = mock(PoliticaDeLimites.class);
        when(rota.decidir(any(), any())).thenThrow(new IllegalStateException("explotó"));
        filtro = new FiltroDeLimitePorCuenta(rota, FixedClock.at(AHORA));
        conSesionDe(CUENTA);

        Salida salida = filtrar(new MockHttpServletRequest("POST", "/api/v1/wall"));

        assertThat(salida.llegoAlControlador()).isTrue();
        assertThat(salida.respuesta().getStatus()).isEqualTo(200);
    }

    // spec 050 FR-021
    @Test
    @DisplayName("una cuenta en «ver como» recibe su 403 de siempre ANTES de contarse: el filtro de solo lectura va primero")
    void verComoSeRechazaAntesDeContarse() throws Exception {
        modo = ModoDeLimite.APLICAR;
        conSesionDe(CUENTA);
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionDeSoloLectura.ABIERTA_POR, UUID.randomUUID().toString());
        sesion.setAttribute(SesionDeSoloLectura.VENCE_EN, AHORA.plusSeconds(600).toEpochMilli());
        sesion.setAttribute(SesionDeSoloLectura.ROL, "MENTOR");
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/api/v1/wall");
        pedido.setSession(sesion);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        GenericServlet controlador = new GenericServlet() {
            @Override
            public void service(ServletRequest req, ServletResponse res) {
                throw new AssertionError("no debía llegar al controller");
            }
        };

        new MockFilterChain(controlador, new FiltroDeSoloLectura(FixedClock.at(AHORA)), filtro).doFilter(pedido, respuesta);

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(respuesta.getContentAsString()).contains("MODO_SOLO_LECTURA");
        assertThat(gruposContados).as("ni siquiera se contó").isEmpty();
        assertThat(FiltroDeLimitePorCuentaConfig.ORDEN).isGreaterThan(FiltroDeSoloLecturaConfig.ORDEN);
    }

    // spec 050 FR-013
    @Test
    @DisplayName("se registra solo para /api/v1/* y después de la cadena de Spring Security (−100) y del filtro de solo lectura (−90)")
    void elRegistroDelFiltro() {
        FilterRegistrationBean<FiltroDeLimitePorCuenta> registro = new FiltroDeLimitePorCuentaConfig()
                .filtroDeLimitePorCuenta(politicaConContador(), FixedClock.at(AHORA));

        assertThat(registro.getUrlPatterns()).containsExactly("/api/v1/*");
        assertThat(registro.getOrder()).isEqualTo(-85).isGreaterThan(-100);
        assertThat(registro.getFilter()).isInstanceOf(FiltroDeLimitePorCuenta.class);
    }

    // spec 050 FR-013
    @Test
    @DisplayName("el contexto de la aplicación no cambia el grupo: se mira la ruta sin él")
    void conContexto() throws Exception {
        modo = ModoDeLimite.REGISTRAR;
        conSesionDe(CUENTA);
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/renaser/api/v1/wall");
        pedido.setContextPath("/renaser");

        filtrar(pedido);

        assertThat(gruposContados).containsExactly(GrupoDeLimite.MURO);
    }

    @Test
    @DisplayName("el SujetoDeLimite no deja pasar una clave vacía ni larguísima")
    void elSujetoSeAcota() {
        assertThat(SujetoDeLimite.cuenta("  ").clave()).isEqualTo("c:desconocido");
        assertThat(SujetoDeLimite.origen("x".repeat(500)).clave()).hasSizeLessThanOrEqualTo(80);
        assertThat(SujetoDeLimite.origen("198.51.100.7").paraElRegistro()).as("nunca la IP en el registro").isEqualTo("sin_cuenta");
    }
}
