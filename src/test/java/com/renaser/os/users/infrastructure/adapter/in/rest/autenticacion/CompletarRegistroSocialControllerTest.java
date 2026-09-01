package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.shared.domain.RegistroPendienteSocialInvalidoException;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase.CompletarRegistroSocialCommand;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.VincularIdentidadSocialUseCase;
import com.renaser.os.users.application.ports.in.user.GetMyProfileUseCase;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.infrastructure.adapter.in.web.security.SesionWebAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/auth/social/complete} (docs/MODULO_AUTH.md §6.10, D-65). El foco es el
 * transporte: publico (sin sesion), 202 con el mismo cuerpo que el alta por formulario, y 400
 * cuando el token de continuacion no es valido.
 */
@WebMvcTest(AutenticacionController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompletarRegistroSocialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IniciarSesionUseCase iniciarSesionUseCase;
    @MockitoBean
    private GetMyProfileUseCase getMyProfileUseCase;
    @MockitoBean
    private SesionWebAdapter sesionWeb;
    @MockitoBean
    private SolicitarResetContrasenaUseCase solicitarResetContrasenaUseCase;
    @MockitoBean
    private ConfirmarResetContrasenaUseCase confirmarResetContrasenaUseCase;
    @MockitoBean
    private IniciarSesionConProveedorUseCase iniciarSesionConProveedorUseCase;
    @MockitoBean
    private VincularIdentidadSocialUseCase vincularIdentidadSocialUseCase;
    @MockitoBean
    private CompletarRegistroSocialUseCase completarRegistroSocialUseCase;

    private static final String CUERPO = """
            {"registroPendienteToken":"un-token","fullName":"Persona Confirmada","phone":"+54 341 1234567",
             "city":"Rosario"}
            """;

    @Test
    @DisplayName("token valido: 202 con el mismo cuerpo que el alta por formulario, PUBLICO (sin sesion)")
    void tokenValidoDevuelve202ConLaAccountRequest() throws Exception {
        AccountRequestId solicitudId = AccountRequestId.of(UUID.randomUUID());
        when(completarRegistroSocialUseCase.completar(any())).thenReturn(solicitudId);

        mockMvc.perform(post("/api/v1/auth/social/complete").contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accountRequestId").value(solicitudId.value().toString()));

        verifyNoInteractions(sesionWeb);
        ArgumentCaptor<CompletarRegistroSocialCommand> comando =
                ArgumentCaptor.forClass(CompletarRegistroSocialCommand.class);
        verify(completarRegistroSocialUseCase).completar(comando.capture());
        assertThat(comando.getValue().registroPendienteToken()).isEqualTo("un-token");
        assertThat(comando.getValue().fullName()).isEqualTo("Persona Confirmada");
        assertThat(comando.getValue().phone()).isEqualTo("+54 341 1234567");
        assertThat(comando.getValue().city()).isEqualTo("Rosario");
    }

    @Test
    @DisplayName("sin phone/city: igual 202, son opcionales (D-61)")
    void sinPhoneNiCityDevuelve202Igual() throws Exception {
        when(completarRegistroSocialUseCase.completar(any())).thenReturn(AccountRequestId.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/auth/social/complete").contentType(MediaType.APPLICATION_JSON).content("""
                        {"registroPendienteToken":"un-token","fullName":"Persona Confirmada"}
                        """))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("token invalido/vencido/ya usado: 400 y mensaje accionable")
    void tokenInvalidoDevuelve400() throws Exception {
        when(completarRegistroSocialUseCase.completar(any()))
                .thenThrow(new RegistroPendienteSocialInvalidoException());

        mockMvc.perform(post("/api/v1/auth/social/complete").contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("sin registroPendienteToken: 400 y no llega al caso de uso")
    void sinTokenDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social/complete").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Persona Confirmada"}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(completarRegistroSocialUseCase);
    }

    @Test
    @DisplayName("sin fullName: 400 y no llega al caso de uso")
    void sinFullNameDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/social/complete").contentType(MediaType.APPLICATION_JSON).content("""
                        {"registroPendienteToken":"un-token"}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(completarRegistroSocialUseCase);
    }

    /**
     * Endpoint PUBLICO a proposito (docs/MODULO_AUTH.md §6.10): quien lo llama todavia no tiene
     * cuenta. Mandar {@code X-Actor-Id} no deberia importar ni consultarse — a diferencia de
     * {@code /social/link}, este endpoint no usa la sesion para nada.
     */
    @Test
    @DisplayName("ignora X-Actor-Id: el endpoint es publico, no depende de sesion ni de header")
    void noConsultaSesionNiActorHeader() throws Exception {
        when(completarRegistroSocialUseCase.completar(any())).thenReturn(AccountRequestId.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/auth/social/complete")
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isAccepted());

        verifyNoInteractions(sesionWeb);
    }
}
