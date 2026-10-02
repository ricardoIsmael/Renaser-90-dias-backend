package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase.CerrarMiCuentaCommand;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase.ComoConfirmar;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase.Metodo;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;
import com.renaser.os.users.infrastructure.adapter.in.web.security.SesionWebAdapter;
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

import java.time.Instant;
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

/**
 * Yo → «Eliminar mi cuenta» (D-243): siempre la cuenta de la sesion. Pruebas de seguridad de la
 * regla 03: SUSPENDED → 403, y el cuerpo no puede elegir otra cuenta.
 */
@WebMvcTest(EliminarMiCuentaController.class)
@AutoConfigureMockMvc(addFilters = false)
class EliminarMiCuentaControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private CerrarMiCuentaUseCase cerrarMiCuenta;
    @MockitoBean
    private SesionWebAdapter sesionWeb;

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
    @DisplayName("GET dice como confirmar y cuantos dias de gracia hay")
    void comoConfirmar() throws Exception {
        UUID id = sesionDe(UserRole.TRAINEE, UserStatus.ACTIVE);
        when(cerrarMiCuenta.comoConfirmar(UserId.of(id))).thenReturn(new ComoConfirmar(Metodo.CONTRASENA, 30));

        mockMvc.perform(get("/api/v1/users/me/account-deletion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmaCon").value("CONTRASENA"))
                .andExpect(jsonPath("$.diasDeGracia").value(30));
    }

    @Test
    @DisplayName("POST cierra la cuenta de la sesion, invalida esta sesion y devuelve las fechas")
    void cerrar() throws Exception {
        UUID id = sesionDe(UserRole.TRAINEE, UserStatus.ACTIVE);
        Instant cierre = Instant.parse("2026-10-02T15:00:00Z");
        when(cerrarMiCuenta.cerrar(new CerrarMiCuentaCommand(UserId.of(id), "clave", null)))
                .thenReturn(EstadoBajaCuenta.de(cierre, cierre, 30));

        mockMvc.perform(post("/api/v1/users/me/account-deletion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasena\":\"clave\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cerradaEn").value("2026-10-02T15:00:00Z"))
                .andExpect(jsonPath("$.seBorraEl").value("2026-11-01T15:00:00Z"))
                .andExpect(jsonPath("$.diasDeGracia").value(30));
        verify(sesionWeb).cerrar(any());
    }

    @Test
    @DisplayName("el cuerpo no elige la cuenta: un userId en el cuerpo se ignora, se cierra la de la sesion")
    void noSeCierraLaDeOtro() throws Exception {
        UUID id = sesionDe(UserRole.TRAINEE, UserStatus.ACTIVE);
        UUID otro = UUID.randomUUID();
        when(cerrarMiCuenta.cerrar(any())).thenReturn(EstadoBajaCuenta.de(Instant.EPOCH, Instant.EPOCH, 30));

        mockMvc.perform(post("/api/v1/users/me/account-deletion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasena\":\"clave\",\"userId\":\"" + otro + "\",\"cuenta\":\"" + otro + "\"}"))
                .andExpect(status().isOk());
        verify(cerrarMiCuenta).cerrar(new CerrarMiCuentaCommand(UserId.of(id), "clave", null));
    }

    @Test
    @DisplayName("una cuenta SUSPENDIDA con sesion viva recibe 403 y no se toca nada")
    void suspendida403() throws Exception {
        sesionDe(UserRole.TRAINEE, UserStatus.SUSPENDED);

        mockMvc.perform(post("/api/v1/users/me/account-deletion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasena\":\"clave\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cerrarMiCuenta);
    }
}
