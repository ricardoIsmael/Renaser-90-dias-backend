package com.renaser.os.rocks.infrastructure.adapter.in.rest.cierre;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder.AccionDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.rocks.api.CierresDelDiaFinder.DiaDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.MotivoRepetido;
import com.renaser.os.rocks.api.CierresDelDiaFinder.RespuestaDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.ResumenDeCierres;
import com.renaser.os.rocks.application.services.CierresDelAlumnoService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/mentor/groups/{g}/learners/{u}/cierres} (spec 017): el contrato, que nada se cachee y la autorización negativa. El
 * permiso declarado es {@code USE_APP}, que tienen todos los roles, así que quien protege es el guard del servicio REAL
 * ({@link CierresDelAlumnoService}); los otros módulos son dobles.
 */
@WebMvcTest(CierreDelAlumnoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CierresDelAlumnoService.class, CierreDelAlumnoControllerTest.RelojDePrueba.class})
class CierreDelAlumnoControllerTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);

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
    private CierresDelDiaFinder cierres;

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
        reset(usuarios, acompanamiento, cierres);
        for (UserId id : List.of(mentora, otroMentor)) {
            when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        }
        when(acompanamiento.acompanaVigente(any(UserId.class), any(UUID.class), any(Instant.class))).thenAnswer(
                i -> mentora.equals(i.getArgument(0)) && fenix.equals(i.getArgument(1))
                        || otroMentor.equals(i.getArgument(0)) && aurora.equals(i.getArgument(1)));
        when(acompanamiento.aprendicesVigentes(any(UUID.class), any(Instant.class))).thenReturn(List.of());
        when(acompanamiento.aprendicesVigentes(eq(fenix), any())).thenReturn(List.of(ana));
        when(acompanamiento.aprendicesVigentes(eq(aurora), any())).thenReturn(List.of(beto));
        when(cierres.semanaDe(any(), any())).thenReturn(new CierresDeLaSemana(LUNES, LocalDate.of(2026, 10, 12), List.of(
                new DiaDelCierre(LUNES, "CERRADO", Instant.parse("2026-10-27T03:02:00Z"), null, List.of(
                        new AccionDelCierre(UUID.randomUUID(), "Revisar las facturas", LocalTime.of(20, 30), false, null,
                                new RespuestaDelCierre("SIN_TIEMPO", "OTRA_HORA", "Se alargó la reunión")))),
                new DiaDelCierre(LUNES.plusDays(1), "ABIERTO", null, null, List.of())),
                new ResumenDeCierres(1, 0, List.of(new MotivoRepetido("SIN_TIEMPO", 1)))));
    }

    private ResultActions pedir(String query, UserId actor, UUID grupo, UserId alumno) throws Exception {
        return mockMvc.perform(get("/api/v1/mentor/groups/" + grupo + "/learners/" + alumno + "/cierres" + query)
                .header("X-Actor-Id", actor.toString()));
    }

    @Test
    @DisplayName("el mentor del grupo ve los cierres de su alumno con el JSON del contrato, sin cache")
    void contrato() throws Exception {
        pedir("", mentora, fenix, ana)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.semana").value("2026-10-26"))
                .andExpect(jsonPath("$.primeraSemana").value("2026-10-12"))
                .andExpect(jsonPath("$.dias", hasSize(2)))
                .andExpect(jsonPath("$.dias[0].estado").value("CERRADO"))
                .andExpect(jsonPath("$.dias[0].cerradoEn").value("2026-10-27T03:02:00Z"))
                .andExpect(jsonPath("$.dias[0].acciones[0].titulo").value("Revisar las facturas"))
                .andExpect(jsonPath("$.dias[0].acciones[0].cierre.motivo").value("SIN_TIEMPO"))
                .andExpect(jsonPath("$.dias[0].acciones[0].cierre.manana").value("OTRA_HORA"))
                .andExpect(jsonPath("$.dias[1].estado").value("ABIERTO"))
                .andExpect(jsonPath("$.resumen.cerrados").value(1))
                .andExpect(jsonPath("$.resumen.sinCerrar").value(0))
                .andExpect(jsonPath("$.resumen.motivos[0].motivo").value("SIN_TIEMPO"))
                .andExpect(jsonPath("$.resumen.motivos[0].veces").value(1));
    }

    @Test
    @DisplayName("la semana pedida llega a la lectura; sin ella, nula (la en curso la decide la lectura en la zona del alumno)")
    void semanaPedida() throws Exception {
        pedir("?semana=2026-10-19", mentora, fenix, ana).andExpect(status().isOk());
        verify(cierres).semanaDe(ana, LocalDate.of(2026, 10, 19));

        pedir("", mentora, fenix, ana).andExpect(status().isOk());
        verify(cierres).semanaDe(ana, null);
    }

    @Test
    @DisplayName("una semana que no se puede pedir (futura, anterior a la primera, no lunes) es 400; una fecha que no es fecha tambien")
    void semanaInvalida() throws Exception {
        doThrow(new IllegalArgumentException("INVALID_DATE: esa semana todavía no llega")).when(cierres).semanaDe(any(), any());

        pedir("?semana=2026-11-02", mentora, fenix, ana).andExpect(status().isBadRequest());
        pedir("?semana=no-es-fecha", mentora, fenix, ana).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("una persona que no existe es 404")
    void inexistente() throws Exception {
        doThrow(new NoSuchElementException("Participante no encontrado")).when(cierres).semanaDe(any(), any());

        pedir("", mentora, fenix, ana).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("autorizacion negativa: un alumno, un mentor de otro grupo, un alumno de otro grupo y una cuenta suspendida reciben 403 y no se lee nada")
    void autorizacionNegativa() throws Exception {
        pedir("", ana, fenix, ana).andExpect(status().isForbidden());
        pedir("", otroMentor, fenix, ana).andExpect(status().isForbidden());
        pedir("", mentora, fenix, beto).andExpect(status().isForbidden());
        when(usuarios.findById(mentora)).thenReturn(Optional.of(new UserSummary(mentora, "Mentor", null, UserRole.MENTOR, UserStatus.SUSPENDED)));
        pedir("", mentora, fenix, ana).andExpect(status().isForbidden());

        verifyNoInteractions(cierres);
    }
}
