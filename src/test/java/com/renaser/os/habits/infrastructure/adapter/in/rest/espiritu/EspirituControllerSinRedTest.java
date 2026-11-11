package com.renaser.os.habits.infrastructure.adapter.in.rest.espiritu;

import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituSinRedUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituSinRedUseCase.ResultadoEntregaSinRed;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.ResultadoEntrega;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/spirit-audio/submit} con las tres cabeceras de lo hecho sin red (spec 048, issue #186): con {@code X-Id-Cliente} y
 * {@code X-Hecho-En} va por la entrega con la hora real; sin ellas, exactamente por lo de siempre y con la misma respuesta; lo que no
 * se aplica es un 409 con {@code codigo}.
 */
@WebMvcTest(EspirituController.class)
@AutoConfigureMockMvc(addFilters = false)
class EspirituControllerSinRedTest {

    private static final String ID = "5f0c1f4e-2d0a-4b7e-9d77-0c9f3f6b1a10";
    private static final String HECHO = "2026-11-09T16:00:00.000Z";
    private static final String CUERPO = "{\"day\":3,\"summaryText\":\"Entendí que la calma se entrena\"}";

    @Autowired MockMvc mvc;
    @MockitoBean ConsultarEstadoEspirituUseCase consultar;
    @MockitoBean EntregarResumenEspirituUseCase entregar;
    @MockitoBean EntregarResumenEspirituSinRedUseCase entregarSinRed;
    @MockitoBean UserSummaryFinder users;
    private final UserId actor = UserId.of(UUID.randomUUID());

    @BeforeEach
    void actorActivo() {
        when(users.findById(actor)).thenReturn(Optional.of(new UserSummary(actor, "Fixture", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(entregar.entregar(any())).thenReturn(new ResultadoEntrega(false));
        when(entregarSinRed.entregar(any(), any())).thenReturn(new ResultadoEntregaSinRed(true, null));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    /** El pedido con o sin cabeceras ({@code null} = esa cabecera no viaja). */
    private ResultActions enviar(String id, String hechoEn) throws Exception {
        var pedido = post("/api/v1/spirit-audio/submit").header("X-Actor-Id", actor.toString())
                .contentType("application/json").content(CUERPO);
        if (id != null) {
            pedido.header("X-Id-Cliente", id).header("X-Origen-De-La-Hora", "RELOJ_DEL_SISTEMA");
        }
        if (hechoEn != null) {
            pedido.header("X-Hecho-En", hechoEn);
        }
        return mvc.perform(pedido);
    }

    @Test
    @DisplayName("con las cabeceras va por la entrega sin red con su hora real, y responde si contó a tiempo")
    void conCabeceras() throws Exception { // spec 048 FR-010
        enviar(ID, HECHO).andExpect(status().isOk()).andExpect(jsonPath("$.onTime").value(true))
                .andExpect(jsonPath("$.motivoSinPuntos").doesNotExist());

        ArgumentCaptor<HechoSinRed> hecho = ArgumentCaptor.forClass(HechoSinRed.class);
        verify(entregarSinRed).entregar(any(), hecho.capture());
        assertThat(hecho.getValue().idCliente()).isEqualTo(UUID.fromString(ID));
        assertThat(hecho.getValue().hechoEn()).isEqualTo(Instant.parse(HECHO));
        assertThat(hecho.getValue().origenDeLaHora()).isEqualTo(OrigenDeLaHora.RELOJ_DEL_SISTEMA);
        verifyNoInteractions(entregar);
    }

    @Test
    @DisplayName("sin las cabeceras (o con solo una) responde exactamente lo de hoy: por el caso de uso de siempre y solo {\"onTime\"}")
    void sinCabeceras() throws Exception { // spec 048 FR-008
        enviar(null, null).andExpect(status().isOk()).andExpect(content().json("{\"onTime\":false}", true));
        enviar(ID, null).andExpect(status().isOk());
        enviar(null, HECHO).andExpect(status().isOk());

        verify(entregar, times(3)).entregar(any());
        verifyNoInteractions(entregarSinRed);
    }

    @Test
    @DisplayName("si el hábito quedó sin puntos, la respuesta lo dice con motivoSinPuntos")
    void sinPuntosSeDice() throws Exception { // spec 048 FR-015
        when(entregarSinRed.entregar(any(), any())).thenReturn(new ResultadoEntregaSinRed(true, "LLEGO_DESPUES_DE_48_H"));

        enviar(ID, HECHO).andExpect(status().isOk()).andExpect(jsonPath("$.onTime").value(true))
                .andExpect(jsonPath("$.motivoSinPuntos").value("LLEGO_DESPUES_DE_48_H"));
    }

    @Test
    @DisplayName("lo que no se aplica es un 409 con su codigo y su mensaje en tuteo (YA_ESTABA_HECHO, FUERA_DE_SU_DIA, RECURSO_YA_NO_EXISTE)")
    void noAplicadoEsUn409ConCodigo() throws Exception { // spec 048 FR-016
        doThrow(new NoAplicadoSinRedException(CodigoNoAplicado.YA_ESTABA_HECHO)).when(entregarSinRed).entregar(any(), any());
        enviar(ID, HECHO).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("YA_ESTABA_HECHO"))
                .andExpect(jsonPath("$.message").value("Esto ya estaba hecho en tu cuenta. Se quedó lo que ya había."));

        doThrow(new NoAplicadoSinRedException(CodigoNoAplicado.FUERA_DE_SU_DIA)).when(entregarSinRed).entregar(any(), any());
        enviar(UUID.randomUUID().toString(), HECHO).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_SU_DIA"))
                .andExpect(jsonPath("$.message").value("Esto era de un día que ya terminó y no se pudo aplicar."));

        doThrow(new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE)).when(entregarSinRed).entregar(any(), any());
        enviar(UUID.randomUUID().toString(), HECHO).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RECURSO_YA_NO_EXISTE"));
    }

    @Test
    @DisplayName("una cuenta suspendida es 403 también con las cabeceras")
    void suspendidaEs403() throws Exception { // spec 048 FR-016
        doThrow(new NotAuthorizedException("Cuenta suspendida", NotAuthorizedException.CUENTA_SUSPENDIDA)).when(entregarSinRed)
                .entregar(any(), any());

        enviar(ID, HECHO).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CUENTA_SUSPENDIDA"));
    }

    @Test
    @DisplayName("cabeceras mal hechas son un 400 de siempre y no llegan a ningún caso de uso")
    void cabecerasMalHechas() throws Exception {
        enviar("no-es-uuid", HECHO).andExpect(status().isBadRequest());
        enviar(ID, "ayer").andExpect(status().isBadRequest());

        verifyNoInteractions(entregar, entregarSinRed);
    }
}
