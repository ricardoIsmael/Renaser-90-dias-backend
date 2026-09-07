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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.filter.ForwardedHeaderFilter;
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
 * Auditoria NFR 2026-09-06 (2a pasada). El backend vive detras de CloudFront, y sin
 * {@code server.forward-headers-strategy} el {@code getRemoteAddr()} que alimenta los limites por
 * IP (login, alta, reset) devolvia la IP del borde de CloudFront para TODOS los clientes: un solo
 * contador compartido por todo el mundo. Esta prueba fija que, con el filtro de cabeceras
 * reenviadas delante del controller, la IP que llega al caso de uso es la del cliente real
 * (primer valor de {@code X-Forwarded-For}).
 *
 * <p><b>Por que el filtro se registra aca a mano.</b> En produccion lo registra Spring Boot al
 * leer {@code server.forward-headers-strategy=framework}: el bean vive en
 * {@code org.springframework.boot.web.server.autoconfigure.servlet.ServletWebServerConfiguration}
 * (verificado con {@code javap}: {@code @ConditionalOnProperty(name="server.forward-headers-strategy",
 * havingValue="framework")}). Esa configuracion NO forma parte del slice {@code @WebMvcTest} — la
 * primera version de esta prueba fallo con {@code expected "203.0.113.9" but was "127.0.0.1"} por
 * eso, no por la configuracion (E-149). La configuracion de abajo replica exactamente lo que hace
 * Boot; lo que se prueba es el efecto del filtro sobre {@code getRemoteAddr()}, que es lo que
 * consumen los controllers.
 */
@WebMvcTest(AccountRequestController.class)
@Import({SecurityConfig.class, AccountRequestControllerIpRealTest.FiltroComoEnProduccion.class})
@TestPropertySource(properties = {
        "renaser.web.cors.origenes=http://localhost:8081",
        "server.forward-headers-strategy=framework"
})
class AccountRequestControllerIpRealTest {

    @TestConfiguration
    static class FiltroComoEnProduccion {
        @Bean
        FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
            return new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        }
    }

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

    @Test
    @DisplayName("detras de CloudFront, la IP que ve el limite de tasa es la del cliente, no la del borde")
    void laIpDelClienteSaleDeXForwardedFor() throws Exception {
        when(consultarEmailRegistradoUseCase.estaRegistrado(eq("alguien@ejemplo.com"), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/account-requests/exists")
                        .header("X-Forwarded-For", "203.0.113.9, 130.176.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguien@ejemplo.com\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(consultarEmailRegistradoUseCase).estaRegistrado(eq("alguien@ejemplo.com"), ip.capture());
        assertThat(ip.getValue()).isEqualTo("203.0.113.9");
    }

    /**
     * Regresion de E-151. Con un cliente IPv6, el {@code ForwardedHeaderFilter} reconstruye la
     * direccion como host de URI y {@code getRemoteAddr()} la devuelve <b>entre corchetes</b>.
     * Postgres rechaza esa forma en una columna {@code inet}
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
                        .header("X-Forwarded-For", "2803:9810:6075:9310:c63b:3904:e158:3228, 130.176.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ipv6@ejemplo.com\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(consultarEmailRegistradoUseCase).estaRegistrado(eq("ipv6@ejemplo.com"), ip.capture());
        assertThat(ip.getValue())
                .as("sin corchetes: Postgres rechaza \"[2803:...]\" en una columna inet")
                .isEqualTo("2803:9810:6075:9310:c63b:3904:e158:3228");
    }
}
