package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.contrato;

import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.FirmarContratoUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.FirmarContratoUseCase.FirmarContratoCommand;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ObtenerUrlFirmaContratoUseCase;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFaseId;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-216 (TRN-21 del e2e): {@code POST /api/v1/phase-contracts} acepta un cuerpo OPCIONAL con la fase
 * que la persona cree firmar. Sin cuerpo sigue siendo el pedido de siempre (el APK no se actualiza por
 * aire: el cambio tiene que ser aditivo).
 */
@WebMvcTest(ContratoController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContratoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ConsultarContratosPendientesUseCase pendientes;
    @MockitoBean ConsultarContratosUseCase consultar;
    @MockitoBean FirmarContratoUseCase firmar;
    @MockitoBean ObtenerUrlFirmaContratoUseCase urlFirma;
    @MockitoBean UserSummaryFinder users;

    private final UserId actor = UserId.of(UUID.randomUUID());

    @BeforeEach
    void aprendizActivo() {
        when(users.findById(actor)).thenReturn(Optional.of(
                new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private ContratoFase firmado(FasePrograma fase) {
        return ContratoFase.firmar(ContratoFaseId.of(UUID.randomUUID()), actor, fase, 84,
                FixedClock.at(Instant.parse("2026-09-27T19:16:58Z")));
    }

    @Test
    void sinCuerpoEsElPedidoDeSiempre() throws Exception {
        when(firmar.firmar(any())).thenReturn(firmado(FasePrograma.FASE_2_DESARROLLO));

        mvc.perform(post("/api/v1/phase-contracts").header("X-Actor-Id", actor.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("FASE_2_DESARROLLO"));

        verify(firmar).firmar(new FirmarContratoCommand(actor, null));
    }

    @Test
    void conLaFaseLaPasaAlCasoDeUso() throws Exception {
        when(firmar.firmar(any())).thenReturn(firmado(FasePrograma.FASE_4_ASCENSION));

        mvc.perform(post("/api/v1/phase-contracts").header("X-Actor-Id", actor.toString())
                        .contentType("application/json").content("""
                                {"phase":"FASE_4_ASCENSION"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("FASE_4_ASCENSION"));

        verify(firmar).firmar(new FirmarContratoCommand(actor, FasePrograma.FASE_4_ASCENSION));
    }

    @Test
    void unCuerpoVacioTambienEsElPedidoDeSiempre() throws Exception {
        when(firmar.firmar(any())).thenReturn(firmado(FasePrograma.FASE_2_DESARROLLO));

        mvc.perform(post("/api/v1/phase-contracts").header("X-Actor-Id", actor.toString())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isCreated());

        verify(firmar).firmar(new FirmarContratoCommand(actor, null));
    }

    @Test
    void unaFaseQueNoExisteEsUn400SinFirmar() throws Exception {
        mvc.perform(post("/api/v1/phase-contracts").header("X-Actor-Id", actor.toString())
                        .contentType("application/json").content("""
                                {"phase":"FASE_9"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(firmar);
    }
}
