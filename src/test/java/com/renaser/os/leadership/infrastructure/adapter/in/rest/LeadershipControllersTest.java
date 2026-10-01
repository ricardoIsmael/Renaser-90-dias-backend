package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.services.BancoDeLiderazgo;
import com.renaser.os.leadership.application.services.LiderazgoDePrueba;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketPendiente;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeLiderazgo.semaforo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los endpoints de la gestión del Líder de Mentores (D-241), de punta a punta en la capa web: el
 * contrato, que no viaje ningún aprendiz, y la autorización negativa (rol sin permiso → 403, cuenta
 * suspendida → 403 aunque el token sea válido).
 */
@WebMvcTest({LeadershipMentorsController.class, LeadershipReportController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(LiderazgoDePrueba.class)
class LeadershipControllersTest {

    private static final String PADRON = "/api/v1/leadership/mentors";
    private static final String REPORTE = "/api/v1/leadership/report";
    private static final UserId LIDER = id("c1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId LUISA = id("a1");
    private static final UserId RAUL = id("a2");
    private static final UserId ANA = id("b1");
    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BancoDeLiderazgo banco;

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    @BeforeEach
    void preparar() {
        banco.observaciones.clear();
        banco.pendientes.clear();
        banco.usuario(LIDER, "Líder", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        banco.usuario(LUISA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(RAUL, "Raúl Soto", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(ANA, "Ana Pérez", UserRole.TRAINEE, UserStatus.ACTIVE);
        banco.perfil(LUISA, "N1", "YELLOW");
        banco.semaforoDe(LUISA, semaforo(1, 0, 0, 0, "85.0"));
        banco.evaluacion(LUISA, "80.0");
        banco.pendientes.add(new TicketPendiente(UUID.randomUUID(), ANA, Instant.parse("2026-09-29T15:00:00Z")));
    }

    private ResultActions pedir(String ruta, UserId actor) throws Exception {
        return mockMvc.perform(get(ruta).header("X-Actor-Id", actor.toString()));
    }

    private ResultActions observar(UserId actor, UserId mentor, String cuerpo) throws Exception {
        return mockMvc.perform(post(PADRON + "/" + mentor + "/observations").header("X-Actor-Id", actor.toString())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    @DisplayName("padron: el contrato, con bloques y su source, y sin un solo aprendiz (RL-07)")
    void padron() throws Exception {
        banco.grupo(FENIX, "Grupo Fénix", LUISA, List.of(ANA));

        pedir(PADRON, LIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.timezone").value("America/Lima"))
                .andExpect(jsonPath("$.mentors[0].fullName").value("Luisa Rojas"))
                .andExpect(jsonPath("$.mentors[0].groups.source").value("OK"))
                .andExpect(jsonPath("$.mentors[0].groups.items[0].name").value("Grupo Fénix"))
                .andExpect(jsonPath("$.mentors[0].traineeCount").value(1))
                .andExpect(jsonPath("$.mentors[0].semaforo.summary.etiqueta").value("Al día"))
                .andExpect(jsonPath("$.mentors[0].attention.open").value(1))
                .andExpect(jsonPath("$.mentors[0].attention.medianResponseHours").value(nullValue()))
                .andExpect(jsonPath("$.mentors[0].evaluation.percentage").value(80.0))
                .andExpect(jsonPath("$.mentors[1].fullName").value("Raúl Soto"))
                .andExpect(jsonPath("$.mentors[1].semaforo.summary").value(nullValue()))
                .andExpect(content().string(not(containsString("Ana Pérez"))))
                .andExpect(content().string(not(containsString(ANA.toString()))));
    }

    @Test
    @DisplayName("ficha: perfil y observaciones; un aprendiz no tiene ficha (404)")
    void ficha() throws Exception {
        pedir(PADRON + "/" + LUISA, ALQUIMISTA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentor.fullName").value("Luisa Rojas"))
                .andExpect(jsonPath("$.profile.level").value("N1"))
                .andExpect(jsonPath("$.accountActive").value(true))
                .andExpect(jsonPath("$.recentObservations").isEmpty());
        pedir(PADRON + "/" + ANA, LIDER).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("observacion: 201 y queda en la ficha; un tipo invalido es 400")
    void observacion() throws Exception {
        observar(LIDER, LUISA, """
                {"type":"RECONOCIMIENTO","text":"Muy buena respuesta","sentByChat":false,"operationKey":"k-1"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("RECONOCIMIENTO"));
        observar(LIDER, LUISA, """
                {"type":"FELICITACION","text":"x","sentByChat":false,"operationKey":"k-2"}
                """).andExpect(status().isBadRequest());
        observar(LIDER, LUISA, """
                {"type":"ALERTA","text":"  ","sentByChat":false,"operationKey":"k-3"}
                """).andExpect(status().isBadRequest());

        pedir(PADRON + "/" + LUISA + "/observations", LIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].text").value("Muy buena respuesta"));
        assertThat(banco.observaciones).hasSize(1);
    }

    @Test
    @DisplayName("reporte: mes pedido, cerrado, y los pendientes de hoy no se muestran como del mes")
    void reporte() throws Exception {
        pedir(REPORTE + "?month=2026-08", LIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.ranked[0].mentor.fullName").value("Luisa Rojas"))
                .andExpect(jsonPath("$.ranked[0].mentor.attention.open").value(nullValue()))
                .andExpect(jsonPath("$.withoutSample[0].mentor.fullName").value("Raúl Soto"));
        pedir(REPORTE + "?month=2026-13", LIDER).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("autorizacion negativa: MENTOR y TRAINEE reciben 403 en los cinco endpoints")
    void rolesSinPermiso() throws Exception {
        for (UserId actor : List.of(LUISA, ANA)) {
            pedir(PADRON, actor).andExpect(status().isForbidden());
            pedir(PADRON + "/" + RAUL, actor).andExpect(status().isForbidden());
            pedir(PADRON + "/" + RAUL + "/observations", actor).andExpect(status().isForbidden());
            observar(actor, RAUL, """
                    {"type":"ALERTA","text":"x","sentByChat":false,"operationKey":"k-9"}
                    """).andExpect(status().isForbidden());
            pedir(REPORTE, actor).andExpect(status().isForbidden());
        }
        assertThat(banco.observaciones).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: un LIDER SUSPENDIDO recibe 403 aunque su token sea valido")
    void liderSuspendido() throws Exception {
        banco.usuario(LIDER, "Líder", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);

        pedir(PADRON, LIDER).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Cuenta suspendida"));
        pedir(REPORTE, LIDER).andExpect(status().isForbidden());
        observar(LIDER, LUISA, """
                {"type":"ALERTA","text":"x","sentByChat":false,"operationKey":"k-8"}
                """).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("el rol no se inyecta: mandar role en el body no cambia nada")
    void rolNoInyectable() throws Exception {
        observar(LUISA, RAUL, """
                {"type":"ALERTA","text":"x","sentByChat":false,"operationKey":"k-7","role":"MENTOR_LEAD"}
                """).andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.result.ContentResultMatchers content() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content();
    }
}
