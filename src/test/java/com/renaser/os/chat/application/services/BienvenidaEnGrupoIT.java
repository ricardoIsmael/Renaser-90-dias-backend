package com.renaser.os.chat.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnGrupoUseCase;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La bienvenida del mentor en el chat del grupo estable contra Postgres de verdad (D-191, V71).
 *
 * <p>Lo que un doble no puede probar: que el {@code UPDATE … WHERE bienvenida_enviada_en IS NULL}
 * frena de verdad a una segunda entrega (también cruzada, en dos hilos), que la columna existe y que
 * V71 marca a los que ya estaban en un grupo al desplegar.
 *
 * <p>Se llama al caso de uso directo, sin el evento: la semilla es SQL y no publica nada. Sin
 * {@code @Transactional} de clase: el caso de uso abre las suyas y hay que ver lo comprometido.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BienvenidaEnGrupoIT {

    @Autowired
    private DarBienvenidaEnGrupoUseCase darBienvenidas;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;

    private UUID cohorteId;
    private final List<UUID> celulas = new ArrayList<>();
    private final List<UUID> usuarios = new ArrayList<>();

    @BeforeEach
    void seed() {
        celulas.clear();
        usuarios.clear();
        cohorteId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte bienvenida', CURRENT_DATE - 10)",
                cohorteId);
    }

    @AfterEach
    void limpiar() {
        celulas.forEach(id -> jdbcTemplate.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id));
        celulas.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorteId);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
    }

    @Test
    @DisplayName("un aprendiz nuevo en un grupo estable con mentor recibe UN mensaje del mentor; la reentrega no lo repite")
    void unMensajeYLaReentregaNoDuplica() {
        UUID grupo = grupo("REGULAR");
        UUID mentor = usuario("MENTOR", "Carlos Ramírez");
        UUID ana = usuario("APRENDIZ", "Ana Pérez");
        asignar(grupo, mentor, "MENTOR");
        UUID asignacion = asignar(grupo, ana, "APRENDIZ");

        assertThat(darBienvenidas.darBienvenidas(grupo)).isEqualTo(1);
        assertThat(darBienvenidas.darBienvenidas(grupo)).as("reentrega").isZero();

        List<String> textos = jdbcTemplate.queryForList("""
                SELECT m.texto FROM renaser.mensajes m JOIN renaser.conversaciones c ON c.id = m.conversacion_id
                WHERE c.celula_id = ? AND m.emisor_id = ?
                """, String.class, grupo, mentor);
        assertThat(textos).singleElement().satisfies(t -> assertThat(t).contains("Ana").contains("Carlos")
                .doesNotContain("{nombre}").doesNotContain("{mentor}"));
        assertThat(marca(asignacion)).isTrue();
    }

    @Test
    @DisplayName("dos entregas cruzadas en dos hilos: un solo mensaje")
    void entregasCruzadasNoDuplican() throws Exception {
        UUID grupo = grupo("REGULAR");
        UUID mentor = usuario("MENTOR", "Carlos Ramírez");
        asignar(grupo, mentor, "MENTOR");
        asignar(grupo, usuario("APRENDIZ", "Ana Pérez"), "APRENDIZ");

        CountDownLatch largada = new CountDownLatch(1);
        CompletableFuture<Integer> uno = CompletableFuture.supplyAsync(() -> esperarYDar(largada, grupo));
        CompletableFuture<Integer> otro = CompletableFuture.supplyAsync(() -> esperarYDar(largada, grupo));
        largada.countDown();

        assertThat(uno.get() + otro.get()).isEqualTo(1);
        assertThat(mensajesDelGrupo(grupo)).isEqualTo(1);
    }

    @Test
    @DisplayName("la recepción no recibe bienvenida de grupo y su marca queda NULL")
    void recepcionNo() {
        UUID recepcion = grupo("RECEPCION");
        asignar(recepcion, usuario("MENTOR", "Guía Uno"), "GUIA");
        UUID asignacion = asignar(recepcion, usuario("APRENDIZ", "Ana Pérez"), "APRENDIZ");

        assertThat(darBienvenidas.darBienvenidas(recepcion)).isZero();
        assertThat(mensajesDelGrupo(recepcion)).isZero();
        assertThat(marca(asignacion)).isFalse();
    }

    @Test
    @DisplayName("sin mentor no se manda y queda pendiente; cuando el grupo tiene mentor, sale")
    void sinMentorQuedaPendiente() {
        UUID grupo = grupo("REGULAR");
        UUID asignacion = asignar(grupo, usuario("APRENDIZ", "Ana Pérez"), "APRENDIZ");

        assertThat(darBienvenidas.darBienvenidas(grupo)).isZero();
        assertThat(marca(asignacion)).isFalse();

        asignar(grupo, usuario("MENTOR", "Carlos Ramírez"), "MENTOR");
        assertThat(darBienvenidas.darBienvenidas(grupo)).isEqualTo(1);
        assertThat(marca(asignacion)).isTrue();
    }

    @Test
    @DisplayName("V71 marca como ya bienvenidas las pertenencias de aprendiz que existían, y no las de mentor")
    void laMigracionMarcaLasExistentes() {
        HikariDataSource principal = (HikariDataSource) dataSource;
        String base = "renaser_v71_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("CREATE DATABASE " + base);
        try {
            String url = principal.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + base + "$1");
            DriverManagerDataSource aparte = new DriverManagerDataSource(url, principal.getUsername(), principal.getPassword());
            migrarHasta(aparte, "70");
            JdbcTemplate jdbc = new JdbcTemplate(aparte);
            UUID cohorte = UUID.randomUUID();
            UUID celula = UUID.randomUUID();
            UUID aprendiz = UUID.randomUUID();
            UUID mentor = UUID.randomUUID();
            jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'C', CURRENT_DATE)", cohorte);
            jdbc.update("INSERT INTO renaser.celulas (id, nombre, cohorte_id) VALUES (?, 'G', ?)", celula, cohorte);
            for (UUID u : List.of(aprendiz, mentor)) {
                jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                        + "VALUES (?, ?, 'X', 'APRENDIZ', 'ACTIVO')", u, u + "@renaser.test");
            }
            UUID asigAprendiz = asignarEn(jdbc, celula, aprendiz, "APRENDIZ");
            UUID asigMentor = asignarEn(jdbc, celula, mentor, "MENTOR");

            migrarHasta(aparte, "71");

            assertThat(jdbc.queryForObject("SELECT bienvenida_enviada_en IS NOT NULL FROM renaser.asignaciones_celula WHERE id = ?",
                    Boolean.class, asigAprendiz)).as("el aprendiz que ya estaba").isTrue();
            assertThat(jdbc.queryForObject("SELECT bienvenida_enviada_en IS NULL FROM renaser.asignaciones_celula WHERE id = ?",
                    Boolean.class, asigMentor)).as("el mentor no lleva marca").isTrue();
        } finally {
            jdbcTemplate.execute("DROP DATABASE IF EXISTS " + base + " WITH (FORCE)");
        }
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private int esperarYDar(CountDownLatch largada, UUID grupo) {
        try {
            largada.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return darBienvenidas.darBienvenidas(grupo);
    }

    private static void migrarHasta(DataSource ds, String version) {
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(version).load().migrate();
    }

    private UUID grupo(String tipo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, 'Fenix', ?, CAST(? AS renaser.tipo_celula), CURRENT_DATE - 5, CURRENT_DATE + 20)
                """, id, cohorteId, tipo);
        jdbcTemplate.update("INSERT INTO renaser.conversaciones (id, tipo, celula_id, nombre) VALUES (?, 'CELULA', ?, 'Fenix')",
                UUID.randomUUID(), id);
        celulas.add(id);
        return id;
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

    private UUID asignar(UUID celula, UUID usuario, String funcion) {
        return asignarEn(jdbcTemplate, celula, usuario, funcion);
    }

    private static UUID asignarEn(JdbcTemplate jdbc, UUID celula, UUID usuario, String funcion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), now() - interval '1 hour',
                        'ADMINISTRATIVO', ?)
                """, id, celula, usuario, funcion, "prueba|" + id);
        return id;
    }

    private boolean marca(UUID asignacion) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT bienvenida_enviada_en IS NOT NULL FROM renaser.asignaciones_celula WHERE id = ?",
                Boolean.class, asignacion));
    }

    private int mensajesDelGrupo(UUID celula) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM renaser.mensajes m JOIN renaser.conversaciones c ON c.id = m.conversacion_id
                WHERE c.celula_id = ?
                """, Integer.class, celula);
        return n == null ? 0 : n;
    }
}
