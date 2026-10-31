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

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_VIGENTE;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.VIERNES_FUTURO;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El líder ve a todos los aprendices, estén o no en un grupo (spec 021), de punta a punta en la capa web: el contrato de
 * la lista y de las cuatro lecturas de una persona, que el cuerpo sea el de la ruta por grupo, que nada se cachee, y la
 * autorización negativa (403 por rol, por cuenta suspendida; 404 por persona que no se muestra).
 */
@WebMvcTest({PersonasDelProgramaController.class, CumplimientoDelLiderController.class,
        HuecosDeLaPersonaController.class, CierresDeLaPersonaController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(CumplimientoDePrueba.class)
class PersonasDelProgramaControllerTest {

    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId SOPORTE_TI = id("e1");
    private static final UserId MENTORA = id("a1");
    private static final UserId OTRO_MENTOR = id("a2");
    private static final UserId GUIA = id("a3");
    private static final UserId ANA = id("b1");
    private static final UserId BETO = id("b2");
    private static final UserId PRUEBA = id("b3");

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
        banco.usuario(SOPORTE_TI, "Soporte", UserRole.IT_SUPPORT, UserStatus.ACTIVE);
        banco.usuario(OTRO_MENTOR, "Otro Mentor", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(GUIA, "Guía", UserRole.TRAINEE_GUIDE, UserStatus.ACTIVE);
        banco.grupoConMentor(FENIX, "Grupo Fénix", MENTORA, "Luisa Rojas");
        banco.aprendiz(FENIX, ANA, "Ana Pérez");
        banco.aprendizSinGrupo(BETO, "Beto Paz");
        banco.aprendizSinGrupo(PRUEBA, "Cuenta de Prueba");
        banco.cuentaDePrueba(PRUEBA);
        banco.vigente(ANA, ventanaPareja(DESDE_VIGENTE, false, 90));
        banco.vigente(BETO, ventanaPareja(DESDE_VIGENTE, false, 40));
        banco.semana(SEMANA_CERRADA, ANA, ventanaPareja(DESDE_SEMANA_CERRADA, true, 90));
        banco.semana(SEMANA_CERRADA, BETO, ventanaPareja(DESDE_SEMANA_CERRADA, true, 40));
    }

    private ResultActions pedir(String ruta, UserId actor) throws Exception {
        return mockMvc.perform(get(ruta).header("X-Actor-Id", actor.toString()));
    }

    private static String personas() {
        return "/api/v1/leadership/people";
    }

    private static String de(UserId persona, String parte) {
        return personas() + "/" + persona + "/" + parte;
    }

    private String cuerpo(String ruta, UserId actor) throws Exception {
        return pedir(ruta, actor).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // ── la lista ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("lista: el contrato {items, page, size, total}, por nombre, con y sin grupo, sin cache y sin correo")
    void lista() throws Exception {
        pedir(personas(), LIDER)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(30))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].personId").value(ANA.toString()))
                .andExpect(jsonPath("$.items[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.items[0].grupoId").value(FENIX.toString()))
                .andExpect(jsonPath("$.items[0].grupoNombre").value("Grupo Fénix"))
                .andExpect(jsonPath("$.items[0].mentorNombre").value("Luisa Rojas"))
                .andExpect(jsonPath("$.items[0].diaPrograma").value(63))
                .andExpect(jsonPath("$.items[1].personId").value(BETO.toString()))
                .andExpect(jsonPath("$.items[1].grupoId").doesNotExist())
                .andExpect(jsonPath("$.items[1].grupoNombre").doesNotExist())
                .andExpect(jsonPath("$.items[1].mentorNombre").doesNotExist())
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].correo").doesNotExist());
        assertThat(cuerpo(personas(), LIDER)).doesNotContain("@").doesNotContain("Cuenta de Prueba");
    }

    @Test
    @DisplayName("lista: q busca sin tildes ni mayúsculas; page y size pagan la lista; el administrador y el alquimista también la abren")
    void listaConParametros() throws Exception {
        pedir(personas() + "?q=PEREZ", LIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].nombre").value("Ana Pérez"));
        pedir(personas() + "?page=1&size=1", ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].nombre").value("Beto Paz"));
        pedir(personas(), ALQUIMISTA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("lista: una página negativa, un tamaño en cero o de más de 100 es 400")
    void listaConParametrosInvalidos() throws Exception {
        pedir(personas() + "?page=-1", LIDER).andExpect(status().isBadRequest());
        pedir(personas() + "?size=0", LIDER).andExpect(status().isBadRequest());
        pedir(personas() + "?size=101", LIDER).andExpect(status().isBadRequest());
        pedir(personas() + "?size=abc", LIDER).andExpect(status().isBadRequest());
    }

    // ── la semana ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("semana de alguien sin grupo: 200, sin grupo ni mentor, puedeAvisarAlMentor en false, sin cache, y deja su consulta")
    void semanaSinGrupo() throws Exception {
        pedir(de(BETO, "week"), LIDER)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.personaId").value(BETO.toString()))
                .andExpect(jsonPath("$.nombre").value("Beto Paz"))
                .andExpect(jsonPath("$.grupoId").doesNotExist())
                .andExpect(jsonPath("$.mentor").doesNotExist())
                .andExpect(jsonPath("$.puedeAvisarAlMentor").value(false))
                .andExpect(jsonPath("$.desde").value("2026-10-12"))
                .andExpect(jsonPath("$.hasta").value("2026-10-18"))
                .andExpect(jsonPath("$.color").value("ROJO"))
                .andExpect(jsonPath("$.porcentaje").value(40.0))
                .andExpect(jsonPath("$.dias", hasSize(7)));
        assertThat(banco.consultas).singleElement().satisfies(c -> {
            assertThat(c.personaId()).isEqualTo(BETO);
            assertThat(c.grupoId()).isNull();
            assertThat(c.mentorId()).isNull();
        });
    }

    @Test
    @DisplayName("semana de alguien con grupo y mentor: puedeAvisarAlMentor en true, en la ruta nueva Y en la del grupo")
    void puedeAvisarAlMentor() throws Exception {
        pedir(de(ANA, "week"), LIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupoId").value(FENIX.toString()))
                .andExpect(jsonPath("$.mentor.nombre").value("Luisa Rojas"))
                .andExpect(jsonPath("$.puedeAvisarAlMentor").value(true));
        pedir("/api/v1/leadership/groups/" + FENIX + "/people/" + ANA + "/week", LIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeAvisarAlMentor").value(true));
    }

    @Test
    @DisplayName("semana: para alguien con grupo el cuerpo es IDÉNTICO al de la ruta por grupo, con la ventana vigente y con un viernes")
    void elMismoCuerpoQueElDelGrupo() throws Exception {
        String grupo = "/api/v1/leadership/groups/" + FENIX + "/people/" + ANA + "/week";

        assertThat(cuerpo(de(ANA, "week"), LIDER)).isEqualTo(cuerpo(grupo, LIDER));
        assertThat(cuerpo(de(ANA, "week") + "?semanaHasta=" + SEMANA_CERRADA, LIDER))
                .isEqualTo(cuerpo(grupo + "?semanaHasta=" + SEMANA_CERRADA, LIDER));
    }

    @Test
    @DisplayName("semana: la fecha se pide con «semanaHasta», como en la ruta por grupo; «hasta» se entiende igual y, si vienen los dos, manda semanaHasta")
    void semanaHastaYHasta() throws Exception {
        pedir(de(ANA, "week") + "?semanaHasta=" + SEMANA_CERRADA, LIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-10-10"))
                .andExpect(jsonPath("$.hasta").value("2026-10-16"))
                .andExpect(jsonPath("$.cerrada").value(true));
        pedir(de(ANA, "week") + "?hasta=" + SEMANA_CERRADA, LIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-10-10"));
        pedir(de(ANA, "week") + "?semanaHasta=" + SEMANA_CERRADA + "&hasta=" + SEMANA_CERRADA, LIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-10-10"));
        pedir(de(ANA, "week") + "?semanaHasta=" + VIERNES_FUTURO, LIDER).andExpect(status().isBadRequest());
        pedir(de(ANA, "week") + "?semanaHasta=2026-10-15", LIDER).andExpect(status().isBadRequest());
        pedir(de(ANA, "week") + "?hasta=2026-10-15", LIDER).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("huecos y cierres piden la semana con «semana», el mismo nombre que sus rutas por grupo, y dan el mismo cuerpo")
    void semanaDeHuecosYCierres() throws Exception {
        String grupo = "/api/v1/leadership/groups/" + FENIX + "/people/" + ANA;

        assertThat(cuerpo(de(ANA, "huecos") + "?semana=2026-10-12", LIDER))
                .isEqualTo(cuerpo(grupo + "/huecos?semana=2026-10-12", LIDER));
        assertThat(cuerpo(de(ANA, "cierres") + "?semana=2026-10-12", LIDER))
                .isEqualTo(cuerpo(grupo + "/cierres?semana=2026-10-12", LIDER));
        pedir(de(ANA, "huecos") + "?semana=2026-10-12", LIDER).andExpect(jsonPath("$.semana").value("2026-10-12"));
        pedir(de(ANA, "cierres") + "?semana=2026-10-05", LIDER).andExpect(jsonPath("$.semana").value("2026-10-05"));
    }

    // ── el Radar, los huecos y los cierres ──────────────────────────────────

    @Test
    @DisplayName("radar, huecos y cierres de alguien sin grupo: 200, sin cache; huecos y cierres dejan su consulta y el radar no")
    void lecturasDeUnaPersona() throws Exception {
        pedir(de(BETO, "radar"), LIDER)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.miRadar.encendido").value(false))
                .andExpect(jsonPath("$.entries", hasSize(0)));
        assertThat(banco.consultas).isEmpty();

        pedir(de(BETO, "huecos") + "?semana=2026-10-12", LIDER)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.semana").value("2026-10-12"));
        pedir(de(BETO, "cierres") + "?semana=2026-10-12", LIDER)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.semana").value("2026-10-12"));
        assertThat(banco.consultas).hasSize(2);
        assertThat(banco.consultas).allSatisfy(c -> {
            assertThat(c.personaId()).isEqualTo(BETO);
            assertThat(c.grupoId()).isNull();
        });
    }

    // ── autorización negativa ───────────────────────────────────────────────

    private List<String> rutasDeUnaPersona(UserId persona) {
        return List.of(de(persona, "week"), de(persona, "radar"), de(persona, "huecos"), de(persona, "cierres"));
    }

    @Test
    @DisplayName("autorizacion negativa: MENTOR (también el del grupo), guía, aprendiz y Soporte TI reciben 403 en las cinco rutas y no se lee ni registra nada")
    void rolesSinPermiso() throws Exception {
        for (UserId actor : List.of(OTRO_MENTOR, MENTORA, GUIA, ANA, SOPORTE_TI)) {
            pedir(personas(), actor).andExpect(status().isForbidden());
            for (String ruta : rutasDeUnaPersona(BETO)) {
                pedir(ruta, actor).andExpect(status().isForbidden());
            }
        }

        assertThat(banco.consultas).isEmpty();
        assertThat(banco.lecturasDelRadar).isEmpty();
        assertThat(banco.ventanasPedidasALaSemana).isEmpty();
        assertThat(banco.horariosPedidos).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: lider, admin y alquimista SUSPENDIDOS reciben 403 aunque su token sea valido")
    void cuentaSuspendida() throws Exception {
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.SUSPENDED);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.SUSPENDED);

        for (UserId actor : List.of(LIDER, ADMIN, ALQUIMISTA)) {
            pedir(personas(), actor).andExpect(status().isForbidden());
            for (String ruta : rutasDeUnaPersona(BETO)) {
                pedir(ruta, actor).andExpect(status().isForbidden());
            }
        }
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("una cuenta de prueba, una suspendida, quien no es aprendiz y un id que no existe son 404 en las cuatro rutas, sin leer ni registrar")
    void personaQueNoSeMuestra() throws Exception {
        banco.aprendizSinGrupo(id("b4"), "Suspendida");
        banco.usuario(id("b4"), "Suspendida", UserRole.TRAINEE, UserStatus.SUSPENDED);

        for (UserId persona : List.of(PRUEBA, id("b4"), MENTORA, LIDER, id("ff"))) {
            for (String ruta : rutasDeUnaPersona(persona)) {
                pedir(ruta, LIDER).andExpect(status().isNotFound());
            }
        }

        assertThat(banco.consultas).isEmpty();
        assertThat(banco.lecturasDelRadar).isEmpty();
        assertThat(banco.ventanasPedidasALaSemana).isEmpty();
        assertThat(banco.horariosPedidos).isEmpty();
    }
}
