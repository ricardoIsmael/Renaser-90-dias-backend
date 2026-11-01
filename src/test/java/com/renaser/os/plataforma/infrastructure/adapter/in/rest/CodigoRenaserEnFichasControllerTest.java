package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.CambiarVisibilidadDelCodigoRenaserUseCase;
import com.renaser.os.plataforma.application.ports.in.ConsultarVisibilidadDelCodigoRenaserUseCase;
import com.renaser.os.plataforma.application.ports.in.VisibilidadDelCodigoRenaser;
import com.renaser.os.plataforma.application.ports.in.VisibilidadDelCodigoRenaser.UltimoCambio;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET/PUT /api/v1/admin/codigo-renaser-en-fichas} (spec 034): la forma del JSON, que no se cachee, el cuerpo inválido y
 * que el rechazo del caso de uso sea un 403. Quién puede de verdad (la guardia por rol y por cuenta suspendida) lo prueba
 * {@code VisibilidadDelCodigoRenaserServiceTest} y el escenario de punta a punta, {@code CodigoRenaserEnFichasIT}.
 */
@WebMvcTest(CodigoRenaserEnFichasController.class)
@AutoConfigureMockMvc(addFilters = false)
class CodigoRenaserEnFichasControllerTest {

    private static final String RUTA = "/api/v1/admin/codigo-renaser-en-fichas";

    private final UserId admin = UserId.of(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ConsultarVisibilidadDelCodigoRenaserUseCase consultar;
    @MockitoBean
    private CambiarVisibilidadDelCodigoRenaserUseCase cambiar;

    @BeforeEach
    void preparar() {
        reset(consultar, cambiar);
    }

    private VisibilidadDelCodigoRenaser oculto() {
        return new VisibilidadDelCodigoRenaser(false, false, Map.of(UserRole.MENTOR, false, UserRole.TRAINEE_GUIDE, false,
                UserRole.MENTOR_LEAD, false), null);
    }

    @Test
    @DisplayName("GET: el estado con sus tres roles, sin último cambio de fábrica, y sin caché")
    void leer() throws Exception {
        when(consultar.consultar(admin)).thenReturn(oculto());

        mockMvc.perform(get(RUTA).header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.mixto").value(false))
                .andExpect(jsonPath("$.porRol.MENTOR").value(false))
                .andExpect(jsonPath("$.porRol.TRAINEE_GUIDE").value(false))
                .andExpect(jsonPath("$.porRol.MENTOR_LEAD").value(false))
                .andExpect(jsonPath("$.ultimoCambio").value(nullValue()));
    }

    @Test
    @DisplayName("PUT: cambia con {visible} y responde el estado con quién y cuándo lo cambió")
    void cambiarElEstado() throws Exception {
        var visible = new VisibilidadDelCodigoRenaser(true, false, Map.of(UserRole.MENTOR, true, UserRole.TRAINEE_GUIDE, true,
                UserRole.MENTOR_LEAD, true), new UltimoCambio(admin, "Ana Pérez", Instant.parse("2026-10-31T23:40:12Z")));
        when(cambiar.cambiar(admin, true)).thenReturn(visible);

        mockMvc.perform(put(RUTA).header("X-Actor-Id", admin.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\": true}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.ultimoCambio.porId").value(admin.toString()))
                .andExpect(jsonPath("$.ultimoCambio.porNombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.ultimoCambio.en").value("2026-10-31T23:40:12Z"));
        verify(cambiar).cambiar(admin, true);
    }

    @Test
    @DisplayName("PUT sin «visible», con un valor que no es booleano o con otro campo solo: 400 y no se cambia nada")
    void cuerpoInvalido() throws Exception {
        for (String cuerpo : new String[]{"{}", "{\"visible\": null}", "{\"visible\": \"si\"}", "{\"role\": \"ADMIN\"}"}) {
            mockMvc.perform(put(RUTA).header("X-Actor-Id", admin.toString()).contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(cambiar);
    }

    @Test
    @DisplayName("el rechazo del caso de uso (rol sin permiso, cuenta suspendida) es un 403, al leer y al cambiar")
    void rechazo() throws Exception {
        when(consultar.consultar(any())).thenThrow(new NotAuthorizedException("Solo Administración"));
        when(cambiar.cambiar(any(), anyBoolean())).thenThrow(new NotAuthorizedException("Solo Administración"));

        mockMvc.perform(get(RUTA).header("X-Actor-Id", UUID.randomUUID().toString())).andExpect(status().isForbidden());
        mockMvc.perform(put(RUTA).header("X-Actor-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\": true}"))
                .andExpect(status().isForbidden());
    }
}
