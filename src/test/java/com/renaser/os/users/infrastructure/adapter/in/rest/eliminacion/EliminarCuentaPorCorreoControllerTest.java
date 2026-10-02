package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarCuentaPorCorreoUseCase;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pagina web publica (D-243): sin sesion; 202 siempre al pedir el codigo. */
@WebMvcTest(EliminarCuentaPorCorreoController.class)
@AutoConfigureMockMvc(addFilters = false)
class EliminarCuentaPorCorreoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CerrarCuentaPorCorreoUseCase cerrarPorCorreo;

    @Test
    @DisplayName("pedir el codigo responde 202 sin cuerpo")
    void pedirCodigo() throws Exception {
        mockMvc.perform(post("/api/v1/account-deletion/request-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@renaser.dev\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("confirmar con el codigo correcto devuelve las fechas; uno equivocado, 400")
    void confirmar() throws Exception {
        Instant cierre = Instant.parse("2026-10-02T15:00:00Z");
        when(cerrarPorCorreo.confirmar(eq("ana@renaser.dev"), eq("123456"), any()))
                .thenReturn(EstadoBajaCuenta.de(cierre, cierre, 30));
        when(cerrarPorCorreo.confirmar(eq("ana@renaser.dev"), eq("000000"), any()))
                .thenThrow(new CodigoVerificacionInvalidoException());

        mockMvc.perform(post("/api/v1/account-deletion/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@renaser.dev\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seBorraEl").value("2026-11-01T15:00:00Z"));
        mockMvc.perform(post("/api/v1/account-deletion/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@renaser.dev\",\"codigo\":\"000000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sin correo: 400")
    void sinCorreo() throws Exception {
        mockMvc.perform(post("/api/v1/account-deletion/request-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
