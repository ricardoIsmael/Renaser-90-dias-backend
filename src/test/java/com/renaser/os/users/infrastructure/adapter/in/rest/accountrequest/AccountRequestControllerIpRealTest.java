package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.shared.web.SecurityConfig;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.CheckAccountRequestStatusUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ConsultarEmailRegistradoUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.DeleteAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ListAccountRequestsUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.RejectAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.VerificarDominioEmailUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.VerificarDominioEmailUseCase.ResultadoVerificacionDominio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auditoria NFR 2026-09-06 (2a pasada), corregida en E-678. El backend vive detras de CloudFront y de un nginx
 * transparente: la conexion que ve Spring es la del nginx, asi que la IP que alimenta los limites por IP (login, alta,
 * reset) sale de {@code X-Forwarded-For}. Esta prueba fija que al caso de uso le llega la que agrego CloudFront (el
 * valor de mas a la derecha) y no lo que escriba el cliente a su izquierda.
 *
 * <p>No se registra ningun {@code ForwardedHeaderFilter}: en produccion
 * {@code server.forward-headers-strategy} es {@code none} (lo fija {@code CabecerasReenviadasTest}) y la IP la resuelve
 * {@code DireccionIpDelCliente} leyendo la cabecera tal como llega. El contexto completo, con el servidor real, lo
 * ejercita {@code IpDelClienteIT}.
 */
@WebMvcTest(AccountRequestController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class AccountRequestControllerIpRealTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitAccountRequestUseCase submitUseCase;
    @MockitoBean
    private ApproveAccountRequestUseCase approveUseCase;
    @MockitoBean
    private RejectAccountRequestUseCase rejectUseCase;
    @MockitoBean
    private ListAccountRequestsUseCase listUseCase;
    @MockitoBean
    private DeleteAccountRequestUseCase deleteUseCase;
    @MockitoBean
    private CheckAccountRequestStatusUseCase checkStatusUseCase;
    @MockitoBean
    private ConsultarEmailRegistradoUseCase consultarEmailRegistradoUseCase;
    @MockitoBean
    private VerificarDominioEmailUseCase verificarDominioEmailUseCase;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    /** Spec 025: el controlador suma las opciones de la hoja «Aceptar»; estas pruebas no las ejercen. */
    @MockitoBean
    private com.renaser.os.users.application.ports.in.accountrequest.ConsultarOpcionesDeAceptacionUseCase opcionesDeAceptacion;

    @Test
    @DisplayName("la IP que ve el limite de tasa es la que agrego CloudFront (la ultima), no la que escribio el cliente")
    void laIpDelClienteEsLaUltimaDeXForwardedFor() throws Exception {
        when(consultarEmailRegistradoUseCase.estaRegistrado(eq("alguien@ejemplo.com"), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/account-requests/exists")
                        .header("X-Forwarded-For", "198.51.100.7, 203.0.113.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguien@ejemplo.com\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(consultarEmailRegistradoUseCase).estaRegistrado(eq("alguien@ejemplo.com"), ip.capture());
        assertThat(ip.getValue()).isEqualTo("203.0.113.9");
    }

    /**
     * Regresion de E-151. Con un cliente IPv6, la direccion puede llegar <b>entre corchetes</b> (como host de URI) o con
     * puerto. Postgres rechaza esa forma en una columna {@code inet}
     * ({@code invalid input syntax for type inet: "[2803:...]"}) y el alta explotaba: nadie con
     * conexion IPv6 podia registrarse, mientras que en IPv4 todo seguia funcionando.
     *
     * <p>Esta prueba falla contra el codigo anterior al arreglo — que es la unica forma de que
     * sirva de algo.
     */
    @Test
    @DisplayName("un cliente IPv6 llega sin corchetes, que es lo unico que acepta una columna inet")
    void laIpDeUnClienteIpv6LlegaSinCorchetes() throws Exception {
        when(consultarEmailRegistradoUseCase.estaRegistrado(eq("ipv6@ejemplo.com"), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/account-requests/exists")
                        .header("X-Forwarded-For", "198.51.100.7, 2803:9810:6075:9310:c63b:3904:e158:3228")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ipv6@ejemplo.com\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(consultarEmailRegistradoUseCase).estaRegistrado(eq("ipv6@ejemplo.com"), ip.capture());
        assertThat(ip.getValue())
                .as("sin corchetes: Postgres rechaza \"[2803:...]\" en una columna inet")
                .isEqualTo("2803:9810:6075:9310:c63b:3904:e158:3228");
    }

    /**
     * Regresion del hallazgo del cupo (2026-09-21). De los tres handlers publicos de este
     * controller, {@code verify-email} era el unico que NO recibia la {@code HttpServletRequest},
     * y por eso era el unico que no podia gastar cupo: no sabia a quien contarle la peticion. Y es
     * justamente el que sale de la maquina — consulta por DNS el dominio que vino en el cuerpo —,
     * asi que el mas caro de los tres era tambien el unico sin tope.
     *
     * <p>Aca se fija la mitad del arreglo que vive en el borde: que al caso de uso le llegue la IP
     * del cliente real y no la del borde de CloudFront, que es lo que hace que el contador cuente
     * a alguien. La otra mitad —que con esa IP se gaste cupo ANTES de salir a la red— la fija
     * {@code ConsultaEmailServiceTest.cortaAntesDeSalirALaRed}.
     */
    @Test
    @DisplayName("verify-email tambien recibe la IP del cliente: es el handler que sale a la red")
    void verifyEmailRecibeLaIpDelClienteReal() throws Exception {
        when(verificarDominioEmailUseCase.verificar(eq("alguien@ejemplo.com"), anyString()))
                .thenReturn(ResultadoVerificacionDominio.puedeRecibir());

        mockMvc.perform(post("/api/v1/account-requests/verify-email")
                        .header("X-Forwarded-For", "198.51.100.7, 203.0.113.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguien@ejemplo.com\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(verificarDominioEmailUseCase).verificar(eq("alguien@ejemplo.com"), ip.capture());
        assertThat(ip.getValue()).isEqualTo("203.0.113.9");
    }
}
