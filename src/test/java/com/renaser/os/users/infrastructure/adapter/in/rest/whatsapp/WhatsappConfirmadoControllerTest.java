package com.renaser.os.users.infrastructure.adapter.in.rest.whatsapp;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.domain.WhatsappDeLaCuentaException;
import com.renaser.os.shared.domain.WhatsappDeLaCuentaException.Motivo;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.in.whatsapp.WhatsappConfirmadoUseCase;
import com.renaser.os.users.application.ports.in.whatsapp.WhatsappConfirmadoUseCase.ConfirmarCommand;
import com.renaser.os.users.application.ports.in.whatsapp.WhatsappConfirmadoUseCase.Estado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Yo → «WhatsApp» (spec 051): siempre la cuenta de la sesion, 403 a una suspendida, y los rechazos con su {@code codigo}. */
@WebMvcTest(WhatsappConfirmadoController.class)
@AutoConfigureMockMvc(addFilters = false)
class WhatsappConfirmadoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private WhatsappConfirmadoUseCase useCase;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private UUID sesionDe(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id)))
                .thenReturn(Optional.of(new UserSummary(UserId.of(id), "Actor", null, rol, estado)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id.toString(), null, List.of()));
        return id;
    }

    // spec 051 FR-001
    @Test
    @DisplayName("GET devuelve disponibilidad y el numero enmascarado (nunca el completo)")
    void estado() throws Exception {
        UUID id = sesionDe(UserRole.MENTOR, UserStatus.ACTIVE);
        when(useCase.estado(UserId.of(id))).thenReturn(new Estado(true, Optional.of("+51 9•• ••• •21")));

        mockMvc.perform(get("/api/v1/users/me/whatsapp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.confirmed").value("+51 9•• ••• •21"));
    }

    // spec 051 FR-004
    @Test
    @DisplayName("el cuerpo no elige la cuenta: un userId en el cuerpo se ignora y se confirma la de la sesion")
    void noSeConfirmaLaDeOtro() throws Exception {
        UUID id = sesionDe(UserRole.ALCHEMIST, UserStatus.ACTIVE);
        UUID otro = UUID.randomUUID();
        when(useCase.confirmar(any())).thenReturn("+51 9•• ••• •21");

        mockMvc.perform(post("/api/v1/users/me/whatsapp/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatsapp\":\"+51 987654321\",\"code\":\"123456\",\"userId\":\"" + otro + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value("+51 9•• ••• •21"));
        verify(useCase).confirmar(new ConfirmarCommand(UserId.of(id), "+51 987654321", "123456", "127.0.0.1"));
    }

    // spec 051 FR-003
    @Test
    @DisplayName("un rechazo del servicio sale con su estado y su codigo: 422 CODIGO_INCORRECTO, 409 NUMERO_EN_USO")
    void rechazosConCodigo() throws Exception {
        sesionDe(UserRole.TRAINEE, UserStatus.ACTIVE);
        when(useCase.confirmar(any())).thenThrow(new WhatsappDeLaCuentaException(Motivo.CODIGO_INCORRECTO))
                .thenThrow(new WhatsappDeLaCuentaException(Motivo.NUMERO_EN_USO));
        String cuerpo = "{\"whatsapp\":\"+51 987654321\",\"code\":\"123456\"}";

        mockMvc.perform(post("/api/v1/users/me/whatsapp/confirm").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INCORRECTO"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mockMvc.perform(post("/api/v1/users/me/whatsapp/confirm").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NUMERO_EN_USO"));
    }

    // spec 051 FR-004
    @Test
    @DisplayName("una cuenta SUSPENDIDA con sesion viva recibe 403 en los tres endpoints y no se toca nada")
    void suspendida403() throws Exception {
        sesionDe(UserRole.MENTOR, UserStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/users/me/whatsapp")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/me/whatsapp/code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"whatsapp\":\"+51 987654321\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/me/whatsapp/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"whatsapp\":\"+51 987654321\",\"code\":\"123456\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }
}
