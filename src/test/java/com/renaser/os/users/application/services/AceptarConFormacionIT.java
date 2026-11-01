package com.renaser.os.users.application.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaAlAceptar;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 025 (D-302) de punta a punta: aceptar una solicitud eligiendo formación, duración, rol y ficha, con sesión real, Spring Security, los casos de uso
 * y Postgres con V140. El alta es la de producción (submit con token de correo); la limpieza, el borrado definitivo de verdad.
 *
 * <p>La ficha con diagnóstico (spec 026) se reemplaza por un doble ({@link FichaAlAceptar}): lo que importa aquí es que la aceptación lo llame DENTRO de
 * su transacción y que, si falla, no quede nada a medias.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AceptarConFormacionIT {

    private static final String CONTRASENA = "Secreta123!clave";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SubmitAccountRequestUseCase submit;
    @Autowired
    private TokenVerificacionEmailPort tokens;
    @Autowired
    private BorradoDefinitivoService borrado;
    @MockitoBean
    private FichaAlAceptar ficha;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> cuentas = new ArrayList<>();
    private final List<UUID> grupos = new ArrayList<>();
    private UUID cohorte;
    private final Instant inicio = Instant.now();

    @BeforeEach
    void ficha() {
        Mockito.reset(ficha);
        when(ficha.hayDiagnosticoPublicado()).thenReturn(true);
        /* Spec 027: una formación sin términos publicados no recibe a nadie; estas pruebas aceptan en las tres. */
        com.renaser.os.onboarding.TerminosDePrueba.publicarEnTodas(jdbc);
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        jdbc.update("UPDATE renaser.formaciones SET activa = true");
        esperarLosAvisosDeEstaPrueba();
        cuentas.forEach(id -> borrado.borrar(UserId.of(id), RegistroDeEliminacion.Accion.ELIMINADA_POR_ADMIN, null));
        com.renaser.os.onboarding.TerminosDePrueba.restablecer(jdbc);
        grupos.forEach(id -> jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        if (cohorte != null) {
            jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
        }
    }

    @Test
    @DisplayName("un aprendiz del Diplomado de 1 año con diagnóstico: inscripción a 365 días con el reloj en pausa, ficha pedida una vez dentro de la transacción, "
            + "y la solicitud dice lo elegido y quién lo aceptó")
    void aprendizDelDiplomadoDeUnAnio() throws Exception {
        UUID admin = usuario("ADMIN");
        String sesion = sesionDe(admin);
        Solicitud solicitud = solicitud("Ana Diplomada");

        HttpResponse<String> respuesta = aprobar(solicitud.id(), sesion,
                Map.of("formations", List.of("DIPLOMADO"), "durationDays", 365, "withDiagnosis", true));

        assertThat(respuesta.statusCode()).isEqualTo(204);
        assertThat(rolYEstado(solicitud.usuario())).containsEntry("rol", "APRENDIZ").containsEntry("estado", "ACTIVO");
        assertThat(jdbc.queryForMap("SELECT formacion, duracion_dias, dia_programa, programa_activado_en FROM renaser.participantes_programa "
                + "WHERE usuario_id = ?", solicitud.usuario()))
                .containsEntry("formacion", "DIPLOMADO").containsEntry("duracion_dias", (short) 365)
                .containsEntry("dia_programa", 0).containsEntry("programa_activado_en", null);
        verify(ficha).conDiagnostico(UserId.of(solicitud.usuario()), UserId.of(admin));

        JsonNode fila = filaDelListado(solicitud.id(), sesion, "?status=APPROVED&size=200");
        assertThat(fila.get("acceptance").get("formations").get(0).asText()).isEqualTo("DIPLOMADO");
        assertThat(fila.get("acceptance").get("durationDays").asInt()).isEqualTo(365);
        assertThat(fila.get("acceptance").get("role").asText()).isEqualTo("TRAINEE");
        assertThat(fila.get("acceptance").get("withDiagnosis").asBoolean()).isTrue();
        assertThat(fila.get("reviewedByName").asText()).isEqualTo("Fixture ADMIN");
    }

    @Test
    @DisplayName("FR-010 y SC-007: la persona aceptada en el Diplomado entra a la recepción del Diplomado y a ninguna de la Escuela")
    void entraALaRecepcionDeSuFormacion() throws Exception {
        UUID recepcionDiplomado = recepcion("DIPLOMADO");
        UUID recepcionEscuela = recepcion("ESCUELA");
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Beto Diplomado");

        assertThat(aprobar(solicitud.id(), sesion, Map.of("formations", List.of("DIPLOMADO"), "durationDays", 90)).statusCode()).isEqualTo(204);

        esperarLosAvisosDeEstaPrueba();
        assertThat(asignaciones(solicitud.usuario(), recepcionDiplomado)).as("recepción del Diplomado").isEqualTo(1);
        assertThat(asignaciones(solicitud.usuario(), recepcionEscuela)).as("recepción de la Escuela").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.asignaciones_celula a JOIN renaser.celulas c ON c.id = a.celula_id "
                + "WHERE a.usuario_id = ? AND c.formacion <> 'DIPLOMADO'", Integer.class, solicitud.usuario())).isZero();
    }

    @Test
    @DisplayName("un mentor en Escuela y Diplomado: queda asignado a las dos, trabaja en la primera, sin duración propia")
    void mentorEnDosFormaciones() throws Exception {
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Mentor Doble");

        HttpResponse<String> respuesta = aprobar(solicitud.id(), sesion,
                Map.of("role", "MENTOR", "formations", List.of("DIPLOMADO", "ESCUELA")));

        assertThat(respuesta.statusCode()).isEqualTo(204);
        assertThat(rolYEstado(solicitud.usuario())).containsEntry("rol", "MENTOR");
        assertThat(jdbc.queryForList("SELECT formacion || ':' || activa FROM renaser.formaciones_personal WHERE usuario_id = ? ORDER BY formacion",
                String.class, solicitud.usuario())).containsExactly("DIPLOMADO:false", "ESCUELA:true");
        JsonNode aceptacion = filaDelListado(solicitud.id(), sesion, "?status=APPROVED&size=200").get("acceptance");
        assertThat(aceptacion.get("durationDays").isNull()).isTrue();
        assertThat(aceptacion.get("role").asText()).isEqualTo("MENTOR");
    }

    @Test
    @DisplayName("Administración y Alquimista no reciben formaciones (ven las tres)")
    void administracionNoRecibeFormaciones() throws Exception {
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Admin Sin Formacion");

        assertThat(aprobar(solicitud.id(), sesion, Map.of("role", "ADMIN", "formations", List.of("DIPLOMADO"))).statusCode()).isEqualTo(204);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.formaciones_personal WHERE usuario_id = ?", Integer.class, solicitud.usuario())).isZero();
        assertThat(jdbc.queryForObject("SELECT formacion FROM renaser.participantes_programa WHERE usuario_id = ?", String.class, solicitud.usuario()))
                .isEqualTo("ESCUELA");
        assertThat(filaDelListado(solicitud.id(), sesion, "?status=APPROVED&size=200").get("acceptance").get("formations")).isEmpty();
    }

    @Test
    @DisplayName("FR-013: sin cuerpo (la app anterior) entra a la Escuela a 90 días y sin diagnóstico, y nunca llama a la ficha")
    void laAppAnteriorEntraALaEscuela() throws Exception {
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Aprendiz De Siempre");

        assertThat(aprobar(solicitud.id(), sesion, null).statusCode()).isEqualTo(204);

        assertThat(jdbc.queryForMap("SELECT formacion, duracion_dias FROM renaser.participantes_programa WHERE usuario_id = ?", solicitud.usuario()))
                .containsEntry("formacion", "ESCUELA").containsEntry("duracion_dias", (short) 90);
        JsonNode aceptacion = filaDelListado(solicitud.id(), sesion, "?status=APPROVED&size=200").get("acceptance");
        assertThat(aceptacion.get("formations").get(0).asText()).isEqualTo("ESCUELA");
        assertThat(aceptacion.get("withDiagnosis").asBoolean()).isFalse();
        verify(ficha, never()).conDiagnostico(any(), any());
    }

    @Test
    @DisplayName("un pedido inválido es 400 y deja la solicitud pendiente y la cuenta sin tocar: Diplomado sin duración, dos formaciones para un aprendiz, "
            + "una duración que no ofrece, una formación que no existe y una desactivada")
    void losPedidosInvalidosNoDejanNadaAMedias() throws Exception {
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Pedido Raro");
        jdbc.update("UPDATE renaser.formaciones SET activa = false WHERE codigo = 'CERTIFICACION'");

        List<Map<String, Object>> pedidos = List.of(
                Map.of("formations", List.of("DIPLOMADO")),
                Map.of("formations", List.of("ESCUELA", "CERTIFICACION")),
                Map.of("formations", List.of("ESCUELA"), "durationDays", 365),
                Map.of("formations", List.of("MAESTRIA")),
                Map.of("formations", List.of("CERTIFICACION")));
        for (Map<String, Object> pedido : pedidos) {
            assertThat(aprobar(solicitud.id(), sesion, pedido).statusCode()).as("pedido %s", pedido).isEqualTo(400);
        }

        assertThat(rolYEstado(solicitud.usuario())).containsEntry("rol", "APRENDIZ").containsEntry("estado", "INACTIVO");
        assertThat(jdbc.queryForObject("SELECT estado::text FROM renaser.solicitudes_cuenta WHERE id = ?", String.class, solicitud.id())).isEqualTo("PENDIENTE");
        assertThat(contar("participantes_programa", solicitud.usuario())).isZero();
    }

    @Test
    @DisplayName("FR-008: si la ficha falla, la aceptación entera se deshace: la cuenta sigue inactiva, sin inscripción y la solicitud pendiente")
    void siLaFichaFallaNoQuedaNadaCambiado() throws Exception {
        doThrow(new IllegalStateException("la ficha se cayó")).when(ficha).conDiagnostico(any(), any());
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Se Cae La Ficha");

        HttpResponse<String> respuesta = aprobar(solicitud.id(), sesion,
                Map.of("role", "MENTOR", "formations", List.of("ESCUELA", "DIPLOMADO"), "withDiagnosis", true));

        assertThat(respuesta.statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(rolYEstado(solicitud.usuario())).containsEntry("rol", "APRENDIZ").containsEntry("estado", "INACTIVO");
        assertThat(contar("participantes_programa", solicitud.usuario())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.formaciones_personal WHERE usuario_id = ?", Integer.class, solicitud.usuario())).isZero();
        assertThat(jdbc.queryForObject("SELECT estado::text FROM renaser.solicitudes_cuenta WHERE id = ?", String.class, solicitud.id())).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("SELECT formaciones_elegidas IS NULL FROM renaser.solicitudes_cuenta WHERE id = ?", Boolean.class, solicitud.id())).isTrue();
    }

    @Test
    @DisplayName("sin diagnóstico publicado, aceptar «con diagnóstico» es 409 y no acepta a nadie")
    void sinDiagnosticoPublicadoEs409() throws Exception {
        when(ficha.hayDiagnosticoPublicado()).thenReturn(false);
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Sin Diagnostico");

        assertThat(aprobar(solicitud.id(), sesion, Map.of("withDiagnosis", true)).statusCode()).isEqualTo(409);

        assertThat(rolYEstado(solicitud.usuario())).containsEntry("estado", "INACTIVO");
        verify(ficha, never()).conDiagnostico(any(), any());
    }

    @Test
    @DisplayName("FR-018 y SC-005: dos administradores aceptan la misma solicitud a la vez y queda exactamente una cuenta aceptada")
    void dosAceptacionesSimultaneas() throws Exception {
        String uno = sesionDe(usuario("ADMIN"));
        String otro = sesionDe(usuario("ALQUIMISTA"));
        Solicitud solicitud = solicitud("Doble Clic");

        CompletableFuture<HttpResponse<String>> primera = CompletableFuture.supplyAsync(() -> sinChequeo(() -> aprobar(solicitud.id(), uno, Map.of("formations", List.of("ESCUELA")))));
        CompletableFuture<HttpResponse<String>> segunda = CompletableFuture.supplyAsync(() -> sinChequeo(() -> aprobar(solicitud.id(), otro, Map.of("formations", List.of("CERTIFICACION")))));
        List<Integer> estados = List.of(primera.get().statusCode(), segunda.get().statusCode());

        assertThat(estados).as("una acepta y la otra recibe 409").containsExactlyInAnyOrder(204, 409);
        assertThat(contar("participantes_programa", solicitud.usuario())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado::text FROM renaser.solicitudes_cuenta WHERE id = ?", String.class, solicitud.id())).isEqualTo("APROBADA");
    }

    @Test
    @DisplayName("US4: el listado filtra por formación (las pendientes siempre se ven) y las aceptadas anteriores a las formaciones figuran como Escuela sin diagnóstico")
    void filtrarPorFormacion() throws Exception {
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud enDiplomado = solicitud("Filtro Diplomado");
        Solicitud enEscuela = solicitud("Filtro Escuela");
        Solicitud pendiente = solicitud("Filtro Pendiente");
        assertThat(aprobar(enDiplomado.id(), sesion, Map.of("formations", List.of("DIPLOMADO"), "durationDays", 90)).statusCode()).isEqualTo(204);
        assertThat(aprobar(enEscuela.id(), sesion, null).statusCode()).isEqualTo(204);

        List<UUID> delDiplomado = idsDelListado(sesion, "?formation=DIPLOMADO&size=200");
        assertThat(delDiplomado).contains(enDiplomado.id(), pendiente.id()).doesNotContain(enEscuela.id());
        List<UUID> aceptadasDelDiplomado = idsDelListado(sesion, "?status=APPROVED&formation=DIPLOMADO&size=200");
        assertThat(aceptadasDelDiplomado).contains(enDiplomado.id()).doesNotContain(enEscuela.id(), pendiente.id());
        assertThat(idsDelListado(sesion, "?status=APPROVED&formation=ESCUELA&size=200")).contains(enEscuela.id()).doesNotContain(enDiplomado.id());
        assertThat(aprobar(UUID.randomUUID(), sesion, null).statusCode()).isEqualTo(404);
        assertThat(enviar("GET", "/api/v1/account-requests?formation=MAESTRIA", sesion, null).statusCode()).isEqualTo(400);

        UUID anterior = solicitudAceptadaAntesDeLasFormaciones();
        JsonNode fila = filaDelListado(anterior, sesion, "?status=APPROVED&size=200");
        assertThat(fila.get("acceptance").get("formations").get(0).asText()).isEqualTo("ESCUELA");
        assertThat(fila.get("acceptance").get("withDiagnosis").asBoolean()).isFalse();
        assertThat(fila.get("acceptance").get("role").isNull()).isTrue();
    }

    @Test
    @DisplayName("las opciones de la hoja traen solo las formaciones activas con sus duraciones y si se ofrece el diagnóstico; un mentor no las ve (403)")
    void opcionesDeAceptacion() throws Exception {
        jdbc.update("UPDATE renaser.formaciones SET activa = false WHERE codigo = 'CERTIFICACION'");
        String sesion = sesionDe(usuario("ADMIN"));

        JsonNode opciones = json.readTree(enviar("GET", "/api/v1/account-requests/acceptance-options", sesion, null).body());

        List<String> codigos = new ArrayList<>();
        opciones.get("formations").forEach(f -> codigos.add(f.get("code").asText()));
        assertThat(codigos).containsExactly("ESCUELA", "DIPLOMADO");
        JsonNode diplomado = opciones.get("formations").get(1);
        assertThat(diplomado.get("durationsDays")).extracting(JsonNode::asInt).containsExactly(90, 365);
        assertThat(diplomado.get("durationLabels")).extracting(JsonNode::asText).containsExactly("3 meses", "1 año");
        assertThat(opciones.get("diagnosisAvailable").asBoolean()).isTrue();

        when(ficha.hayDiagnosticoPublicado()).thenReturn(false);
        assertThat(json.readTree(enviar("GET", "/api/v1/account-requests/acceptance-options", sesion, null).body()).get("diagnosisAvailable").asBoolean()).isFalse();

        for (String rol : List.of("MENTOR", "GUIA_APRENDIZ", "APRENDIZ")) {
            assertThat(enviar("GET", "/api/v1/account-requests/acceptance-options", sesionDe(usuario(rol)), null).statusCode()).as(rol).isEqualTo(403);
        }
        UUID suspendido = usuario("ADMIN");
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", suspendido);
        assertThat(enviar("GET", "/api/v1/account-requests/acceptance-options", sesionDe(suspendido), null).statusCode()).isEqualTo(403);
    }


    @Test
    @DisplayName("025-2 A: Administración cambia la duración de quien ya empezó, con motivo; conserva su día y el cambio queda en la bitácora del reloj")
    void cambiarLaDuracionDeQuienYaEmpezo() throws Exception {
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Cambia De Duracion");
        assertThat(aprobar(solicitud.id(), sesion, Map.of("formations", List.of("DIPLOMADO"), "durationDays", 90)).statusCode()).isEqualTo(204);
        jdbc.update("UPDATE renaser.participantes_programa SET fecha_inicio = CURRENT_DATE - 39, programa_activado_en = now() - interval '40 days', "
                + "dia_programa = 40 WHERE usuario_id = ?", solicitud.usuario());

        assertThat(enviar("POST", "/api/v1/admin/trainees/" + solicitud.usuario() + "/duracion", sesion,
                Map.of("duracionDias", 365, "motivo", "")).statusCode()).as("sin motivo").isEqualTo(400);
        assertThat(enviar("POST", "/api/v1/admin/trainees/" + solicitud.usuario() + "/duracion", sesion,
                Map.of("duracionDias", 120, "motivo", "Pidió más tiempo")).statusCode()).as("el Diplomado no ofrece 120").isEqualTo(400);
        HttpResponse<String> respuesta = enviar("POST", "/api/v1/admin/trainees/" + solicitud.usuario() + "/duracion", sesion,
                Map.of("duracionDias", 365, "motivo", "Pidió más tiempo"));

        assertThat(respuesta.statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT duracion_dias FROM renaser.participantes_programa WHERE usuario_id = ?", Integer.class, solicitud.usuario()))
                .isEqualTo(365);
        assertThat(jdbc.queryForMap("SELECT dia_anterior, dia_nuevo, duracion_anterior, duracion_nueva, motivo FROM renaser.ajustes_dia_programa "
                + "WHERE participante_id = ?", solicitud.usuario()))
                .containsEntry("duracion_anterior", (short) 90).containsEntry("duracion_nueva", (short) 365)
                .containsEntry("motivo", "Pidió más tiempo").containsEntry("dia_anterior", (short) 40).containsEntry("dia_nuevo", (short) 40);
        assertThat(enviar("POST", "/api/v1/admin/trainees/" + solicitud.usuario() + "/duracion", sesion,
                Map.of("duracionDias", 365, "motivo", "Otra vez")).statusCode()).as("ya tiene esa duración").isEqualTo(409);
        assertThat(enviar("POST", "/api/v1/admin/trainees/" + solicitud.usuario() + "/duracion", sesionDe(usuario("MENTOR")),
                Map.of("duracionDias", 90, "motivo", "No puede")).statusCode()).as("un mentor no").isEqualTo(403);
        JsonNode ficha = json.readTree(enviar("GET", "/api/v1/admin/trainees/" + solicitud.usuario(), sesion, null).body());
        assertThat(ficha.get("lastDayAdjustment").get("newDuration").asInt()).isEqualTo(365);
    }


    @Test
    @DisplayName("spec 027, FR-021: una formación sin términos publicados no recibe personas nuevas (409) y la hoja lo sabe; al publicarlos, sí")
    void sinTerminosNoSeAcepta() throws Exception {
        com.renaser.os.onboarding.TerminosDePrueba.restablecer(jdbc);
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Sin Terminos");

        HttpResponse<String> rechazada = aprobar(solicitud.id(), sesion, Map.of("formations", List.of("DIPLOMADO"), "durationDays", 90));

        assertThat(rechazada.statusCode()).isEqualTo(409);
        assertThat(rechazada.body()).contains("términos");
        assertThat(rolYEstado(solicitud.usuario())).containsEntry("estado", "INACTIVO");
        boolean diplomadoConTerminos = true;
        for (JsonNode formacion : json.readTree(enviar("GET", "/api/v1/account-requests/acceptance-options", sesion, null).body()).get("formations")) {
            if ("DIPLOMADO".equals(formacion.get("code").asText())) {
                diplomadoConTerminos = formacion.get("termsPublished").asBoolean();
            }
        }
        assertThat(diplomadoConTerminos).isFalse();
        // la Escuela sí tiene los suyos (la versión 1 de la migración): aceptar ahí no se traba
        Solicitud enLaEscuela = solicitud("Con Terminos");
        assertThat(aprobar(enLaEscuela.id(), sesion, Map.of("formations", List.of("ESCUELA"))).statusCode()).isEqualTo(204);

        com.renaser.os.onboarding.TerminosDePrueba.publicarEnTodas(jdbc);
        assertThat(aprobar(solicitud.id(), sesion, Map.of("formations", List.of("DIPLOMADO"), "durationDays", 90)).statusCode()).isEqualTo(204);
    }

    // ── Semilla y pedidos ──────────────────────────────────────────────────

    private record Solicitud(UUID id, UUID usuario) {
    }

    private Solicitud solicitud(String nombre) {
        String email = "acepta-" + UUID.randomUUID() + "@renaser.dev";
        String ip = "198.51.100." + ThreadLocalRandom.current().nextInt(1, 255);
        UUID id = submit.submit(SubmitAccountRequestCommand.porFormulario(email, nombre, "+51 999 888 777", "Lima",
                tokens.generar(email, Duration.ofMinutes(30)), CONTRASENA, ip)).value();
        UUID usuario = jdbc.queryForObject("SELECT usuario_id FROM renaser.solicitudes_cuenta WHERE id = ?", UUID.class, id);
        cuentas.add(usuario);
        return new Solicitud(id, usuario);
    }

    /** Una solicitud como las de antes de las formaciones: aceptada, con la foto que V140 les puso a todas. */
    private UUID solicitudAceptadaAntesDeLasFormaciones() {
        Solicitud solicitud = solicitud("Aceptada De Antes");
        jdbc.update("UPDATE renaser.solicitudes_cuenta SET estado = 'APROBADA', formaciones_elegidas = ARRAY['ESCUELA'], con_diagnostico = false, "
                + "duracion_elegida_dias = NULL, rol_elegido = NULL, revisada_en = now() WHERE id = ?", solicitud.id());
        return solicitud.id();
    }

    private UUID usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", "Fixture " + rol, rol);
        cuentas.add(id);
        return id;
    }

    private UUID recepcion(String formacion) {
        if (cohorte == null) {
            cohorte = UUID.randomUUID();
            jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte de aceptar con formación', DATE '2026-10-01')", cohorte);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, formacion) VALUES (?, ?, ?, CAST('RECEPCION' AS renaser.tipo_celula), ?)",
                id, "Recepción de prueba " + formacion, cohorte, formacion);
        grupos.add(id);
        return id;
    }

    private int asignaciones(UUID usuario, UUID celula) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.asignaciones_celula WHERE usuario_id = ? AND celula_id = ? AND fin IS NULL",
                Integer.class, usuario, celula);
    }

    private HttpResponse<String> aprobar(UUID solicitud, String sesion, Map<String, ?> cuerpo) throws Exception {
        return enviar("POST", "/api/v1/account-requests/" + solicitud + "/approve", sesion, cuerpo);
    }

    private JsonNode filaDelListado(UUID solicitud, String sesion, String consulta) throws Exception {
        JsonNode contenido = json.readTree(enviar("GET", "/api/v1/account-requests" + consulta, sesion, null).body()).get("content");
        for (JsonNode fila : contenido) {
            if (solicitud.toString().equals(fila.get("id").asText())) {
                return fila;
            }
        }
        throw new AssertionError("La solicitud " + solicitud + " no figura en el listado");
    }

    private List<UUID> idsDelListado(String sesion, String consulta) throws Exception {
        List<UUID> ids = new ArrayList<>();
        json.readTree(enviar("GET", "/api/v1/account-requests" + consulta, sesion, null).body()).get("content")
                .forEach(fila -> ids.add(UUID.fromString(fila.get("id").asText())));
        return ids;
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String sesion, Map<String, ?> cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                .header("X-Auth-Token", sesion);
        if (cuerpo != null) {
            pedido.header("Content-Type", "application/json");
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private interface Pedido {
        HttpResponse<String> hacer() throws Exception;
    }

    private static HttpResponse<String> sinChequeo(Pedido pedido) {
        try {
            return pedido.hacer();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> rolYEstado(UUID id) {
        return jdbc.queryForMap("SELECT rol::text AS rol, estado::text AS estado FROM renaser.usuarios WHERE id = ?", id);
    }

    private int contar(String tabla, UUID usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser." + tabla + " WHERE usuario_id = ?", Integer.class, usuario);
    }

    private void esperarLosAvisosDeEstaPrueba() throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (avisosPendientes() > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
        }
        assertThat(avisosPendientes()).as("publicaciones de esta prueba sin completar en el outbox").isZero();
    }

    private int avisosPendientes() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE completion_date IS NULL AND publication_date >= ?",
                Integer.class, java.sql.Timestamp.from(inicio));
    }

    private String sesionDe(UUID usuario) {
        return guardarSesion(sesiones, usuario);
    }

    private static <S extends Session> String guardarSesion(SessionRepository<S> repositorio, UUID usuario) {
        S sesion = repositorio.createSession();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(usuario.toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion);
        return sesion.getId();
    }
}
