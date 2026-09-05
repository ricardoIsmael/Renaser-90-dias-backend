package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarCodigoResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarCodigoResetContrasenaUseCase.SolicitarCodigoResetContrasenaCommand;
import com.renaser.os.users.application.ports.in.autenticacion.VerificarCodigoResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.VerificarCodigoResetContrasenaUseCase.ResultadoVerificacionReset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/auth/password/forgot} y {@code /verify-code} (docs/MODULO_AUTH.md §7.6,
 * D-102). El foco es el transporte: publicos (sin sesion), 202 siempre en {@code forgot}, 200 con
 * el {@code resetToken} en {@code verify-code}, y 400 cuando el codigo no sirve o ni siquiera
 * tiene la forma correcta.
 */
@WebMvcTest(ResetContrasenaPorCodigoController.class)
@AutoConfigureMockMvc(addFilters = false)
class ResetContrasenaPorCodigoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SolicitarCodigoResetContrasenaUseCase solicitarCodigoUseCase;
    @MockitoBean
    private VerificarCodigoResetContrasenaUseCase verificarCodigoUseCase;

    @Test
    @DisplayName("forgot: 202 y el caso de uso recibe el email y la IP del request")
    void forgotDevuelve202YPasaEmailEIp() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguien@renaser.dev\"}").with(req -> {
                            req.setRemoteAddr("203.0.113.7");
                            return req;
                        }))
                .andExpect(status().isAccepted());

        ArgumentCaptor<SolicitarCodigoResetContrasenaCommand> captor =
                ArgumentCaptor.forClass(SolicitarCodigoResetContrasenaCommand.class);
        verify(solicitarCodigoUseCase).solicitarCodigo(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("alguien@renaser.dev");
        assertThat(captor.getValue().requestIp()).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("forgot: el 202 es el mismo exista o no la cuenta — el controller no sabe ni pregunta")
    void forgotNoDistingueSiLaCuentaExiste() throws Exception {
        // El caso de uso no hace nada cuando no hay cuenta; la respuesta HTTP es identica.
        mockMvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@renaser.dev\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void forgotConEmailInvalidoDevuelve400SinLlegarAlCasoDeUso() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-un-correo\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(solicitarCodigoUseCase);
    }

    @Test
    void forgotConLimiteDeTasaExcedidoDevuelve429() throws Exception {
        doThrow(new RateLimitExceededException("Limite de solicitudes de reseteo de contrasena excedido"))
                .when(solicitarCodigoUseCase).solicitarCodigo(any());

        mockMvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguien@renaser.dev\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("verify-code: 200 con el resetToken que despues acepta /password/reset-confirm")
    void verifyCodeDevuelve200ConElResetToken() throws Exception {
        when(verificarCodigoUseCase.verificarCodigo(any())).thenReturn(new ResultadoVerificacionReset("token-opaco"));

        mockMvc.perform(post("/api/v1/auth/password/verify-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguien@renaser.dev\",\"codigo\":\"483920\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").value("token-opaco"));
    }

    @Test
    void verifyCodeConCodigoInvalidoDevuelve400() throws Exception {
        when(verificarCodigoUseCase.verificarCodigo(any())).thenThrow(new CodigoVerificacionInvalidoException());

        mockMvc.perform(post("/api/v1/auth/password/verify-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguien@renaser.dev\",\"codigo\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El codigo no es valido o ya vencio"));
    }

    @Test
    @DisplayName("verify-code: un codigo que no son 6 digitos falla en el DTO, sin gastar un intento real")
    void verifyCodeConFormaInvalidaDevuelve400SinLlegarAlCasoDeUso() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/verify-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alguien@renaser.dev\",\"codigo\":\"12345\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(verificarCodigoUseCase);
    }
}
