package com.renaser.os.rag.infrastructure.adapter.in.rest.memoria;

import com.renaser.os.rag.application.ports.in.memoria.BorrarMemoriaUseCase;
import com.renaser.os.rag.application.ports.in.memoria.ConsultarMemoriaUseCase;
import com.renaser.os.rag.application.ports.in.memoria.ConsultarMemoriaUseCase.MemoriaEnElPerfil;
import com.renaser.os.rag.domain.model.memoria.CategoriaDeRecuerdo;
import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;
import com.renaser.os.rag.domain.model.memoria.Recuerdo;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de "Lo que Renasia recuerda de ti" (D-167). Filtros apagados, como
 * {@code PropuestasRenasiaControllerTest}; que sin sesion se rechace lo prueba
 * {@link MemoriaRenasiaControllerAutenticacionTest}.
 */
@WebMvcTest(MemoriaRenasiaController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemoriaRenasiaControllerTest {

    private static final String MEMORIA = "/api/v1/renasia/memoria";
    private static final String RECUERDO = "/api/v1/renasia/memoria/recuerdos/{id}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private ConsultarMemoriaUseCase consultarUseCase;
    @MockitoBean
    private BorrarMemoriaUseCase borrarUseCase;

    private final UUID recuerdoId = UUID.randomUUID();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private UUID actor(UserStatus status) {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.of(
                new UserSummary(UserId.of(actorId), "Actor", null, UserRole.TRAINEE, status)));
        return actorId;
    }

    @Test
    @DisplayName("ve lo suyo: la categoria para agrupar, el titulo para mostrar, y si la memoria esta encendida")
    void verLoSuyo() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        var memoria = new MemoriaDeRenasia(List.of(new Recuerdo(recuerdoId, CategoriaDeRecuerdo.PREFERENCIAS_DE_TRATO,
                "Prefiere respuestas cortas", Instant.parse("2026-09-20T15:00:00Z"))), Optional.empty(), Instant.EPOCH);
        when(consultarUseCase.paraElPerfil(UserId.of(actorId))).thenReturn(new MemoriaEnElPerfil(memoria, true));

        mockMvc.perform(get(MEMORIA).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.recuerdos[0].id").value(recuerdoId.toString()))
                .andExpect(jsonPath("$.recuerdos[0].categoria").value("PREFERENCIAS_DE_TRATO"))
                .andExpect(jsonPath("$.recuerdos[0].titulo").value("Como prefieres que te acompañe"))
                .andExpect(jsonPath("$.recuerdos[0].texto").value("Prefiere respuestas cortas"))
                .andExpect(jsonPath("$.recuerdos[0].creadoEn").doesNotExist())
                .andExpect(jsonPath("$.resumen").value(nullValue()));
    }

    @Test
    @DisplayName("olvidar un recuerdo propio es 204; uno que no existe o es ajeno, 404")
    void olvidarUno() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);
        UUID ajeno = UUID.randomUUID();
        doThrow(new NoSuchElementException("Ese recuerdo no existe"))
                .when(borrarUseCase).borrarRecuerdo(UserId.of(actorId), ajeno);

        mockMvc.perform(delete(RECUERDO, recuerdoId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(RECUERDO, ajeno).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNotFound());

        verify(borrarUseCase).borrarRecuerdo(UserId.of(actorId), recuerdoId);
    }

    @Test
    @DisplayName("olvidar todo es 204")
    void olvidarTodo() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);

        mockMvc.perform(delete(MEMORIA).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNoContent());

        verify(borrarUseCase).borrarTodo(UserId.of(actorId));
    }

    @Test
    @DisplayName("autorizacion negativa: un usuario SUSPENDIDO recibe 403 y los casos de uso ni se llaman")
    void suspendidoEs403() throws Exception {
        UUID actorId = actor(UserStatus.SUSPENDED);

        mockMvc.perform(get(MEMORIA).header("X-Actor-Id", actorId.toString())).andExpect(status().isForbidden());
        mockMvc.perform(delete(RECUERDO, recuerdoId).header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(MEMORIA).header("X-Actor-Id", actorId.toString())).andExpect(status().isForbidden());

        verifyNoInteractions(consultarUseCase, borrarUseCase);
    }

    @Test
    @DisplayName("un id que no es UUID es 400, sin llegar al caso de uso")
    void idInvalidoEs400() throws Exception {
        UUID actorId = actor(UserStatus.ACTIVE);

        mockMvc.perform(delete(RECUERDO, "no-es-un-uuid").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(borrarUseCase);
    }
}
