package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.in.eliminacion.AdministrarEliminacionDeCuentasUseCase;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administracion → «Eliminar cuenta» (D-243). Autorizacion negativa de la regla 03: el aprendiz lo
 * corta el interceptor; MENTOR y LIDER pasan el interceptor (A-1, falla-abierto / modo sombra) y los
 * corta el caso de uso; una cuenta SUSPENDED recibe 403 aunque sea ADMIN.
 */
@WebMvcTest(EliminacionDeCuentasAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class EliminacionDeCuentasAdminControllerTest {

    private static final String CUERPO = "{\"confirmEmail\":\"ana@renaser.dev\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private AdministrarEliminacionDeCuentasUseCase administrar;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private UUID sesionDe(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(id)))
                .thenReturn(Optional.of(new UserSummary(UserId.of(id), "Actor", null, rol, estado)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id.toString(), null, List.of()));
        return id;
    }

    @Test
    @DisplayName("un ADMIN activo elimina: 204")
    void adminElimina() throws Exception {
        UUID admin = sesionDe(UserRole.ADMIN, UserStatus.ACTIVE);
        UUID objetivo = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/users/{id}/account-deletion", objetivo)
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNoContent());
        verify(administrar).eliminar(UserId.of(admin), UserId.of(objetivo), "ana@renaser.dev");
    }

    @Test
    @DisplayName("un aprendiz no puede eliminar a otro: 403 y el caso de uso ni se llama")
    void aprendiz403() throws Exception {
        sesionDe(UserRole.TRAINEE, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/admin/users/{id}/account-deletion", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden());
        verifyNoInteractions(administrar);
    }

    @Test
    @DisplayName("un ADMIN SUSPENDIDO con sesion viva recibe 403")
    void adminSuspendido403() throws Exception {
        sesionDe(UserRole.ADMIN, UserStatus.SUSPENDED);

        mockMvc.perform(post("/api/v1/admin/users/{id}/account-deletion", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden());
        verifyNoInteractions(administrar);
    }

    @Test
    @DisplayName("MENTOR y LIDER DE MENTORES: el caso de uso los rechaza con 403")
    void mentorYLider403() throws Exception {
        for (UserRole rol : new UserRole[] {UserRole.MENTOR, UserRole.MENTOR_LEAD}) {
            sesionDe(rol, UserStatus.ACTIVE);
            doThrow(new NotAuthorizedException("Solo ADMIN/ALCHEMIST eliminan cuentas"))
                    .when(administrar).eliminar(any(), any(), any());

            mockMvc.perform(post("/api/v1/admin/users/{id}/account-deletion", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("sin el correo de confirmacion: 400")
    void sinConfirmacion400() throws Exception {
        sesionDe(UserRole.ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/admin/users/{id}/account-deletion", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(administrar);
    }

    @Test
    @DisplayName("recuperar: ADMIN 204, aprendiz 403")
    void recuperar() throws Exception {
        UUID admin = sesionDe(UserRole.ADMIN, UserStatus.ACTIVE);
        UUID objetivo = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin/users/{id}/account-deletion/recover", objetivo))
                .andExpect(status().isNoContent());
        verify(administrar).recuperar(UserId.of(admin), UserId.of(objetivo));

        sesionDe(UserRole.TRAINEE, UserStatus.ACTIVE);
        mockMvc.perform(post("/api/v1/admin/users/{id}/account-deletion/recover", objetivo))
                .andExpect(status().isForbidden());
    }
}
