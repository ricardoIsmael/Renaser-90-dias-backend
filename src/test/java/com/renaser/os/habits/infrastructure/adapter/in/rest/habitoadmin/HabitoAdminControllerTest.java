package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase.ActualizarHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.CambiarActivoHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarCatalogoAdminUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.EliminarHabitoUseCase;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-263: editar un hábito del catálogo no puede apagar en silencio {@code mandatoryOnIntoxication}. Desde
 * D-169, apagarla vuelve opcional el post de la comunidad en los días de intoxicación. Y la respuesta la
 * tiene que devolver, para que un formulario la pueda mandar de vuelta.
 */
@WebMvcTest(HabitoAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class HabitoAdminControllerTest {

    /** Lo que mandaría un formulario que nunca conoció la bandera: no la trae. */
    private static final String SIN_BANDERA = """
            {"description": "Escribe solo lo bueno", "category": "BODY", "evidenceRequirement": "OPTIONAL",
             "isOptional": false}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSummaryFinder userSummaryFinder;
    @MockitoBean
    private ConsultarCatalogoAdminUseCase consultarCatalogo;
    @MockitoBean
    private CrearHabitoUseCase crearHabito;
    @MockitoBean
    private ActualizarHabitoUseCase actualizarHabito;
    @MockitoBean
    private CambiarActivoHabitoUseCase cambiarActivo;
    @MockitoBean
    private EliminarHabitoUseCase eliminarHabito;

    private final UUID admin = UUID.randomUUID();
    private final UUID post = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        when(userSummaryFinder.findById(UserId.of(admin))).thenReturn(Optional.of(
                new UserSummary(UserId.of(admin), "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(actualizarHabito.actualizar(any())).thenReturn(postDeComunidad());
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("un pedido sin la bandera la conserva: nunca se apaga en silencio")
    void sinBanderaLaConserva() throws Exception {
        editar(SIN_BANDERA).andExpect(status().isOk());

        assertThat(comandoEnviado().conservaObligatorioEnIntoxicacion()).isTrue();
    }

    @Test
    @DisplayName("false explícito la apaga: es una decisión, no un olvido")
    void falseExplicitoLaApaga() throws Exception {
        editar(conBandera("false")).andExpect(status().isOk());

        ActualizarHabitoCommand comando = comandoEnviado();
        assertThat(comando.conservaObligatorioEnIntoxicacion()).isFalse();
        assertThat(comando.detalles().obligatorioEnIntoxicacion()).isFalse();
    }

    @Test
    @DisplayName("true explícito la prende")
    void trueExplicitoLaPrende() throws Exception {
        editar(conBandera("true")).andExpect(status().isOk());

        ActualizarHabitoCommand comando = comandoEnviado();
        assertThat(comando.conservaObligatorioEnIntoxicacion()).isFalse();
        assertThat(comando.detalles().obligatorioEnIntoxicacion()).isTrue();
    }

    @Test
    @DisplayName("la respuesta devuelve la bandera, para que un formulario la pueda mandar de vuelta")
    void laRespuestaLaDevuelve() throws Exception {
        editar(SIN_BANDERA)
                .andExpect(jsonPath("$.mandatoryOnIntoxication").value(true))
                .andExpect(jsonPath("$.isOptional").value(false));
    }

    private ResultActions editar(String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/habits/" + post)
                .header("X-Actor-Id", admin.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private static String conBandera(String valor) {
        return SIN_BANDERA.replace("\"isOptional\": false", "\"isOptional\": false, \"mandatoryOnIntoxication\": " + valor);
    }

    private ActualizarHabitoCommand comandoEnviado() {
        ArgumentCaptor<ActualizarHabitoCommand> captor = ArgumentCaptor.forClass(ActualizarHabitoCommand.class);
        verify(actualizarHabito).actualizar(captor.capture());
        return captor.getValue();
    }

    private Habito postDeComunidad() {
        return Habito.crearDeSistema(HabitoId.of(post), "POST DIARIO EN COMUNIDAD", TipoHabito.CHECKBOX,
                new DetallesHabito("Escribe solo lo bueno", "CUERPO", ExigenciaEvidencia.OPCIONAL, false, true),
                Instant.parse("2026-09-25T20:00:00Z"));
    }
}
