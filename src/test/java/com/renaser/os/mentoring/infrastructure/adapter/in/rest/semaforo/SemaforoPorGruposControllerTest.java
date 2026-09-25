package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.mentoring.application.services.BancoDelSemaforo;
import com.renaser.os.mentoring.application.services.VistasDelSemaforoDePrueba;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.DESDE_VIGENTE;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/semaforo/groups}: el permiso declarado es {@code USE_APP}, que tienen TODOS los
 * roles, así que quien protege es el guard. Estas pruebas lo demuestran de punta a punta.
 */
@WebMvcTest(SemaforoPorGruposController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(VistasDelSemaforoDePrueba.class)
class SemaforoPorGruposControllerTest {

    private static final String RESUMEN = "/api/v1/semaforo/groups";

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID AURORA = UUID.fromString("00000000-0000-0000-0000-00000000f002");
    private static final UserId LUISA = id("a1");
    private static final UserId RAUL = id("a2");
    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId ANA = id("b1");
    private static final UserId LUIS = id("b2");
    private static final UserId MARIO = id("b3");
    private static final UserId PIA = id("b4");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BancoDelSemaforo banco;

    @BeforeEach
    void preparar() {
        banco.limpiar();
        banco.usuario(LIDER, "Lider", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);

        banco.grupo(FENIX, "Grupo Fénix");
        banco.mentor(FENIX, LUISA);
        banco.usuario(LUISA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        aprendiz(FENIX, ANA, "Ana Pérez", 85);
        aprendiz(FENIX, LUIS, "Luis Díaz", 50);
        aprendiz(FENIX, MARIO, "Mario Paz", null);

        banco.grupo(AURORA, "Grupo Aurora");
        banco.mentor(AURORA, RAUL);
        banco.usuario(RAUL, "Raúl Soto", UserRole.MENTOR, UserStatus.ACTIVE);
        aprendiz(AURORA, PIA, "Pia Luna", 70);
    }

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    private void aprendiz(UUID grupo, UserId id, String nombre, Integer porcentajeParejo) {
        banco.aprendiz(grupo, id);
        banco.persona(id, nombre);
        if (porcentajeParejo != null) {
            banco.vigente(id, ventanaPareja(DESDE_VIGENTE, false, porcentajeParejo));
        }
    }

    private ResultActions pedir(String ruta, UserId actor) throws Exception {
        return mockMvc.perform(get(ruta).header("X-Actor-Id", actor.toString()));
    }

    @Test
    @DisplayName("el lider ve el resumen con el JSON exacto del contrato (§4.4) y sin un solo aprendiz")
    void resumenConElFormatoDelContrato() throws Exception {
        pedir(RESUMEN, LIDER)
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "desde": "2026-09-18", "hasta": "2026-09-24", "cerrada": false,
                          "totales": { "verde": 1, "amarillo": 1, "rojo": 1, "sinDatos": 1, "total": 4 },
                          "grupos": [
                            { "grupoId": "00000000-0000-0000-0000-00000000f002", "grupoNombre": "Grupo Aurora",
                              "mentorNombre": "Raúl Soto",
                              "resumen": { "verde": 0, "amarillo": 1, "rojo": 0, "sinDatos": 0, "total": 1 },
                              "promedio": 70.0, "color": "AMARILLO", "etiqueta": "Requiere atención" },
                            { "grupoId": "00000000-0000-0000-0000-00000000f001", "grupoNombre": "Grupo Fénix",
                              "mentorNombre": "Luisa Rojas",
                              "resumen": { "verde": 1, "amarillo": 0, "rojo": 1, "sinDatos": 1, "total": 3 },
                              "promedio": 67.5, "color": "AMARILLO", "etiqueta": "Requiere atención" }
                          ]
                        }
                        """, JsonCompareMode.STRICT))
                .andExpect(content().string(not(containsString("Ana Pérez"))))
                .andExpect(content().string(not(containsString(ANA.toString()))));
    }

    @Test
    @DisplayName("ADMIN y ALCHEMIST tambien lo ven")
    void administracionTambien() throws Exception {
        for (UserId actor : List.of(ADMIN, ALQUIMISTA)) {
            pedir(RESUMEN, actor).andExpect(status().isOk()).andExpect(jsonPath("$.totales.total").value(4));
        }
    }

    @Test
    @DisplayName("autorizacion negativa: un MENTOR recibe 403 del guard aunque el interceptor lo deje pasar (A-1)")
    void mentorRecibe403() throws Exception {
        pedir(RESUMEN, LUISA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo MENTOR_LEAD/ADMIN/ALCHEMIST consultan el semaforo por grupos"));
        assertThat(banco.lecturasDelSemaforo).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: un TRAINEE activo tiene USE_APP y aun asi recibe 403 del guard")
    void traineeRecibe403() throws Exception {
        pedir(RESUMEN, ANA).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("autorizacion negativa: un LIDER SUSPENDIDO recibe 403 aunque su token sea valido (el modo sombra no lo frena)")
    void liderSuspendido() throws Exception {
        banco.usuario(LIDER, "Lider", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);

        pedir(RESUMEN, LIDER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("La cuenta esta suspendida"));
    }

    @Test
    @DisplayName("autorizacion negativa: un TRAINEE SUSPENDIDO recibe 403 del interceptor")
    void traineeSuspendido() throws Exception {
        banco.usuario(ANA, "Ana Pérez", UserRole.TRAINEE, UserStatus.SUSPENDED);

        pedir(RESUMEN, ANA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cuenta suspendida"));
    }

    @Test
    @DisplayName("semanaHasta que no es viernes: 400")
    void semanaHastaNoViernes() throws Exception {
        pedir(RESUMEN + "?semanaHasta=2026-09-19", LIDER).andExpect(status().isBadRequest());
    }
}
