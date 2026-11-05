package com.renaser.os.calendar.infrastructure.adapter.in.rest.meet;

import com.renaser.os.calendar.application.ports.in.meet.CuentaDeGoogleUseCase;
import com.renaser.os.calendar.application.ports.in.meet.CuentaDeGoogleUseCase.ResultadoDeLaConexion;
import com.renaser.os.calendar.application.ports.in.meet.EstadoDeLaCuentaVista;
import com.renaser.os.calendar.application.ports.in.meet.EstadoDeLaCuentaVista.AccionVista;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.EstadoDeLaCuenta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El contrato HTTP de la cuenta de Google (spec 040, FR-010, FR-011, FR-015) y la página a la que Google devuelve al navegador. */
@WebMvcTest({CuentaDeGoogleController.class, RegresoDeGoogleController.class})
@AutoConfigureMockMvc(addFilters = false)
class CuentaDeGoogleControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CuentaDeGoogleUseCase cuentaUseCase;

    // spec 040 FR-011, SC-006
    @Test
    @DisplayName("FR-011: el estado de la cuenta no trae ningún permiso permanente ni token: ni como campo ni como valor")
    void elEstadoNoTrae_ningunSecreto() throws Exception {
        when(cuentaUseCase.estado(any())).thenReturn(new EstadoDeLaCuentaVista(true, true, "renasercorporation@gmail.com", EstadoDeLaCuenta.ACTIVA,
                Instant.parse("2026-11-05T15:00:00Z"), null, null,
                List.of(new AccionVista(Instant.parse("2026-11-05T15:00:00Z"), AccionDeAuditoriaGoogle.CONECTAR, "Kelin", true, null))));

        String cuerpo = mockMvc.perform(get("/api/v1/admin/integraciones/google").header("X-Actor-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conectada").value(true))
                .andExpect(jsonPath("$.correo").value("renasercorporation@gmail.com"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.acciones[0].accion").value("CONECTAR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo.toLowerCase()).doesNotContain("token", "secret", "refresh");
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: empezar a conectar devuelve la dirección de Google adonde mandar a la persona")
    void empezarAConectar() throws Exception {
        when(cuentaUseCase.iniciarConexion(any())).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?state=abc");

        mockMvc.perform(post("/api/v1/admin/integraciones/google/connect").header("X-Actor-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://accounts.google.com/o/oauth2/v2/auth?state=abc"));
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: desconectar responde 204 y llama al caso de uso con quien lo pide")
    void desconectar() throws Exception {
        UUID actor = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/integraciones/google").header("X-Actor-Id", actor.toString())).andExpect(status().isNoContent());

        verify(cuentaUseCase).desconectar(any());
    }

    // spec 040 FR-010, SC-006
    @Test
    @DisplayName("FR-010: al volver de Google se responde una página con la frase, sin cachear, y el código y el state no se repiten en la página")
    void elRegresoDeGoogle() throws Exception {
        when(cuentaUseCase.completarConexion(eq("el-codigo-secreto"), eq("el-state-secreto"), eq(null)))
                .thenReturn(new ResultadoDeLaConexion(true, "Cuenta conectada. Ya puedes volver a Renaser."));

        String pagina = mockMvc.perform(get("/api/v1/integraciones/google/callback").param("code", "el-codigo-secreto").param("state", "el-state-secreto"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cuenta conectada")))
                .andReturn().getResponse().getContentAsString();

        assertThat(pagina).doesNotContain("el-codigo-secreto", "el-state-secreto");
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: si Google devuelve error o falta el código, la página dice la frase del servicio y se escapa el HTML")
    void elRegresoConErrorSeEscapa() throws Exception {
        when(cuentaUseCase.completarConexion(eq(null), eq("s"), eq("access_denied")))
                .thenReturn(new ResultadoDeLaConexion(false, "No diste el permiso. <script>alert(1)</script>"));

        String pagina = mockMvc.perform(get("/api/v1/integraciones/google/callback").param("state", "s").param("error", "access_denied"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(pagina).contains("No se pudo conectar").doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
