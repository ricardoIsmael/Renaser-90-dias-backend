package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarComunidadDeLaFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarComunidadDeLaFormacionUseCase.FijarTipoCommand;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prender y apagar un tipo de comunidad (spec 024, FR-034): el interceptor deja pasar solo a quien tiene {@code MANAGE_ROLES} (Administración y
 * Alquimista) con la cuenta activa; cualquier otro rol o una cuenta suspendida reciben 403 sin llegar al caso de uso.
 */
@WebMvcTest(ComunidadDeLaFormacionAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class ComunidadDeLaFormacionAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigurarComunidadDeLaFormacionUseCase configurar;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private UserId sesionDe(UserRole rol, UserStatus estado) {
        UserId actor = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Actor", null, rol, estado)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.value().toString(), null, List.of()));
        return actor;
    }

    @Test
    @DisplayName("ADMIN activo apaga un tipo y recibe los que quedan prendidos")
    void adminApagaUnTipo() throws Exception {
        UserId admin = sesionDe(UserRole.ADMIN, UserStatus.ACTIVE);
        when(configurar.fijar(new FijarTipoCommand(admin, Formacion.DIPLOMADO, TipoDeComunidad.DIRECTO, false)))
                .thenReturn(Set.of(TipoDeComunidad.GENERAL, TipoDeComunidad.GRUPO, TipoDeComunidad.SOPORTE));

        mockMvc.perform(put("/api/v1/admin/formaciones/DIPLOMADO/comunidad/DIRECTO")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prendido\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prendidos.length()").value(3));

        verify(configurar).fijar(new FijarTipoCommand(admin, Formacion.DIPLOMADO, TipoDeComunidad.DIRECTO, false));
    }

    @Test
    @DisplayName("autorización negativa: un aprendiz recibe 403 del interceptor (el mentor y el líder pasan el interceptor y los rechaza el caso de uso: lo prueba ComunidadPorFormacionIT)")
    void otrosRolesNoPuedenConfigurar() throws Exception {
        for (UserRole rol : List.of(UserRole.TRAINEE)) {
            sesionDe(rol, UserStatus.ACTIVE);
            mockMvc.perform(put("/api/v1/admin/formaciones/ESCUELA/comunidad/SOPORTE")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"prendido\": false}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(configurar);
    }

    @Test
    @DisplayName("autorización negativa: un ADMIN con la cuenta SUSPENDIDA recibe 403")
    void adminSuspendidoNoPuedeConfigurar() throws Exception {
        sesionDe(UserRole.ADMIN, UserStatus.SUSPENDED);

        mockMvc.perform(put("/api/v1/admin/formaciones/ESCUELA/comunidad/SOPORTE")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prendido\": true}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(configurar);
    }

    @Test
    @DisplayName("un código de formación o de tipo que no existe es un error de quien llama (400), no un 500")
    void codigoQueNoExiste() throws Exception {
        sesionDe(UserRole.ALCHEMIST, UserStatus.ACTIVE);

        mockMvc.perform(put("/api/v1/admin/formaciones/NO_EXISTE/comunidad/SOPORTE")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prendido\": true}"))
                .andExpect(status().isBadRequest());
    }
}
