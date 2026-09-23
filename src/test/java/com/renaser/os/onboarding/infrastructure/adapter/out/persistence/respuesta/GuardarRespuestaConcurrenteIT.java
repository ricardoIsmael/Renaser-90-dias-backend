package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-213: dos guardados SIMULTANEOS de la misma pregunta, contra Postgres de verdad.
 *
 * <p>El Mapa guarda la prioridad por dos caminos a la vez. Con el upsert viejo —buscar y, si no
 * existe, insertar— los dos pedidos veian "no existe" y el segundo moria contra el UNIQUE
 * {@code (usuario_id, pregunta_id)} con un 409. Esto no se puede probar dentro de una sola
 * transaccion ni con dobles: hacen falta dos conexiones compitiendo de verdad.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Guardar la misma respuesta en paralelo (E-213)")
class GuardarRespuestaConcurrenteIT {

    private static final int HILOS = 8;
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-23T15:00:00Z"));

    @Autowired
    private RespuestaPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate enTransaccion;

    private UUID usuarioId;
    private short seccionId;
    private int preguntaId;

    @BeforeEach
    void sembrar() {
        usuarioId = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, 'Prueba', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')",
                usuarioId, usuarioId + "@prueba.test");
        seccionId = jdbc.queryForObject("INSERT INTO renaser.secciones_onboarding (flujo, clave_seccion, titulo) "
                + "VALUES ('v90', ?, 'Prueba') RETURNING id", Short.class, "e213-" + usuarioId);
        preguntaId = jdbc.queryForObject("INSERT INTO renaser.preguntas_onboarding (seccion_id, clave_pregunta, texto, tipo) "
                + "VALUES (?, 'e213', 'Prueba', CAST('TEXTO' AS renaser.tipo_pregunta_onboarding)) RETURNING id",
                Integer.class, seccionId);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", usuarioId);
        jdbc.update("DELETE FROM renaser.preguntas_onboarding WHERE id = ?", preguntaId);
        jdbc.update("DELETE FROM renaser.secciones_onboarding WHERE id = ?", seccionId);
    }

    @Test
    @DisplayName("ocho guardados a la vez de una pregunta nueva: ninguno falla y queda una sola fila")
    void guardadosSimultaneosNoChocan() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        List<Future<?>> resultados = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            String valor = "valor " + i;
            resultados.add(pool.submit(() -> {
                largada.await();
                // Cada hilo en su propia transaccion, igual que dos pedidos HTTP distintos.
                return enTransaccion.execute(tx -> adapter.guardar(Respuesta.crear(TipoPreguntaOnboarding.TEXTO,
                        UserId.of(usuarioId), preguntaId, valor, null, null, null, null, null, CLOCK)));
            }));
        }
        largada.countDown();
        for (Future<?> resultado : resultados) {
            resultado.get(); // relanza la excepcion si alguno choco contra el UNIQUE
        }
        pool.shutdown();

        Integer filas = jdbc.queryForObject("SELECT count(*) FROM renaser.respuestas_onboarding "
                + "WHERE usuario_id = ? AND pregunta_id = ?", Integer.class, usuarioId, preguntaId);
        assertThat(filas).isEqualTo(1);
    }
}
