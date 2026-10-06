package com.renaser.os.onboarding.infrastructure.adapter.in.rest.respuesta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-80 (2026-10-06) de punta a punta: {@code POST /api/v1/onboarding/answers} con la respuesta {@code birth_date}
 * tal como la manda la app ({@code textValue} ISO). Antes de este cambio el servidor guardaba cualquier fecha (la
 * app vieja deja elegir desde los 14 años); ahora la de alguien sin 18 cumplidos en el día de Lima sale 400 con un
 * mensaje claro y no pisa la que ya estuviera guardada.
 *
 * <p>Usa el reloj real: las fechas se calculan con el día de Lima de este momento, que es el mismo que mira el
 * dominio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FechaDeNacimientoMayorDeEdadIT {

    private static final String RUTA = "/api/v1/onboarding/answers";
    private static final String RECHAZO = "Renaser es solo para mayores de 18 años";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private UUID ana;
    private int preguntaFecha;
    private LocalDate hoyEnLima;

    @BeforeEach
    void seed() {
        ana = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Ana Pérez', 'APRENDIZ', 'ACTIVO')
                """, ana, ana + "@renaser.test");
        preguntaFecha = jdbcTemplate.queryForObject(
                "SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = 'birth_date'", Integer.class);
        hoyEnLima = LocalDate.now(ZoneId.of("America/Lima"));
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra respuestas_onboarding.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", ana);
    }

    @Test
    @DisplayName("17 años y 364 días: 400 «Renaser es solo para mayores de 18 años» y no queda nada guardado")
    void menorPorUnDiaSeRechaza() throws Exception {
        HttpResponse<String> respuesta = responder(hoyEnLima.minusYears(18).plusDays(1));

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(400);
        assertThat(json.readTree(respuesta.body()).get("message").asText()).isEqualTo(RECHAZO);
        assertThat(fechaGuardada()).isEmpty();
    }

    @Test
    @DisplayName("18 justos hoy: 200 y la fecha queda guardada")
    void dieciochoJustosSeGuarda() throws Exception {
        LocalDate nacimiento = hoyEnLima.minusYears(18);

        HttpResponse<String> respuesta = responder(nacimiento);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(fechaGuardada()).containsExactly(nacimiento.toString());
    }

    @Test
    @DisplayName("la app vieja manda 14 años sobre una fecha ya guardada: 400 y la guardada no cambia")
    void unaFechaDeMenorNoPisaLaGuardada() throws Exception {
        assertThat(responder(LocalDate.of(1995, 6, 15)).statusCode()).isEqualTo(200);

        HttpResponse<String> respuesta = responder(hoyEnLima.minusYears(14));

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(400);
        assertThat(fechaGuardada()).containsExactly("1995-06-15");
    }

    private HttpResponse<String> responder(LocalDate nacimiento) throws Exception {
        String cuerpo = "{\"questionId\":" + preguntaFecha + ",\"textValue\":\"" + nacimiento + "\"}";
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA))
                .header("X-Auth-Token", guardarSesion(sesiones, ana))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(pedido, HttpResponse.BodyHandlers.ofString());
    }

    private List<String> fechaGuardada() {
        return jdbcTemplate.queryForList("""
                SELECT valor_texto FROM renaser.respuestas_onboarding WHERE usuario_id = ? AND pregunta_id = ?
                """, String.class, ana, preguntaFecha);
    }

    /** Como la deja el login real ({@code SesionWebAdapter}): el id del usuario como nombre. */
    private static <S extends Session> String guardarSesion(SessionRepository<S> repositorio, UUID usuario) {
        S sesion = repositorio.createSession();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(usuario.toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion);
        return sesion.getId();
    }
}
