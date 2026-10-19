package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.services.BancoDeCumplimiento;
import com.renaser.os.leadership.application.services.CumplimientoDePrueba;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_EN_CURSO;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.HASTA_EN_CURSO;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SIN_MENTOR;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventana;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La lista del grupo y la semana de una persona para el líder (spec 005), de punta a punta en la capa web: el
 * contrato, que nada se cachee, y la autorización negativa (rol sin permiso → 403, cuenta suspendida → 403 aunque el
 * token sea válido).
 */
@WebMvcTest(CumplimientoDelLiderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CumplimientoDePrueba.class)
class CumplimientoDelLiderControllerTest {

    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId MENTORA = id("a1");
    private static final UserId OTRO_MENTOR = id("a2");
    private static final UserId ANA = id("b1");
    private static final UserId BETO = id("b2");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BancoDeCumplimiento banco;

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    @BeforeEach
    void preparar() {
        banco.limpiar();
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        banco.usuario(OTRO_MENTOR, "Otro Mentor", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.grupoConMentor(FENIX, "Grupo Fénix", MENTORA, "Luisa Rojas");
        banco.aprendiz(FENIX, ANA, "Ana Pérez");
        banco.aprendiz(FENIX, BETO, "Beto Paz");
        // La lista abre en la ultima semana cerrada (10 al 16); la ficha, en la semana en curso (17 al 23).
        banco.semana(SEMANA_CERRADA, ANA, ventanaPareja(DESDE_SEMANA_CERRADA, true, 90));
        banco.semana(SEMANA_CERRADA, BETO, ventanaPareja(DESDE_SEMANA_CERRADA, true, 40));
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.semana(SEMANA_CERRADA, MENTORA, ventanaPareja(DESDE_SEMANA_CERRADA, true, 75));
        banco.semana(HASTA_EN_CURSO, ANA, ventana(DESDE_EN_CURSO, false, 90, 90, null, null, null, null, null));
    }

    private ResultActions pedir(String ruta, UserId actor) throws Exception {
        return mockMvc.perform(get(ruta).header("X-Actor-Id", actor.toString()));
    }

    private static String grupo() {
        return "/api/v1/leadership/groups/" + FENIX + "/semaforo";
    }

    private static String semana(UserId persona) {
        return "/api/v1/leadership/groups/" + FENIX + "/people/" + persona + "/week";
    }

    // ── la lista ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("lista: sin fecha es la ultima semana cerrada; el contrato del semaforo del grupo, con el orden del lider y «no se mide» al final, sin cache")
    void lista() throws Exception {
        pedir(grupo(), LIDER)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.grupoId").value(FENIX.toString()))
                .andExpect(jsonPath("$.grupoNombre").value("Grupo Fénix"))
                .andExpect(jsonPath("$.desde").value("2026-10-10"))
                .andExpect(jsonPath("$.hasta").value("2026-10-16"))
                .andExpect(jsonPath("$.cerrada").value(true))
                .andExpect(jsonPath("$.resumen.verde").value(1))
                .andExpect(jsonPath("$.resumen.rojo").value(1))
                .andExpect(jsonPath("$.resumen.total").value(2))
                .andExpect(jsonPath("$.aprendices", hasSize(3)))
                .andExpect(jsonPath("$.aprendices[0].aprendizId").value(BETO.toString()))
                .andExpect(jsonPath("$.aprendices[0].nombre").value("Beto Paz"))
                .andExpect(jsonPath("$.aprendices[0].color").value("ROJO"))
                .andExpect(jsonPath("$.aprendices[0].etiqueta").value("Con problemas"))
                .andExpect(jsonPath("$.aprendices[0].porcentaje").value(40.0))
                .andExpect(jsonPath("$.aprendices[0].rol").value("TRAINEE"))
                .andExpect(jsonPath("$.aprendices[0].dias", hasSize(7)))
                .andExpect(jsonPath("$.aprendices[1].aprendizId").value(ANA.toString()))
                .andExpect(jsonPath("$.aprendices[2].aprendizId").value(MENTORA.toString()))
                .andExpect(jsonPath("$.aprendices[2].color").value("NO_SE_MIDE"))
                .andExpect(jsonPath("$.aprendices[2].etiqueta").value("No se mide en el semáforo"))
                .andExpect(jsonPath("$.aprendices[2].porcentaje").value(75.0))
                .andExpect(jsonPath("$.aprendices[2].rol").value("MENTOR"))
                .andExpect(jsonPath("$.aprendices[2].dias", hasSize(0)));
    }

    @Test
    @DisplayName("lista: el administrador y el alquimista tambien la abren")
    void listaParaAdministracion() throws Exception {
        pedir(grupo(), ADMIN).andExpect(status().isOk());
        pedir(grupo(), ALQUIMISTA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("lista: la semana pedida es un viernes; otro dia o una semana futura es 400")
    void listaConSemana() throws Exception {
        banco.semana(LocalDate.of(2026, 10, 9), ANA, ventanaPareja(LocalDate.of(2026, 10, 3), true, 55));

        pedir(grupo() + "?semanaHasta=2026-10-09", LIDER)
                .andExpect(status().isOk()).andExpect(jsonPath("$.cerrada").value(true))
                .andExpect(jsonPath("$.desde").value("2026-10-03"));
        pedir(grupo() + "?semanaHasta=2026-10-15", LIDER).andExpect(status().isBadRequest());
        pedir(grupo() + "?semanaHasta=2026-10-30", LIDER).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("lista: un grupo sin mentor o que no existe es 404")
    void listaDeUnGrupoQueNoSeAbre() throws Exception {
        pedir("/api/v1/leadership/groups/" + SIN_MENTOR + "/semaforo", LIDER).andExpect(status().isNotFound());
        pedir("/api/v1/leadership/groups/" + UUID.randomUUID() + "/semaforo", LIDER).andExpect(status().isNotFound());
    }

    // ── la ficha ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ficha: sin fecha es la semana en curso; el contrato de la semana de una persona, sin cache, y deja una consulta registrada")
    void ficha() throws Exception {
        pedir(semana(ANA), LIDER)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.grupoId").value(FENIX.toString()))
                .andExpect(jsonPath("$.personaId").value(ANA.toString()))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.desde").value("2026-10-17"))
                .andExpect(jsonPath("$.hasta").value("2026-10-23"))
                .andExpect(jsonPath("$.zona").value("America/Lima"))
                .andExpect(jsonPath("$.semanaEnCurso").value(true))
                .andExpect(jsonPath("$.cerrada").value(false))
                .andExpect(jsonPath("$.primeraSemana").value("2026-08-15"))
                .andExpect(jsonPath("$.mentor.nombre").value("Luisa Rojas"))
                .andExpect(jsonPath("$.color").value("VERDE"))
                .andExpect(jsonPath("$.etiqueta").value("Al día"))
                .andExpect(jsonPath("$.porcentaje").value(90.0))
                .andExpect(jsonPath("$.resumen.obligaciones").value(0))
                .andExpect(jsonPath("$.dias", hasSize(7)))
                .andExpect(jsonPath("$.dias[0].fecha").value("2026-10-17"))
                .andExpect(jsonPath("$.dias[0].obligaciones", hasSize(0)))
                .andExpect(jsonPath("$.dias[0].acciones", hasSize(0)))
                .andExpect(jsonPath("$.dias[2].motivo").value("EN_CURSO"))
                .andExpect(jsonPath("$.dias[3].motivo").value("FUTURO"))
                .andExpect(jsonPath("$.dias[6].fecha").value("2026-10-23"));
        assertThat(banco.consultas).hasSize(1);
    }

    @Test
    @DisplayName("ficha: una semana cerrada del pasado")
    void fichaDeUnaSemanaCerrada() throws Exception {
        banco.semana(SEMANA_CERRADA, ANA, ventanaPareja(LocalDate.of(2026, 10, 10), true, 55));

        pedir(semana(ANA) + "?semanaHasta=" + SEMANA_CERRADA, LIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-10-10"))
                .andExpect(jsonPath("$.hasta").value("2026-10-16"))
                .andExpect(jsonPath("$.semanaEnCurso").value(false))
                .andExpect(jsonPath("$.cerrada").value(true))
                .andExpect(jsonPath("$.color").value("ROJO"));
    }

    @Test
    @DisplayName("ficha: una semana futura es 400, una persona de otro grupo es 404, y ninguna deja registro")
    void fichaQueNoSeAbre() throws Exception {
        pedir(semana(ANA) + "?semanaHasta=2026-10-30", LIDER).andExpect(status().isBadRequest());
        pedir(semana(id("b9")), LIDER).andExpect(status().isNotFound());
        pedir("/api/v1/leadership/groups/" + SIN_MENTOR + "/people/" + ANA + "/week", LIDER)
                .andExpect(status().isNotFound());

        assertThat(banco.consultas).isEmpty();
    }

    // ── autorización negativa ───────────────────────────────────────────────

    @Test
    @DisplayName("autorizacion negativa: MENTOR y TRAINEE reciben 403 en los dos endpoints y no se lee ni se registra nada")
    void rolesSinPermiso() throws Exception {
        for (UserId actor : List.of(OTRO_MENTOR, MENTORA, ANA)) {
            pedir(grupo(), actor).andExpect(status().isForbidden());
            pedir(semana(ANA), actor).andExpect(status().isForbidden());
        }

        assertThat(banco.consultas).isEmpty();
        assertThat(banco.lecturasDelSemaforo).isEmpty();
        assertThat(banco.ventanasPedidasALaSemana).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: un LIDER SUSPENDIDO recibe 403 aunque su token sea valido")
    void liderSuspendido() throws Exception {
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);

        pedir(grupo(), LIDER).andExpect(status().isForbidden());
        pedir(semana(ANA), LIDER).andExpect(status().isForbidden());
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: un ADMIN SUSPENDIDO recibe 403")
    void adminSuspendido() throws Exception {
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.SUSPENDED);

        pedir(grupo(), ADMIN).andExpect(status().isForbidden());
        pedir(semana(ANA), ADMIN).andExpect(status().isForbidden());
    }
}
