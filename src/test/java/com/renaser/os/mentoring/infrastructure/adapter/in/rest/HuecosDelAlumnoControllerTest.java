package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.mentoring.api.HuecosDeHorarioFinder;
import com.renaser.os.mentoring.application.services.HuecosDelAlumnoService;
import com.renaser.os.mentoring.application.services.SemanasDeHuecosDePrueba;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/mentor/groups/{g}/learners/{u}/huecos} (spec 015): el contrato, que nada se cachee y la
 * autorización negativa. El permiso declarado es {@code USE_APP}, que tienen todos los roles, así que quien protege es el
 * guard del servicio REAL ({@link HuecosDelAlumnoService}); los otros módulos son dobles.
 */
@WebMvcTest(HuecosDelAlumnoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({HuecosDelAlumnoService.class, HuecosDelAlumnoControllerTest.RelojDePrueba.class})
class HuecosDelAlumnoControllerTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalDate PRIMERA = LocalDate.of(2026, 10, 12);

    private final UserId mentora = UserId.of(UUID.randomUUID());
    private final UserId otroMentor = UserId.of(UUID.randomUUID());
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId beto = UserId.of(UUID.randomUUID());
    private final UUID fenix = UUID.randomUUID();
    private final UUID aurora = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserSummaryFinder usuarios;
    @MockitoBean
    private AcompanamientoFinder acompanamiento;
    @MockitoBean
    private HuecosDeHorarioFinder huecos;

    /** Miércoles 28 de octubre de 2026, 03:00 UTC: todavía el martes 27 a las 22:00 en Lima. */
    @TestConfiguration(proxyBeanMethods = false)
    static class RelojDePrueba {

        @Bean
        Clock reloj() {
            return FixedClock.at(Instant.parse("2026-10-28T03:00:00Z"));
        }
    }

    @BeforeEach
    void preparar() {
        reset(usuarios, acompanamiento, huecos);
        usuario(mentora, UserRole.MENTOR, UserStatus.ACTIVE);
        usuario(otroMentor, UserRole.MENTOR, UserStatus.ACTIVE);
        usuario(ana, UserRole.TRAINEE, UserStatus.ACTIVE);
        usuario(beto, UserRole.TRAINEE, UserStatus.ACTIVE);
        when(acompanamiento.acompanaVigente(any(UserId.class), any(UUID.class), any(Instant.class))).thenAnswer(
                invocacion -> mentora.equals(invocacion.getArgument(0)) && fenix.equals(invocacion.getArgument(1))
                        || otroMentor.equals(invocacion.getArgument(0)) && aurora.equals(invocacion.getArgument(1)));
        when(acompanamiento.aprendicesVigentes(any(UUID.class), any(Instant.class))).thenReturn(List.of());
        when(acompanamiento.aprendicesVigentes(eq(fenix), any())).thenReturn(List.of(ana));
        when(acompanamiento.aprendicesVigentes(eq(aurora), any())).thenReturn(List.of(beto));
        when(huecos.semanaDe(any(), any())).thenReturn(SemanasDeHuecosDePrueba.respuestaDelContrato(LUNES, PRIMERA, false));
    }

    private void usuario(UserId id, UserRole rol, UserStatus estado) {
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Nombre", null, rol, estado)));
    }

    private ResultActions pedir(String query, UserId actor, UUID grupo, UserId alumno) throws Exception {
        return mockMvc.perform(get("/api/v1/mentor/groups/" + grupo + "/learners/" + alumno + "/huecos" + query)
                .header("X-Actor-Id", actor.toString()));
    }

    @Test
    @DisplayName("el mentor del grupo ve los huecos de su alumno con el JSON del contrato, sin cache")
    void contrato() throws Exception {
        pedir("", mentora, fenix, ana)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().json("""
                        {
                          "semana": "2026-10-26", "primeraSemana": "2026-10-12", "cerrada": false,
                          "resumen": {
                            "minutosOcupados": 410,
                            "huecosQueSeRepiten": [ { "desde": "15:00", "hasta": "17:00", "dias": [1, 3, 4] } ],
                            "cumplidosATiempo": 32, "cumplidosTarde": 6, "noCumplidos": 4
                          }
                        }
                        """, JsonCompareMode.LENIENT))
                .andExpect(jsonPath("$.dias[0].fecha").value("2026-10-26"))
                .andExpect(jsonPath("$.dias[0].ventana.desde").value("05:30"))
                .andExpect(jsonPath("$.dias[0].ventana.hasta").value("22:30"))
                .andExpect(jsonPath("$.dias[0].minutosOcupados").value(410))
                .andExpect(jsonPath("$.dias[0].minutosLibres").value(610))
                .andExpect(jsonPath("$.dias[0].huecos[0].desde").value("15:00"))
                .andExpect(jsonPath("$.dias[0].huecos[0].hasta").value("17:00"))
                .andExpect(jsonPath("$.dias[0].huecos[0].minutos").value(120))
                .andExpect(jsonPath("$.dias", hasSize(7)))
                .andExpect(jsonPath("$.dias[1].ventana").value(nullValue()))
                .andExpect(jsonPath("$.dias[1].huecos[0].hasta").value("24:00"));
    }

    @Test
    @DisplayName("la semana pedida llega al calculo; sin ella, nula (la en curso la decide el servicio en la zona del alumno)")
    void semanaPedida() throws Exception {
        pedir("?semana=2026-10-19", mentora, fenix, ana).andExpect(status().isOk());
        verify(huecos).semanaDe(ana, LocalDate.of(2026, 10, 19));

        pedir("", mentora, fenix, ana).andExpect(status().isOk());
        verify(huecos).semanaDe(ana, null);
    }

    @Test
    @DisplayName("una semana que no se puede pedir (futura, anterior a la primera, no lunes) es 400")
    void semanaInvalida() throws Exception {
        doThrow(new IllegalArgumentException("Esa semana todavia no llega")).when(huecos).semanaDe(any(), any());

        pedir("?semana=2026-11-02", mentora, fenix, ana)
                .andExpect(status().isBadRequest());
        pedir("?semana=no-es-fecha", mentora, fenix, ana).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("una persona que no existe es 404")
    void inexistente() throws Exception {
        doThrow(new NoSuchElementException("Persona no encontrada")).when(huecos).semanaDe(any(), any());

        pedir("", mentora, fenix, ana).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("autorizacion negativa: un alumno (TRAINEE) que no acompana nada recibe 403 aunque tenga USE_APP")
    void aprendizRecibe403() throws Exception {
        pedir("", beto, aurora, beto).andExpect(status().isForbidden());
        pedir("", ana, fenix, ana).andExpect(status().isForbidden());
        verifyNoInteractions(huecos);
    }

    @Test
    @DisplayName("autorizacion negativa: el mentor de OTRO grupo recibe 403, y tampoco lee a un alumno ajeno con el id de su grupo")
    void otroGrupo() throws Exception {
        pedir("", otroMentor, fenix, ana).andExpect(status().isForbidden());
        pedir("", mentora, fenix, beto).andExpect(status().isForbidden());
        verifyNoInteractions(huecos);
    }

    @Test
    @DisplayName("autorizacion negativa: un mentor SUSPENDIDO recibe 403 aunque su token sea valido")
    void mentorSuspendido() throws Exception {
        usuario(mentora, UserRole.MENTOR, UserStatus.SUSPENDED);

        pedir("", mentora, fenix, ana).andExpect(status().isForbidden());
        verifyNoInteractions(huecos);
    }

    @Test
    @DisplayName("autorizacion negativa: Soporte TI y un ADMIN que no acompanan el grupo reciben 403 por esta ruta")
    void rolesQueNoAcompanan() throws Exception {
        UserId soporte = UserId.of(UUID.randomUUID());
        UserId admin = UserId.of(UUID.randomUUID());
        usuario(soporte, UserRole.IT_SUPPORT, UserStatus.ACTIVE);
        usuario(admin, UserRole.ADMIN, UserStatus.ACTIVE);

        pedir("", soporte, fenix, ana).andExpect(status().isForbidden());
        pedir("", admin, fenix, ana).andExpect(status().isForbidden());
        verifyNoInteractions(huecos);
    }
}
