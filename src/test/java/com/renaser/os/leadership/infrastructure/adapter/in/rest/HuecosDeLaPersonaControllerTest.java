package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.services.HuecosDePersonaDePrueba;
import com.renaser.os.mentoring.api.HuecosDeHorarioFinder;
import com.renaser.os.mentoring.application.services.SemanasDeHuecosDePrueba;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasDePruebaFinder;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.FichaDeMentorFinder;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/leadership/groups/{g}/people/{p}/huecos} (spec 015): lo ve el líder de mentores, Administración y
 * Alquimista con la misma respuesta que la ruta del mentor, y nadie más. Los guardas son los REALES
 * ({@code AccesoDeLiderazgo}, {@code PersonasDelGrupo}); los otros módulos son dobles.
 */
@WebMvcTest(HuecosDeLaPersonaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({HuecosDePersonaDePrueba.class, HuecosDeLaPersonaControllerTest.RelojDePrueba.class})
class HuecosDeLaPersonaControllerTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant AHORA = Instant.parse("2026-10-28T03:00:00Z");
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalDate PRIMERA = LocalDate.of(2026, 10, 12);

    private final UserId lider = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alquimista = UserId.of(UUID.randomUUID());
    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserId mentora = UserId.of(UUID.randomUUID());
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId beto = UserId.of(UUID.randomUUID());
    private final UUID fenix = UUID.randomUUID();
    private final UUID sinMentor = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserSummaryFinder usuarios;
    @MockitoBean
    private FichaDeMentorFinder fichas;
    @MockitoBean
    private AcompanamientoFinder acompanamiento;
    @MockitoBean
    private ParticipacionProgramaFinder participaciones;
    @MockitoBean
    private CuentasDePruebaFinder pruebas;
    @MockitoBean
    private HuecosDeHorarioFinder huecos;

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojDePrueba {

        @Bean
        Clock reloj() {
            return FixedClock.at(AHORA);
        }
    }

    @BeforeEach
    void preparar() {
        reset(usuarios, fichas, acompanamiento, participaciones, pruebas, huecos);
        usuario(lider, UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        usuario(admin, UserRole.ADMIN, UserStatus.ACTIVE);
        usuario(alquimista, UserRole.ALCHEMIST, UserStatus.ACTIVE);
        usuario(soporte, UserRole.IT_SUPPORT, UserStatus.ACTIVE);
        usuario(mentora, UserRole.MENTOR, UserStatus.ACTIVE);
        usuario(ana, UserRole.TRAINEE, UserStatus.ACTIVE);
        usuario(beto, UserRole.TRAINEE, UserStatus.ACTIVE);
        GrupoAcompanado grupo = new GrupoAcompanado(fenix, "Grupo Fenix", mentora, UUID.randomUUID(),
                LIMA.getId(), 3);
        when(acompanamiento.gruposConMentorVigente(any())).thenReturn(List.of(grupo));
        when(acompanamiento.aprendicesVigentes(any(UUID.class), any(Instant.class))).thenReturn(List.of());
        when(acompanamiento.aprendicesVigentes(fenix, AHORA)).thenReturn(List.of(ana));
        when(participaciones.deParticipante(ana)).thenReturn(Optional.of(new ParticipacionPrograma(ana, true, 15,
                PRIMERA.plusDays(2), LIMA, FasePrograma.PHASE_2_DEVELOPMENT, null, mentora, UserRole.TRAINEE, false,
                true)));
        when(huecos.semanaDe(any(), any())).thenReturn(
                SemanasDeHuecosDePrueba.respuestaDelContrato(LUNES, PRIMERA, false));
    }

    private void usuario(UserId id, UserRole rol, UserStatus estado) {
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Nombre", null, rol, estado)));
    }

    private ResultActions pedir(String query, UserId actor, UUID grupo, UserId persona) throws Exception {
        return mockMvc.perform(get("/api/v1/leadership/groups/" + grupo + "/people/" + persona + "/huecos" + query)
                .header("X-Actor-Id", actor.toString()));
    }

    @Test
    @DisplayName("el lider ve los huecos de una persona de su grupo con el JSON del contrato, sin cache")
    void contrato() throws Exception {
        pedir("", lider, fenix, ana)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.semana").value("2026-10-26"))
                .andExpect(jsonPath("$.primeraSemana").value("2026-10-12"))
                .andExpect(jsonPath("$.cerrada").value(false))
                .andExpect(jsonPath("$.dias", hasSize(7)))
                .andExpect(jsonPath("$.dias[0].ventana.desde").value("05:30"))
                .andExpect(jsonPath("$.dias[0].huecos[0].minutos").value(120))
                .andExpect(jsonPath("$.resumen.huecosQueSeRepiten[0].dias[2]").value(4))
                .andExpect(jsonPath("$.resumen.noCumplidos").value(4));
        verify(huecos).semanaDe(ana, null);
    }

    @Test
    @DisplayName("ADMIN y ALCHEMIST tambien lo ven, y la semana pedida llega al calculo")
    void administracionTambien() throws Exception {
        for (UserId actor : List.of(admin, alquimista)) {
            pedir("?semana=2026-10-19", actor, fenix, ana).andExpect(status().isOk());
        }
        verify(huecos, times(2)).semanaDe(ana, LocalDate.of(2026, 10, 19));
    }

    @Test
    @DisplayName("una semana que no se puede pedir es 400; una persona que el calculo no encuentra, 404")
    void erroresDelCalculo() throws Exception {
        doThrow(new IllegalArgumentException("Esa semana todavia no llega")).when(huecos).semanaDe(any(), any());
        pedir("?semana=2026-11-02", lider, fenix, ana).andExpect(status().isBadRequest());

        doThrow(new NoSuchElementException("Persona no encontrada")).when(huecos).semanaDe(any(), any());
        pedir("", lider, fenix, ana).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("autorizacion negativa: un MENTOR recibe 403 del guard aunque el interceptor lo deje pasar (A-1)")
    void mentorRecibe403() throws Exception {
        pedir("", mentora, fenix, ana)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo MENTOR_LEAD/ADMIN/ALCHEMIST gestionan el cuerpo de mentores"));
        verifyNoInteractions(huecos);
    }

    @Test
    @DisplayName("autorizacion negativa: un TRAINEE y Soporte TI reciben 403")
    void otrosRoles() throws Exception {
        pedir("", ana, fenix, ana).andExpect(status().isForbidden());
        pedir("", soporte, fenix, ana).andExpect(status().isForbidden());
        verifyNoInteractions(huecos);
    }

    @Test
    @DisplayName("autorizacion negativa: un LIDER SUSPENDIDO recibe 403 aunque su token sea valido")
    void liderSuspendido() throws Exception {
        usuario(lider, UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);

        pedir("", lider, fenix, ana)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cuenta suspendida"));
        verifyNoInteractions(huecos);
    }

    @Test
    @DisplayName("autorizacion negativa: ADMIN y ALCHEMIST suspendidos tambien reciben 403")
    void administracionSuspendida() throws Exception {
        usuario(admin, UserRole.ADMIN, UserStatus.SUSPENDED);
        usuario(alquimista, UserRole.ALCHEMIST, UserStatus.SUSPENDED);

        pedir("", admin, fenix, ana).andExpect(status().isForbidden());
        pedir("", alquimista, fenix, ana).andExpect(status().isForbidden());
        verifyNoInteractions(huecos);
    }

    @Test
    @DisplayName("una persona que hoy no es de ese grupo, o un grupo sin mentor, es 404 y no se calcula nada")
    void personaOGrupoAjeno() throws Exception {
        pedir("", lider, fenix, beto).andExpect(status().isNotFound());
        pedir("", lider, sinMentor, ana).andExpect(status().isNotFound());
        verifyNoInteractions(huecos);
    }
}
