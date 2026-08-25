package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.api.SantuarioRotoEvent;
import com.renaser.os.rocks.api.RocaCompletadaEvent;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>La prueba de punta a punta de que el outbox de Spring Modulith funciona</b>
 * (docs/MODULO_NOTIFICATIONS.md §0, el objetivo central de este modulo — es el primer
 * consumidor real de eventos de dominio de otro modulo).
 *
 * <p>Publica el evento REAL (mismo tipo, mismos campos que publican `habits`/`rocks` — se
 * importa directamente {@code habits.api.SantuarioRotoEvent}/{@code rocks.api.RocaCompletadaEvent},
 * sin duplicar ni inventar un tipo propio) a traves de {@link TransactionalEventPublisherTestHelper}
 * (para que el publish quede dentro de una transaccion que SI hace commit — {@code
 * @ApplicationModuleListener} corre en fase AFTER_COMMIT, ver su javadoc) y espera (poll corto,
 * sin Awaitility en el classpath) a que aparezca la fila en {@code renaser.notificaciones}.
 *
 * <p><b>Que prueba esto y que NO prueba:</b> prueba que un evento publicado con
 * {@code ApplicationEventPublisher} llega a nuestro {@code @ApplicationModuleListener}, pasa por
 * {@code EmitirNotificacionUseCase} y queda persistido — el circuito completo del outbox de
 * Modulith (tabla {@code event_publication} + reintento + entrega async post-commit) dentro de
 * este modulo. NO reverifica que `habits`/`rocks` disparen estos eventos en sus propios casos de
 * uso reales (eso es responsabilidad de sus propios tests, ya documentada en
 * `docs/MODULO_HABITS.md`/`docs/MODULO_ROCKS.md`) — importar el evento desde otro modulo para
 * publicarlo a mano en un test es seguro porque {@code habits.api}/{@code rocks.api} son paquetes
 * {@code @NamedInterface}, no fuga tipos internos.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificationsEventOutboxTest {

    @Autowired
    private TransactionalEventPublisherTestHelper publisherHelper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID usuarioId;

    @BeforeEach
    void crearPrerrequisitos() {
        usuarioId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol)
                VALUES (?, ?, 'Aprendiz de Prueba', 'APRENDIZ')
                """, usuarioId, "aprendiz-" + usuarioId + "@renaser.com");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void unSantuarioRotoDeHabitsTerminaEnLaBandejaDeNotificaciones() throws InterruptedException {
        publisherHelper.publicarYConfirmar(
                new SantuarioRotoEvent(UUID.randomUUID(), UserId.of(usuarioId), Instant.now()));

        List<String> tipos = esperarNotificacionesDe(usuarioId);

        assertThat(tipos).contains("SANTUARIO_ROTO");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void unaRocaCompletadaDeRocksTerminaEnLaBandejaDeNotificaciones() throws InterruptedException {
        publisherHelper.publicarYConfirmar(
                new RocaCompletadaEvent(UUID.randomUUID(), UserId.of(usuarioId), Instant.now()));

        List<String> tipos = esperarNotificacionesDe(usuarioId);

        assertThat(tipos).contains("HITO_PROGRAMA");
    }

    /** Poll corto (sin Awaitility, no esta en el classpath): el listener corre async
     * post-commit, asi que la fila puede tardar unos milisegundos en aparecer. */
    private List<String> esperarNotificacionesDe(UUID usuarioId) throws InterruptedException {
        long limite = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < limite) {
            List<String> tipos = jdbcTemplate.queryForList(
                    "select tipo::text from renaser.notificaciones where usuario_id = ?", String.class, usuarioId);
            if (!tipos.isEmpty()) {
                return tipos;
            }
            Thread.sleep(200);
        }
        return List.of();
    }
}
