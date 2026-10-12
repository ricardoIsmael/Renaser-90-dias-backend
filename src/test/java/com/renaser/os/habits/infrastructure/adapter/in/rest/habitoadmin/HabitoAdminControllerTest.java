package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase.ActualizarHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.CambiarActivoHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarCatalogoAdminUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarRolesElegiblesUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarRolesElegiblesUseCase.RolElegible;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase.CrearHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.HabitoDelCatalogo;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.audiencia.VersionDeAudiencia;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @MockitoBean
    private ConsultarRolesElegiblesUseCase rolesElegibles;

    private final UUID admin = UUID.randomUUID();
    private final UUID post = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        when(userSummaryFinder.findById(UserId.of(admin))).thenReturn(Optional.of(
                new UserSummary(UserId.of(admin), "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        when(actualizarHabito.actualizar(any())).thenReturn(HabitoDelCatalogo.sinAudiencia(postDeComunidad()));
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
    // ── Spec 001 (D-264): audiencia por rol ────────────────────────────────

    @Test
    @DisplayName("editar sin targetRoles no toca la audiencia; con lista, la manda; [] es para todos")
    void targetRolesAlEditar() throws Exception {
        editar(SIN_BANDERA).andExpect(status().isOk());
        assertThat(comandoEnviado().audiencia()).isNull();
    }

    @Test
    @DisplayName("editar con targetRoles manda esa audiencia")
    void targetRolesConLista() throws Exception {
        editar(SIN_BANDERA.replace("\"isOptional\": false", "\"isOptional\": false, \"targetRoles\": [\"MENTOR\"]"))
                .andExpect(status().isOk());
        assertThat(comandoEnviado().audiencia()).isEqualTo(Audiencia.de(Set.of(CodigoDeRol.of("MENTOR"))));
    }

    @Test
    @DisplayName("un código de rol mal formado es 400")
    void codigoMalFormadoEs400() throws Exception {
        editar(SIN_BANDERA.replace("\"isOptional\": false", "\"isOptional\": false, \"targetRoles\": [\"mentor\"]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("crear sin targetRoles es para todos")
    void crearSinTargetRolesEsParaTodos() throws Exception {
        when(crearHabito.crear(any())).thenReturn(HabitoDelCatalogo.sinAudiencia(postDeComunidad()));
        mockMvc.perform(post("/api/v1/admin/habits").header("X-Actor-Id", admin.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Nuevo", "habitType": "CHECKBOX", "category": "BODY",
                                 "evidenceRequirement": "OPTIONAL", "isOptional": false,
                                 "mandatoryOnIntoxication": false}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetRoles").isEmpty());
        ArgumentCaptor<CrearHabitoCommand> captor = ArgumentCaptor.forClass(CrearHabitoCommand.class);
        verify(crearHabito).crear(captor.capture());
        assertThat(captor.getValue().audiencia().paraTodos()).isTrue();
    }

    @Test
    @DisplayName("la respuesta trae la audiencia guardada, desde cuándo y quién la cambió")
    void respuestaConAudiencia() throws Exception {
        Instant cuando = Instant.parse("2026-10-12T15:00:00Z");
        Habito habito = postDeComunidad();
        when(actualizarHabito.actualizar(any())).thenReturn(new HabitoDelCatalogo(habito, Optional.of(
                new VersionDeAudiencia(habito.id(), Audiencia.de(Set.of(CodigoDeRol.of("MENTOR"),
                        CodigoDeRol.of("MENTOR_LEAD"))), cuando, UserId.of(admin), cuando)), "Ana Pérez", true));

        editar(SIN_BANDERA)
                .andExpect(jsonPath("$.targetRoles[0]").value("MENTOR"))
                .andExpect(jsonPath("$.targetRoles[1]").value("MENTOR_LEAD"))
                .andExpect(jsonPath("$.targetRolesEffectiveFrom").value("2026-10-12T15:00:00Z"))
                .andExpect(jsonPath("$.audienceChangedBy.name").value("Ana Pérez"))
                .andExpect(jsonPath("$.isDeactivatable").exists())
                .andExpect(jsonPath("$.targetRolesStartTomorrow").value(true));
    }

    @Test
    @DisplayName("GET /roles devuelve código y nombre")
    void rolesElegibles() throws Exception {
        when(rolesElegibles.rolesElegibles(UserId.of(admin))).thenReturn(List.of(new RolElegible("TRAINEE", "Aprendiz")));

        mockMvc.perform(get("/api/v1/admin/habits/roles").header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("TRAINEE"))
                .andExpect(jsonPath("$[0].name").value("Aprendiz"));
    }

    @Test
    @DisplayName("GET ?role= pasa el filtro al caso de uso")
    void listarPorRol() throws Exception {
        when(consultarCatalogo.listar(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/habits").param("role", "MENTOR").header("X-Actor-Id", admin.toString()))
                .andExpect(status().isOk());
        verify(consultarCatalogo).listar(UserId.of(admin), CodigoDeRol.of("MENTOR"));
    }

    @Test
    @DisplayName("un aprendiz recibe 403 en los endpoints del catálogo, también en /roles")
    void aprendizRecibe403() throws Exception {
        UUID aprendiz = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(aprendiz))).thenReturn(Optional.of(
                new UserSummary(UserId.of(aprendiz), "Aprendiz", null, UserRole.TRAINEE, UserStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/admin/habits/roles").header("X-Actor-Id", aprendiz.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/habits").header("X-Actor-Id", aprendiz.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("una cuenta suspendida recibe 403 aunque sea admin")
    void suspendidoRecibe403() throws Exception {
        UUID suspendido = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(suspendido))).thenReturn(Optional.of(
                new UserSummary(UserId.of(suspendido), "Admin", null, UserRole.ADMIN, UserStatus.SUSPENDED)));

        mockMvc.perform(get("/api/v1/admin/habits/roles").header("X-Actor-Id", suspendido.toString()))
                .andExpect(status().isForbidden());
    }
}
