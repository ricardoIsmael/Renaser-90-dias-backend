package com.renaser.os.habits.infrastructure.adapter.in.rest.audioterapiaadmin;

import com.renaser.os.habits.application.ports.in.audioterapiaadmin.ActualizarDuracionAudioterapiaUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEG-02 del e2e del 2026-09-27 (E-370): {@code PATCH /api/v1/admin/audio-therapies/{week}} sin
 * {@code durationDays} respondía 500 ({@code NullPointerException} al desenvolver el {@code Integer} en el
 * controller). Es un cuerpo mal formado: 400 de validación, sin llegar al caso de uso.
 */
@WebMvcTest(AudioTherapyAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AudioTherapyAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private ActualizarDuracionAudioterapiaUseCase actualizarUseCase;

    @Test
    @DisplayName("sin durationDays (o en null) es 400, no un 500, y el caso de uso ni se llama")
    void sinDuracionEs400() throws Exception {
        UUID admin = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(admin))).thenReturn(Optional.of(
                new UserSummary(UserId.of(admin), "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));

        mockMvc.perform(patch("/api/v1/admin/audio-therapies/{week}", 3).header("X-Actor-Id", admin.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/admin/audio-therapies/{week}", 3).header("X-Actor-Id", admin.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationDays\":null}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(actualizarUseCase);
    }
}
