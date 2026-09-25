package com.renaser.os.points.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemanaCerrada;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase;
import com.renaser.os.points.application.ports.in.semaforo.PausarSemaforoUseCase;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El endpoint del propio semáforo contra el interceptor de permisos real (regla 03: autorización
 * negativa y cuenta suspendida) y el formato JSON del contrato (§4.1).
 */
@WebMvcTest(MiSemaforoController.class)
@AutoConfigureMockMvc(addFilters = false)
class MiSemaforoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private ConsultarMiSemaforoUseCase consultarMiSemaforo;
    @MockitoBean
    private PausarSemaforoUseCase pausarSemaforo;

    private final UUID actorId = UUID.randomUUID();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void actor(UserRole rol, UserStatus estado) {
        when(userSummaryFinder.findById(UserId.of(actorId)))
                .thenReturn(Optional.of(new UserSummary(UserId.of(actorId), "Actor", null, rol, estado)));
    }

    private static DetalleDelSemaforo ejemplo() {
        LocalDate jueves = LocalDate.of(2026, 9, 24);
        DiaDelSemaforo medido = new DiaDelSemaforo(jueves, EstadoDiaSemaforo.MEDIDO, 75, ColorSemaforo.AMARILLO,
                9, 7, 3, 2);
        VentanaDelSemaforo vigente = new VentanaDelSemaforo(jueves.minusDays(6), jueves, new BigDecimal("78.3"),
                ColorSemaforo.AMARILLO, 6, false, List.of(medido));
        SemanaCerrada semana = new SemanaCerrada(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18),
                new BigDecimal("82.0"), ColorSemaforo.VERDE, 7, Instant.parse("2026-09-19T05:25:03Z"));
        return new DetalleDelSemaforo(true, true, ZoneId.of("America/Lima"), null, vigente, List.of(semana),
                Instant.parse("2026-09-25T05:25:03Z"));
    }

    @Test
    void elAprendizVeSuSemaforoConPalabrasYDenominadores() throws Exception {
        actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        when(consultarMiSemaforo.consultar(UserId.of(actorId), 8)).thenReturn(ejemplo());

        mockMvc.perform(get("/api/v1/me/semaforo").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplica").value(true))
                .andExpect(jsonPath("$.obligatorio").value(true))
                .andExpect(jsonPath("$.zona").value("America/Lima"))
                .andExpect(jsonPath("$.vigente.color").value("AMARILLO"))
                .andExpect(jsonPath("$.vigente.etiqueta").value("Requiere atención"))
                .andExpect(jsonPath("$.vigente.porcentaje").value(78.3))
                .andExpect(jsonPath("$.vigente.dias[0].estado").value("MEDIDO"))
                .andExpect(jsonPath("$.vigente.dias[0].etiqueta").value("Requiere atención"))
                .andExpect(jsonPath("$.vigente.dias[0].habitos.programados").value(9))
                .andExpect(jsonPath("$.vigente.dias[0].habitos.cumplidos").value(7))
                .andExpect(jsonPath("$.vigente.dias[0].objetivos.programados").value(3))
                .andExpect(jsonPath("$.semanas[0].etiqueta").value("Al día"))
                .andExpect(jsonPath("$.semanas[0].hasta").value("2026-09-18"));
    }

    @Test
    void autorizacionNegativaElAprendizNoPuedePausar() throws Exception {
        actor(UserRole.TRAINEE, UserStatus.ACTIVE);

        mockMvc.perform(put("/api/v1/me/semaforo/pausa").header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hasta\":\"2026-10-05\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/me/semaforo/pausa").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(pausarSemaforo);
    }

    @Test
    void unAprendizSuspendidoRecibe403AunqueSuTokenSeaValido() throws Exception {
        actor(UserRole.TRAINEE, UserStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/me/semaforo").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarMiSemaforo);
    }

    /** El interceptor no revisa al staff (A-1): el guard del servicio responde 403. */
    @Test
    void unMentorSuspendidoRecibe403DelServicio() throws Exception {
        actor(UserRole.MENTOR, UserStatus.SUSPENDED);
        when(consultarMiSemaforo.consultar(eq(UserId.of(actorId)), anyInt()))
                .thenThrow(new NotAuthorizedException("La cuenta esta suspendida"));

        mockMvc.perform(get("/api/v1/me/semaforo").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void elStaffPausaConFecha() throws Exception {
        actor(UserRole.MENTOR, UserStatus.ACTIVE);
        when(pausarSemaforo.pausar(any())).thenReturn(ejemplo());

        mockMvc.perform(put("/api/v1/me/semaforo/pausa").header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hasta\":\"2026-10-05\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void pausarSinFechaEsUnPedidoInvalido() throws Exception {
        actor(UserRole.MENTOR, UserStatus.ACTIVE);

        mockMvc.perform(put("/api/v1/me/semaforo/pausa").header("X-Actor-Id", actorId.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
