package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los integrantes de cada chat de punta a punta sobre el Tomcat real: sesión de verdad, Spring Security
 * delante y Postgres con la comunidad, un grupo con su mentor, un soporte y un 1 a 1.
 * <ul>
 *   <li>que los ven el aprendiz, el mentor y el ADMIN/ALCHEMIST de cada chat que pueden ver, con su rol;</li>
 *   <li>autorización negativa: quien no ve el chat no ve a sus integrantes, un ex integrante ni aparece ni
 *       entra, una cuenta suspendida y quien no tiene sesión reciben 403, y un chat inexistente 404.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ParticipantesDelChatIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> conversaciones = new ArrayList<>();

    private UUID ana;
    private UUID beto;
    private UUID luis;
    private UUID exAlumno;
    private UUID ricardo;
    private UUID kelin;
    private UUID zoe;
    private UUID ajeno;
    private UUID soporte;
    private UUID directa;
    private UUID global;
    private UUID cohorte;
    private UUID grupo;
    private UUID chatDelGrupo;

    @BeforeEach
    void seed() {
        ana = usuario("APRENDIZ", "Ana Pérez");
        beto = usuario("APRENDIZ", "Beto Díaz");
        luis = usuario("APRENDIZ", "Luis Soto");
        exAlumno = usuario("APRENDIZ", "Ex Alumno");
        ricardo = usuario("MENTOR", "Ricardo Palomino");
        kelin = usuario("ADMIN", "Kelin Rojas");
        zoe = usuario("ALQUIMISTA", "Zoe Alquimia");
        ajeno = usuario("ADMIN", "Admin Sin Grupo");
        soporte = conversacion("SOPORTE", "soporte:" + ana, null, "Ana – Formación Renaser");
        participa(soporte, ana);
        participa(soporte, kelin);
        participa(soporte, zoe);
        directa = conversacion("DIRECTA", ana + "_" + luis, null, null);
        participa(directa, ana);
        participa(directa, luis);
        global = conversacionGlobal();
        List.of(ana, beto, luis, ricardo, kelin, zoe).forEach(u -> participa(global, u));
        grupoConMentor();
    }

    /** «Fénix»: mentor Ricardo, aprendices Ana y Beto, Kelin de soporte; Ex Alumno estuvo y salió. */
    private void grupoConMentor() {
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", ricardo);
        cohorte = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte integrantes', CURRENT_DATE - 10)",
                cohorte);
        grupo = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, mentor_id)
                VALUES (?, 'Fenix', ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 5, CURRENT_DATE + 20, ?)
                """, grupo, cohorte, ricardo);
        chatDelGrupo = conversacion("CELULA", null, grupo, "Fenix");
        asignar(ricardo, "MENTOR", "now() - interval '1 hour'", null);
        asignar(ana, "APRENDIZ", "now() - interval '1 hour'", null);
        asignar(beto, "APRENDIZ", "now() - interval '1 hour'", null);
        asignar(kelin, "SOPORTE", "now() - interval '1 hour'", null);
        asignar(exAlumno, "APRENDIZ", "now() - interval '2 days'", "now() - interval '1 day'");
        List.of(ricardo, ana, beto, kelin, exAlumno).forEach(u -> participa(chatDelGrupo, u));
    }

    @AfterEach
    void limpiar() {
        conversaciones.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        jdbcTemplate.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", grupo);
        jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", grupo);
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("grupo: el aprendiz, el mentor y el staff del grupo ven a los mismos: mentor, staff y aprendices, con su rol; el ex integrante no")
    void unGrupoLoVenTodosLosQuePertenecen() throws Exception {
        for (UUID quien : List.of(ana, ricardo, kelin)) {
            JsonNode respuesta = obtener(ruta(chatDelGrupo, ""), sesionDe(quien));

            assertThat(nombres(respuesta)).as("visto por " + quien)
                    .containsExactly("Ricardo Palomino", "Kelin Rojas", "Ana Pérez", "Beto Díaz");
            assertThat(roles(respuesta)).containsExactly("MENTOR", "ADMIN", "APRENDIZ", "APRENDIZ");
            assertThat(respuesta.get("total").asInt()).isEqualTo(4);
        }
        JsonNode deAna = obtener(ruta(chatDelGrupo, ""), sesionDe(ana));
        assertThat(deAna.get("participants").get(2).get("esUnoMismo").asBoolean()).isTrue();
        assertThat(deAna.get("participants").get(3).get("esUnoMismo").asBoolean()).isFalse();
        assertThat(deAna.toString()).doesNotContain("@renaser.test").doesNotContain("email");
    }

    @Test
    @DisplayName("grupo: la ruta de cada tarjeta sirve la tarjeta de verdad")
    void lasTarjetasDelGrupoSirven() throws Exception {
        JsonNode respuesta = obtener(ruta(chatDelGrupo, ""), sesionDe(kelin));

        for (JsonNode fila : respuesta.get("participants")) {
            String rutaDeFoto = fila.get("fotoPath").asText();
            assertThat(rutaDeFoto).isEqualTo("/api/v1/chat/conversations/" + chatDelGrupo + "/miembros/"
                    + fila.get("userId").asText() + "/foto");
            assertThat(pedir(rutaDeFoto, sesionDe(kelin)).statusCode()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("grupo, autorización negativa: 403 a quien no pertenece (otro aprendiz, un ADMIN sin grupo), al ex integrante y sin sesión")
    void unGrupoNoLoVeQuienNoPertenece() throws Exception {
        assertThat(pedir(ruta(chatDelGrupo, ""), sesionDe(luis)).statusCode()).as("otro aprendiz").isEqualTo(403);
        assertThat(pedir(ruta(chatDelGrupo, ""), sesionDe(ajeno)).statusCode()).as("ADMIN sin asignación").isEqualTo(403);
        assertThat(pedir(ruta(chatDelGrupo, ""), sesionDe(exAlumno)).statusCode())
                .as("su fila de participante sigue, su pertenencia no").isEqualTo(403);
        assertThat(pedir(ruta(chatDelGrupo, ""), null).statusCode()).as("sin sesión").isEqualTo(403);
    }

    @Test
    @DisplayName("soporte: la aprendiz y el staff ven a la aprendiz primero y luego al ADMIN y al ALQUIMISTA; otro aprendiz, 403")
    void unSoporte() throws Exception {
        JsonNode deAna = obtener(ruta(soporte, ""), sesionDe(ana));
        JsonNode deZoe = obtener(ruta(soporte, ""), sesionDe(zoe));

        assertThat(nombres(deAna)).containsExactly("Ana Pérez", "Kelin Rojas", "Zoe Alquimia");
        assertThat(roles(deAna)).containsExactly("APRENDIZ", "ADMIN", "ALQUIMISTA");
        assertThat(nombres(deZoe)).isEqualTo(nombres(deAna));
        assertThat(pedir(ruta(soporte, ""), sesionDe(luis)).statusCode()).as("el soporte de otro").isEqualTo(403);
        assertThat(pedir(ruta(soporte, ""), sesionDe(ricardo)).statusCode()).as("un mentor no es staff").isEqualTo(403);
    }

    @Test
    @DisplayName("soporte: quien ya no es ADMIN/ALCHEMIST no figura aunque conserve su fila, y ya no lo ve")
    void unSoporteConUnStaffDegradado() throws Exception {
        jdbcTemplate.update("UPDATE renaser.usuarios SET rol = CAST('MENTOR' AS renaser.rol_usuario) WHERE id = ?", kelin);

        assertThat(nombres(obtener(ruta(soporte, ""), sesionDe(ana)))).containsExactly("Ana Pérez", "Zoe Alquimia");
        assertThat(pedir(ruta(soporte, ""), sesionDe(kelin)).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("comunidad: todos por nombre para cualquier participante, con búsqueda y páginas; sin tarjeta pero con su foto; el que no es participante, 403")
    void laComunidad() throws Exception {
        jdbcTemplate.update("UPDATE renaser.usuarios SET avatar_url = 'https://fotos/ana.jpg' WHERE id = ?", ana);

        JsonNode toda = obtener(ruta(global, ""), sesionDe(luis));
        assertThat(nombres(toda)).containsExactly("Ana Pérez", "Beto Díaz", "Kelin Rojas", "Luis Soto",
                "Ricardo Palomino", "Zoe Alquimia");
        assertThat(toda.get("participants").get(0).get("fotoPath").isNull()).isTrue();
        assertThat(toda.get("participants").get(0).get("avatarUrl").asText()).isEqualTo("https://fotos/ana.jpg");

        JsonNode buscada = obtener(ruta(global, "?q=PEREZ"), sesionDe(kelin));
        assertThat(nombres(buscada)).containsExactly("Ana Pérez");
        assertThat(buscada.get("total").asInt()).isEqualTo(1);

        JsonNode segunda = obtener(ruta(global, "?page=1&size=4"), sesionDe(zoe));
        assertThat(nombres(segunda)).containsExactly("Ricardo Palomino", "Zoe Alquimia");
        assertThat(segunda.get("total").asInt()).isEqualTo(6);

        assertThat(pedir(ruta(global, ""), sesionDe(ajeno)).statusCode()).as("no está en la comunidad").isEqualTo(403);
    }

    @Test
    @DisplayName("1 a 1: las dos personas; un tercero, 403; un chat que no existe, 404")
    void unoAUnoYLosRechazos() throws Exception {
        assertThat(nombres(obtener(ruta(directa, ""), sesionDe(ana)))).containsExactly("Ana Pérez", "Luis Soto");
        assertThat(pedir(ruta(directa, ""), sesionDe(beto)).statusCode()).isEqualTo(403);
        assertThat(pedir(ruta(UUID.randomUUID(), ""), sesionDe(ana)).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("una cuenta suspendida recibe 403 aunque su sesión sea válida, en cualquier chat")
    void unaCuentaSuspendida() throws Exception {
        String sesionDeAna = sesionDe(ana);
        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", ana);

        assertThat(pedir(ruta(chatDelGrupo, ""), sesionDeAna).statusCode()).isEqualTo(403);
        assertThat(pedir(ruta(global, ""), sesionDeAna).statusCode()).isEqualTo(403);
        assertThat(pedir(ruta(soporte, ""), sesionDeAna).statusCode()).isEqualTo(403);
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private static String ruta(UUID conversacion, String consulta) {
        return "/api/v1/chat/conversations/" + conversacion + "/participants" + consulta;
    }

    private List<String> nombres(JsonNode respuesta) {
        List<String> nombres = new ArrayList<>();
        respuesta.get("participants").forEach(f -> nombres.add(f.get("nombre").asText()));
        return nombres;
    }

    private List<String> roles(JsonNode respuesta) {
        List<String> roles = new ArrayList<>();
        respuesta.get("participants").forEach(f -> roles.add(f.get("rol").asText()));
        return roles;
    }

    private JsonNode obtener(String ruta, String sesion) throws Exception {
        HttpResponse<byte[]> respuesta = pedir(ruta, sesion);
        assertThat(respuesta.statusCode()).as(ruta).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<byte[]> pedir(String ruta, String sesion) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        return http.send(pedido.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
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

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        usuarios.add(id);
        return id;
    }

    private UUID conversacion(String tipo, String clave, UUID celula, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa, celula_id, nombre)
                VALUES (?, CAST(? AS renaser.tipo_conversacion), ?, ?, ?)
                """, id, tipo, clave, celula, nombre);
        conversaciones.add(id);
        return id;
    }

    /** La GLOBAL es única por índice parcial: si el arranque ya creó una, se reusa (y no se borra). Sus participantes reales, no se tocan: se buscan por nombre. */
    private UUID conversacionGlobal() {
        List<UUID> existente = jdbcTemplate.queryForList("SELECT id FROM renaser.conversaciones WHERE tipo = 'GLOBAL'", UUID.class);
        return existente.isEmpty() ? conversacion("GLOBAL", null, null, "Comunidad") : existente.get(0);
    }

    private void participa(UUID conversacion, UUID usuario) {
        jdbcTemplate.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                conversacion, usuario);
    }

    private void asignar(UUID usuario, String funcion, String inicio, String fin) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), %s, %s, 'ADMINISTRATIVO', ?)
                """.formatted(inicio, fin == null ? "NULL" : fin), id, grupo, usuario, funcion, "prueba|" + id);
    }
}
