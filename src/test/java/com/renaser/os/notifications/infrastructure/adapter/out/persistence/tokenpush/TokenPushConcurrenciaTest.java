package com.renaser.os.notifications.infrastructure.adapter.out.persistence.tokenpush;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresion de C-10 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): la app
 * movil reintenta {@code POST /push-tokens} con el MISMO token si el primer intento tarda o
 * el response se pierde — dos registros casi simultaneos del mismo token no deben terminar en
 * un 409 ni duplicar la fila. Requiere Postgres real: la carrera contra el
 * {@code UNIQUE(token)} no se puede probar con mocks.
 *
 * <p>Deliberadamente SIN {@code @Transactional} de clase (mismo motivo que
 * {@code RocaDiariaConcurrenciaTest}): los hilos del {@code ExecutorService} necesitan su
 * propia conexion/transaccion para competir de verdad contra la base.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TokenPushConcurrenciaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-01T10:00:00Z"));
    private static final int INTENTOS_CONCURRENTES = 6;

    @Autowired
    private TokenPushPersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID usuarioId;

    @BeforeEach
    void seedUsuario() {
        usuarioId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))
                        """,
                usuarioId, usuarioId + "@renaser.test");
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra tokens_push.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", usuarioId);
    }

    @Test
    @DisplayName("C-10: N registros concurrentes del MISMO token -> una sola fila, sin 409")
    void registrosConcurrentesDelMismoTokenNoDuplicanNiFallan() throws InterruptedException {
        String token = "expo-tok-concurrente-" + usuarioId;
        ExecutorService pool = Executors.newFixedThreadPool(INTENTOS_CONCURRENTES);
        List<Future<TokenPush>> resultados;
        try {
            List<Callable<TokenPush>> intentos = IntStream.range(0, INTENTOS_CONCURRENTES)
                    .<Callable<TokenPush>>mapToObj(i -> () -> adapter.upsertPorToken(
                            TokenPush.registrar(TokenPushId.of(UUID.randomUUID()), UserId.of(usuarioId), token,
                                    PlataformaPush.IOS, CLOCK)))
                    .toList();
            resultados = pool.invokeAll(intentos, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        // El bug (C-10): con findByToken + decidir INSERT/UPDATE en Java, la mitad de estos
        // intentos chocaba contra el UNIQUE(token) -> DataIntegrityViolationException (409).
        List<TokenPush> registrados = resultados.stream().map(this::obtenerSinFallar).toList();
        assertThat(registrados.stream().map(t -> t.id().value()).distinct())
                .as("todos los intentos terminan viendo la MISMA fila").hasSize(1);

        Long filas = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM renaser.tokens_push WHERE token = ?",
                Long.class, token);
        assertThat(filas).as("una sola fila para el token, pese a %s registros concurrentes",
                INTENTOS_CONCURRENTES).isEqualTo(1);
    }

    private TokenPush obtenerSinFallar(Future<TokenPush> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Fallo inesperado en un registro concurrente (era justo lo que C-10 arreglaba)", e);
        }
    }
}
