package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.mentoring.application.services.BancoDelSemaforo;
import com.renaser.os.mentoring.application.services.BancoDelSemaforo.DetallePedido;
import com.renaser.os.mentoring.application.services.VistasDelSemaforoDePrueba;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemanaCerrada;
import com.renaser.os.points.api.VentanaDelSemaforo;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.DESDE_VIGENTE;
import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.LIMA;
import static com.renaser.os.mentoring.application.services.BancoDelSemaforo.SEMANA_CERRADA;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventana;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las dos rutas del mentor contra el controller real, el interceptor de permisos real y los
 * servicios reales (con dobles de {@code community}, {@code points} y {@code users}). Verifica el
 * JSON exacto del contrato (§4.1 y §4.3) y la autorización negativa de CLAUDE.MD §0.3.
 */
@WebMvcTest(SemaforoDelMentorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(VistasDelSemaforoDePrueba.class)
class SemaforoDelMentorControllerTest {

    private static final String TABLA = "/api/v1/mentor/groups/{groupId}/semaforo";
    private static final String DETALLE = "/api/v1/mentor/groups/{groupId}/learners/{userId}/semaforo";

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID AURORA = UUID.fromString("00000000-0000-0000-0000-00000000f002");
    private static final UserId MENTORA = id("a1");
    private static final UserId ANA = id("b1");
    private static final UserId LUIS = id("b2");
    private static final UserId PIA = id("b9");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BancoDelSemaforo banco;

    @BeforeEach
    void preparar() {
        banco.limpiar();
        banco.grupo(FENIX, "Grupo Fénix");
        banco.grupo(AURORA, "Grupo Aurora");
        banco.mentor(FENIX, MENTORA);
        banco.usuario(MENTORA, "Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE);
        // Ana: 6 dias medidos y uno sin nada programado. Luis: el semaforo no lo mide.
        banco.aprendiz(FENIX, ANA);
        banco.persona(ANA, "Ana Pérez");
        banco.vigente(ANA, ventana(DESDE_VIGENTE, false, 75, 80, 80, 80, 75, 80, null));
        banco.aprendiz(FENIX, LUIS);
        banco.persona(LUIS, "Luis Díaz");
        // La mentora ademas cursa en su propio grupo: no puede aparecer en su tabla.
        banco.aprendiz(FENIX, MENTORA);
        banco.vigente(MENTORA, ventanaPareja(DESDE_VIGENTE, false, 40));
        banco.aprendiz(AURORA, PIA);
        banco.persona(PIA, "Pia Luna");
    }

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    private ResultActions pedir(String ruta, UserId actor, Object... variables) throws Exception {
        return mockMvc.perform(get(ruta, variables).header("X-Actor-Id", actor.toString()));
    }

    // ── la tabla ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la tabla del grupo sale con el JSON exacto del contrato (§4.3)")
    void tablaConElFormatoDelContrato() throws Exception {
        pedir(TABLA, MENTORA, FENIX)
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "grupoId": "00000000-0000-0000-0000-00000000f001",
                          "grupoNombre": "Grupo Fénix",
                          "desde": "2026-09-18", "hasta": "2026-09-24", "cerrada": false,
                          "resumen": { "verde": 0, "amarillo": 1, "rojo": 0, "sinDatos": 1, "total": 2 },
                          "aprendices": [
                            { "aprendizId": "00000000-0000-0000-0000-0000000000b1", "nombre": "Ana Pérez",
                              "avatarUrl": null, "porcentaje": 78.3, "color": "AMARILLO",
                              "etiqueta": "Requiere atención", "diasConDatos": 6,
                              "dias": [
                                { "fecha": "2026-09-18", "estado": "MEDIDO", "porcentaje": 75, "color": "AMARILLO",
                                  "etiqueta": "Requiere atención" },
                                { "fecha": "2026-09-19", "estado": "MEDIDO", "porcentaje": 80, "color": "VERDE",
                                  "etiqueta": "Al día" },
                                { "fecha": "2026-09-20", "estado": "MEDIDO", "porcentaje": 80, "color": "VERDE",
                                  "etiqueta": "Al día" },
                                { "fecha": "2026-09-21", "estado": "MEDIDO", "porcentaje": 80, "color": "VERDE",
                                  "etiqueta": "Al día" },
                                { "fecha": "2026-09-22", "estado": "MEDIDO", "porcentaje": 75, "color": "AMARILLO",
                                  "etiqueta": "Requiere atención" },
                                { "fecha": "2026-09-23", "estado": "MEDIDO", "porcentaje": 80, "color": "VERDE",
                                  "etiqueta": "Al día" },
                                { "fecha": "2026-09-24", "estado": "SIN_DATOS", "porcentaje": null, "color": "SIN_DATOS",
                                  "etiqueta": "Sin datos" }
                              ] },
                            { "aprendizId": "00000000-0000-0000-0000-0000000000b2", "nombre": "Luis Díaz",
                              "avatarUrl": null, "porcentaje": null, "color": "SIN_DATOS", "etiqueta": "Sin datos",
                              "diasConDatos": 0, "dias": [] }
                          ]
                        }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("con semanaHasta viernes devuelve esa semana sabado a viernes")
    void tablaDeUnaSemanaCerrada() throws Exception {
        banco.semana(SEMANA_CERRADA, ANA, ventana(LocalDate.of(2026, 9, 12), true, 80, 85, 90, 80, 85, 90, 80));

        pedir(TABLA + "?semanaHasta=2026-09-18", MENTORA, FENIX)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-09-12"))
                .andExpect(jsonPath("$.hasta").value("2026-09-18"))
                .andExpect(jsonPath("$.cerrada").value(true))
                .andExpect(jsonPath("$.aprendices[0].color").value("SIN_DATOS"))
                .andExpect(jsonPath("$.aprendices[1].etiqueta").value("Al día"));
        assertThat(banco.semanasPedidas).containsExactly(SEMANA_CERRADA);
    }

    @Test
    @DisplayName("semanaHasta que no es viernes: 400")
    void semanaHastaNoViernes() throws Exception {
        pedir(TABLA + "?semanaHasta=2026-09-17", MENTORA, FENIX).andExpect(status().isBadRequest());
        assertThat(banco.lecturasDelSemaforo).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: un TRAINEE que no acompaña el grupo recibe 403 del guard")
    void traineeQueNoAcompana() throws Exception {
        pedir(TABLA, ANA, FENIX)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No acompanas ese grupo"));
        assertThat(banco.lecturasDelSemaforo).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: un TRAINEE SUSPENDIDO recibe 403 del interceptor, aunque el token sea valido")
    void traineeSuspendido() throws Exception {
        banco.usuario(ANA, "Ana Pérez", UserRole.TRAINEE, UserStatus.SUSPENDED);

        pedir(TABLA, ANA, FENIX)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cuenta suspendida"));
    }

    @Test
    @DisplayName("autorizacion negativa: un MENTOR SUSPENDIDO que sigue asignado recibe 403 del guard (el interceptor no mira a MENTOR, A-1)")
    void mentorSuspendido() throws Exception {
        banco.usuario(MENTORA, "Luisa Rojas", UserRole.MENTOR, UserStatus.SUSPENDED);

        pedir(TABLA, MENTORA, FENIX)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("La cuenta esta suspendida"));
        pedir(DETALLE, MENTORA, FENIX, ANA.value()).andExpect(status().isForbidden());
        assertThat(banco.lecturasDelSemaforo).isEmpty();
        assertThat(banco.detallesPedidos).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: un exmentor con el token vivo recibe 403")
    void exmentor() throws Exception {
        UserId exmentor = id("a2");
        banco.usuario(exmentor, "Exmentor", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.exmentor(AURORA, exmentor, Instant.parse("2026-09-20T05:00:00Z"));

        pedir(TABLA, exmentor, AURORA).andExpect(status().isForbidden());
        pedir(DETALLE, exmentor, AURORA, PIA.value()).andExpect(status().isForbidden());
    }

    // ── el detalle ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("el detalle de un aprendiz sale con el JSON exacto del contrato (§4.1); semanas por defecto 8")
    void detalleConElFormatoDelContrato() throws Exception {
        banco.detalle(ANA, detalleDelContrato());

        pedir(DETALLE, MENTORA, FENIX, ANA.value())
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "aplica": true, "obligatorio": true, "zona": "America/Lima", "pausa": null,
                          "vigente": {
                            "desde": "2026-09-18", "hasta": "2026-09-24",
                            "porcentaje": 78.3, "color": "AMARILLO", "etiqueta": "Requiere atención",
                            "diasConDatos": 6, "cerrada": false,
                            "dias": [
                              %s
                            ]
                          },
                          "semanas": [
                            { "desde": "2026-09-12", "hasta": "2026-09-18", "porcentaje": 82.0, "color": "VERDE",
                              "etiqueta": "Al día", "diasConDatos": 7, "cerradaEn": "2026-09-19T05:25:03Z" }
                          ],
                          "calculadoEn": "2026-09-25T05:25:03Z"
                        }
                        """.formatted(diasDelContratoEnJson()), JsonCompareMode.STRICT));
        assertThat(banco.detallesPedidos).containsExactly(new DetallePedido(ANA, 8));
    }

    @Test
    @DisplayName("autorizacion negativa: un aprendiz de OTRO grupo no se ve con el id del grupo propio (403)")
    void aprendizDeOtroGrupo() throws Exception {
        pedir(DETALLE, MENTORA, FENIX, PIA.value())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Ese aprendiz no pertenece al grupo que acompanas"));
        assertThat(banco.detallesPedidos).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: un TRAINEE SUSPENDIDO recibe 403 tambien en el detalle")
    void traineeSuspendidoEnElDetalle() throws Exception {
        banco.usuario(ANA, "Ana Pérez", UserRole.TRAINEE, UserStatus.SUSPENDED);

        pedir(DETALLE, ANA, FENIX, ANA.value()).andExpect(status().isForbidden());
        assertThat(banco.detallesPedidos).isEmpty();
    }

    // ── fixture del detalle: el ejemplo del contrato, coherente día por día ──

    /** 9 hábitos y 3 objetivos, 7 + 2 cumplidos = 75 %; 8 y 2, 7 + 1 = 80 %. Promedio 470 / 6 = 78,3. */
    private static DetalleDelSemaforo detalleDelContrato() {
        List<DiaDelSemaforo> dias = List.of(
                dia(0, 75, 9, 7, 3, 2), dia(1, 80, 8, 7, 2, 1), dia(2, 80, 8, 7, 2, 1), dia(3, 80, 8, 7, 2, 1),
                dia(4, 75, 9, 7, 3, 2), dia(5, 80, 8, 7, 2, 1),
                DiaDelSemaforo.sinPorcentaje(DESDE_VIGENTE.plusDays(6), EstadoDiaSemaforo.SIN_DATOS));
        VentanaDelSemaforo vigente = new VentanaDelSemaforo(DESDE_VIGENTE, DESDE_VIGENTE.plusDays(6),
                new BigDecimal("78.3"), ColorSemaforo.AMARILLO, 6, false, dias);
        SemanaCerrada semana = new SemanaCerrada(LocalDate.of(2026, 9, 12), SEMANA_CERRADA, new BigDecimal("82.0"),
                ColorSemaforo.VERDE, 7, Instant.parse("2026-09-19T05:25:03Z"));
        return new DetalleDelSemaforo(true, true, LIMA, null, vigente, List.of(semana),
                Instant.parse("2026-09-25T05:25:03Z"));
    }

    private static DiaDelSemaforo dia(int desplazamiento, int porcentaje, int habitos, int habitosCumplidos,
                                      int objetivos, int objetivosCumplidos) {
        return new DiaDelSemaforo(DESDE_VIGENTE.plusDays(desplazamiento), EstadoDiaSemaforo.MEDIDO, porcentaje,
                porcentaje >= 80 ? ColorSemaforo.VERDE : ColorSemaforo.AMARILLO, habitos, habitosCumplidos,
                objetivos, objetivosCumplidos);
    }

    private static String diasDelContratoEnJson() {
        String medido = """
                { "fecha": "%s", "estado": "MEDIDO", "porcentaje": %d, "color": "%s", "etiqueta": "%s",
                  "habitos": { "programados": %d, "cumplidos": 7 }, "objetivos": { "programados": %d, "cumplidos": %d } }""";
        return String.join(",",
                medido.formatted("2026-09-18", 75, "AMARILLO", "Requiere atención", 9, 3, 2),
                medido.formatted("2026-09-19", 80, "VERDE", "Al día", 8, 2, 1),
                medido.formatted("2026-09-20", 80, "VERDE", "Al día", 8, 2, 1),
                medido.formatted("2026-09-21", 80, "VERDE", "Al día", 8, 2, 1),
                medido.formatted("2026-09-22", 75, "AMARILLO", "Requiere atención", 9, 3, 2),
                medido.formatted("2026-09-23", 80, "VERDE", "Al día", 8, 2, 1),
                """
                { "fecha": "2026-09-24", "estado": "SIN_DATOS", "porcentaje": null, "color": "SIN_DATOS",
                  "etiqueta": "Sin datos",
                  "habitos": { "programados": 0, "cumplidos": 0 }, "objetivos": { "programados": 0, "cumplidos": 0 } }""");
    }
}
