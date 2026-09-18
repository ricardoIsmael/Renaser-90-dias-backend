package com.renaser.os.shared.web;

import com.renaser.os.community.application.ports.in.celula.ConsultarMisCelulasUseCase;
import com.renaser.os.community.infrastructure.adapter.in.rest.celula.MisCelulasController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cerrar una ruta no puede romperle la app a quien SI tiene sesion.
 *
 * <p>El 2026-09-18 se agregaron a {@code SecurityConfig} tres rutas que estaban fuera del filtro:
 * {@code /api/v1/me/cells}, {@code /api/v1/participants/**} y {@code /api/v1/auth/social/link}. La
 * pregunta del dueño antes de desplegar fue la correcta: <i>¿esto no afecta al usuario?</i>
 *
 * <p>Esta clase responde las DOS mitades, que es lo que hace que la respuesta valga:
 * <ul>
 *   <li>sin sesion, con el header {@code X-Actor-Id} que antes alcanzaba → <b>403</b>, el agujero
 *       cerrado;</li>
 *   <li>con sesion → <b>200</b>, la app de siempre sigue funcionando.</li>
 * </ul>
 *
 * <p>Los filtros estan ACTIVOS ({@code @Import(SecurityConfig.class)} sin
 * {@code addFilters = false}): se ejercita la cadena real, no el interceptor solo. Es el mismo
 * patron de {@code AccountRequestControllerAutenticacionTest}, que hasta ahora solo cubria la
 * mitad negativa.
 */
@WebMvcTest(MisCelulasController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class RutasCerradasNoRompenAlUsuarioTest {

    private static final String HEADER_ACTOR_ID = "X-Actor-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarMisCelulasUseCase consultarMisCelulas;

    @Test
    @DisplayName("/me/cells SIN sesion es 403, aunque mande X-Actor-Id (el agujero cerrado)")
    void sinSesionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/me/cells").header(HEADER_ACTOR_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/me/cells CON sesion sigue respondiendo 200: al usuario no se le rompio nada")
    void conSesionSigueFuncionando() throws Exception {
        UUID actor = UUID.randomUUID();
        given(consultarMisCelulas.misCelulas(any())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/me/cells").with(autenticadoComo(actor)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Los integrantes de un grupo tampoco se abren sin sesion")
    void integrantesSinSesionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/me/cells/" + UUID.randomUUID() + "/members")
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    /** Un actor autenticado tal como lo deja la sesion real: el principal es el UUID. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor autenticadoComo(UUID actor) {
        return authentication(new UsernamePasswordAuthenticationToken(actor.toString(), "n/a", List.of()));
    }
}
