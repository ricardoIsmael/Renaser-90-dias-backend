package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.mentoring.application.services.BancoDelSemaforo;
import com.renaser.os.mentoring.application.services.BancoDelSemaforo.DetallePedido;
import com.renaser.os.mentoring.application.services.VistasDelSemaforoDePrueba;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.PausaDelSemaforo;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.DESDE_VIGENTE;
import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.LIMA;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las dos rutas administrativas del semáforo. Lo que hay que demostrar es que la puerta es de ROL:
 * ni el mentor del grupo ni el líder de mentores (que no ve nombres, RL-07) entran, aunque el
 * interceptor deje pasar al primero por A-1 y al segundo por el modo sombra.
 */
@WebMvcTest(SemaforoAdministrativoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(VistasDelSemaforoDePrueba.class)
class SemaforoAdministrativoControllerTest {

    private static final String TABLA = "/api/v1/admin/semaforo/groups/{groupId}";
    private static final String DETALLE = "/api/v1/admin/trainees/{traineeId}/semaforo";

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UserId MENTORA = id("a1");
    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId ANA = id("b1");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BancoDelSemaforo banco;

    @BeforeEach
    void preparar() {
        banco.limpiar();
        banco.grupo(FENIX, "Grupo Fénix");
        banco.mentor(FENIX, MENTORA);
        banco.usuario(MENTORA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(LIDER, "Lider", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        banco.aprendiz(FENIX, ANA);
        banco.persona(ANA, "Ana Pérez");
        banco.vigente(ANA, ventanaPareja(DESDE_VIGENTE, false, 55));
    }

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    private ResultActions pedir(String ruta, UserId actor, Object... variables) throws Exception {
        return mockMvc.perform(get(ruta, variables).header("X-Actor-Id", actor.toString()));
    }

    // ── la tabla con nombres ────────────────────────────────────────────────

    @Test
    @DisplayName("ADMIN ve la tabla de cualquier grupo, con nombres, sin acompañarlo")
    void adminVeLaTabla() throws Exception {
        pedir(TABLA, ADMIN, FENIX)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupoNombre").value("Grupo Fénix"))
                .andExpect(jsonPath("$.aprendices[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.aprendices[0].color").value("ROJO"))
                .andExpect(jsonPath("$.aprendices[0].etiqueta").value("Con problemas"))
                .andExpect(jsonPath("$.aprendices[0].dias[0].etiqueta").value("Con problemas"))
                .andExpect(jsonPath("$.resumen.rojo").value(1));
    }

    @Test
    @DisplayName("autorizacion negativa: el LIDER de mentores no puede pedir la tabla con nombres (403 del guard)")
    void liderNoVeLaTablaConNombres() throws Exception {
        pedir(TABLA, LIDER, FENIX).andExpect(status().isForbidden());
        assertThat(banco.lecturasDelSemaforo).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: ni el propio MENTOR del grupo entra por la puerta administrativa")
    void mentorNoEntraPorLaPuertaAdministrativa() throws Exception {
        pedir(TABLA, MENTORA, FENIX).andExpect(status().isForbidden());
        pedir(DETALLE, MENTORA, ANA.value()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("autorizacion negativa: TRAINEE recibe 403 del interceptor (MANAGE_TRAINEES no es suyo)")
    void traineeRecibe403() throws Exception {
        pedir(TABLA, ANA, FENIX)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No autorizado"));
        pedir(DETALLE, ANA, ANA.value()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("autorizacion negativa: un ADMIN SUSPENDIDO recibe 403 aunque su token sea valido")
    void adminSuspendido() throws Exception {
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.SUSPENDED);

        pedir(TABLA, ADMIN, FENIX).andExpect(status().isForbidden());
        pedir(DETALLE, ADMIN, ANA.value()).andExpect(status().isForbidden());
        assertThat(banco.lecturasDelSemaforo).isEmpty();
        assertThat(banco.detallesPedidos).isEmpty();
    }

    @Test
    @DisplayName("un grupo inexistente es 404; semanaHasta que no es viernes, 400")
    void grupoInexistenteYSemanaInvalida() throws Exception {
        pedir(TABLA, ADMIN, UUID.fromString("00000000-0000-0000-0000-00000000f0ff")).andExpect(status().isNotFound());
        pedir(TABLA + "?semanaHasta=2026-09-19", ADMIN, FENIX).andExpect(status().isBadRequest());
    }

    // ── el detalle de una persona ───────────────────────────────────────────

    @Test
    @DisplayName("ALCHEMIST ve el detalle de una persona del staff, con su pausa")
    void alquimistaVeElDetalleConPausa() throws Exception {
        banco.detalle(ANA, new DetalleDelSemaforo(true, false, LIMA,
                new PausaDelSemaforo(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 5)),
                ventanaPareja(DESDE_VIGENTE, false, 55), List.of(), Instant.parse("2026-09-25T05:25:03Z")));

        pedir(DETALLE + "?semanas=20", ALQUIMISTA, ANA.value())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.obligatorio").value(false))
                .andExpect(jsonPath("$.pausa.desde").value("2026-09-25"))
                .andExpect(jsonPath("$.pausa.hasta").value("2026-10-05"))
                .andExpect(jsonPath("$.vigente.dias.length()").value(7))
                .andExpect(jsonPath("$.vigente.dias[0].etiqueta").value("Con problemas"))
                .andExpect(jsonPath("$.vigente.dias[0].habitos.programados").value(20))
                .andExpect(jsonPath("$.vigente.dias[0].objetivos.programados").value(0));
        assertThat(banco.detallesPedidos).containsExactly(new DetallePedido(ANA, 13));
    }

    @Test
    @DisplayName("sin programa activado: aplica=false, vigente null, semanas vacias y pausa null")
    void personaSinPrograma() throws Exception {
        pedir(DETALLE, ADMIN, ANA.value())
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        { "aplica": false, "obligatorio": true, "zona": "America/Lima", "pausa": null,
                          "vigente": null, "semanas": [], "calculadoEn": null }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("una persona que no existe es 404")
    void personaInexistente() throws Exception {
        pedir(DETALLE, ADMIN, UUID.fromString("00000000-0000-0000-0000-0000000000ff")).andExpect(status().isNotFound());
        assertThat(banco.detallesPedidos).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: el LIDER tampoco ve el detalle de una persona (403 del guard)")
    void liderNoVeElDetalle() throws Exception {
        pedir(DETALLE, LIDER, ANA.value()).andExpect(status().isForbidden());
        assertThat(banco.detallesPedidos).isEmpty();
    }
}
