package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.shared.web.SecurityConfig;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.CheckAccountRequestStatusUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ConsultarEmailRegistradoUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.DeleteAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ListAccountRequestsUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.RejectAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.VerificarDominioEmailUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La bandeja de solicitudes exige sesion; el alta no.
 *
 * <p>Hasta el 2026-09-11 todo {@code /api/v1/account-requests/**} estaba en {@code permitAll()}.
 * Como {@code ActorAutenticadoArgumentResolver} cae al header {@code X-Actor-Id} cuando no hay
 * sesion, y los UUID de usuario viajan en respuestas normales de la API —el propio login devuelve
 * el {@code id}—, bastaba el UUID de un administrador para listar, aprobar, rechazar y borrar
 * solicitudes SIN credencial alguna. Comprobado contra el backend local ese dia: un
 * {@code POST /{id}/approve} con solo el header devolvia 204 y dejaba la cuenta creada y ACTIVA.
 *
 * <p>El caso dificil es que {@code POST /account-requests} (pedir cuenta, publico) y
 * {@code GET /account-requests} (la bandeja, de ADMIN) comparten ruta: separarlos exige matchers
 * POR METODO. Estas pruebas fijan las dos mitades, porque cerrar de mas rompe el registro y
 * cerrar de menos reabre el agujero.
 */
@WebMvcTest(AccountRequestController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "renaser.web.cors.origenes=http://localhost:8081")
class AccountRequestControllerAutenticacionTest {

    private static final String HEADER_ACTOR_ID = "X-Actor-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitAccountRequestUseCase submitUseCase;
    @MockitoBean
    private ApproveAccountRequestUseCase approveUseCase;
    @MockitoBean
    private RejectAccountRequestUseCase rejectUseCase;
    @MockitoBean
    private ListAccountRequestsUseCase listUseCase;
    @MockitoBean
    private DeleteAccountRequestUseCase deleteUseCase;
    @MockitoBean
    private CheckAccountRequestStatusUseCase checkStatusUseCase;
    @MockitoBean
    private ConsultarEmailRegistradoUseCase consultarEmailRegistradoUseCase;
    @MockitoBean
    private VerificarDominioEmailUseCase verificarDominioEmailUseCase;

    @Test
    @DisplayName("listar la bandeja sin sesion se rechaza aunque venga el header de actor")
    void listarSinSesionEsRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/account-requests")
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(listUseCase);
    }

    @Test
    @DisplayName("aprobar sin sesion se rechaza aunque venga el header de actor")
    void aprobarSinSesionEsRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/account-requests/" + UUID.randomUUID() + "/approve")
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(approveUseCase);
    }

    @Test
    @DisplayName("rechazar sin sesion se rechaza aunque venga el header de actor")
    void rechazarSinSesionEsRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/account-requests/" + UUID.randomUUID() + "/reject")
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rejectUseCase);
    }

    @Test
    @DisplayName("borrar sin sesion se rechaza aunque venga el header de actor")
    void borrarSinSesionEsRechazado() throws Exception {
        mockMvc.perform(delete("/api/v1/account-requests/" + UUID.randomUUID())
                        .header(HEADER_ACTOR_ID, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deleteUseCase);
    }

    @Test
    @DisplayName("pedir cuenta SIGUE siendo publico: comparte ruta con la bandeja pero no metodo")
    void pedirCuentaSigueSiendoPublico() throws Exception {
        mockMvc.perform(post("/api/v1/account-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * No se afirma un codigo concreto: con el caso de uso mockeado la respuesta depende del mock,
     * no de la seguridad. Lo que esta prueba fija es lo unico que importa aca — que la cadena de
     * seguridad NO la corte, que es como se veria si el matcher por metodo se escribiera de mas.
     */
    @Test
    @DisplayName("consultar el estado de la propia solicitud SIGUE siendo publico")
    void consultarEstadoSigueSiendoPublico() throws Exception {
        given(checkStatusUseCase.consultar(any()))
                .willReturn(new CheckAccountRequestStatusUseCase.AccountRequestStatusView(
                        AccountRequestStatus.PENDING, null));

        mockMvc.perform(get("/api/v1/account-requests/" + UUID.randomUUID() + "/status"))
                .andExpect(status().isOk());
    }
}
