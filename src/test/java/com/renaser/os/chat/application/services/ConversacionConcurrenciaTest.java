package com.renaser.os.chat.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase.CrearConversacionDirectaCommand;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * Regresion de C-10 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):
 * {@code obtenerOCrear} es "traeme la conversacion directa, y si no existe creala" — dos
 * llamadas concurrentes del mismo par de usuarios no deben crear dos conversaciones ni
 * terminar en un 409. Requiere Postgres real: la carrera contra el
 * {@code UNIQUE(clave_directa)} no se puede probar con mocks.
 *
 * <p>Deliberadamente SIN {@code @Transactional} de clase (mismo motivo que
 * {@code RocaDiariaConcurrenciaTest}): los hilos del {@code ExecutorService} necesitan su
 * propia conexion/transaccion para competir de verdad contra la base.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConversacionConcurrenciaTest {

    private static final int INTENTOS_CONCURRENTES = 6;

    @Autowired
    private CrearConversacionDirectaUseCase crearConversacionDirectaUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId actorId;
    private UserId otroId;

    @BeforeEach
    void seedUsuarios() {
        actorId = crearUsuarioActivo();
        otroId = crearUsuarioActivo();
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_conversacion; la conversacion directa
        // queda huerfana a proposito (no la referencia ningun FK desde usuarios) y se
        // limpia explicitamente por clave.
        String clave = Conversacion.claveDirectaDe(actorId, otroId);
        jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE clave_directa = ?", clave);
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id IN (?, ?)", actorId.value(), otroId.value());
    }

    private UserId crearUsuarioActivo() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))
                        """,
                id, id + "@renaser.test");
        return UserId.of(id);
    }

    @Test
    @DisplayName("C-10: N obtenerOCrear concurrentes del mismo par -> una sola conversacion directa, sin 409")
    void llamadasConcurrentesNoDuplicanLaConversacionDirecta() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(INTENTOS_CONCURRENTES);
        List<Future<Conversacion>> resultados;
        try {
            List<Callable<Conversacion>> intentos = IntStream.range(0, INTENTOS_CONCURRENTES)
                    .<Callable<Conversacion>>mapToObj(i -> () -> crearConversacionDirectaUseCase.obtenerOCrear(
                            new CrearConversacionDirectaCommand(actorId, otroId)))
                    .toList();
            resultados = pool.invokeAll(intentos, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        // El bug (C-10): sin el arreglo, la mitad de estas llamadas terminaba en
        // DataIntegrityViolationException (409) por chocar contra el UNIQUE(clave_directa).
        List<Conversacion> conversaciones = resultados.stream().map(this::obtenerSinFallar).toList();
        assertThat(conversaciones.stream().map(Conversacion::id).distinct())
                .as("todos los intentos terminan viendo la MISMA conversacion").hasSize(1);

        String clave = Conversacion.claveDirectaDe(actorId, otroId);
        Long filasConversacion = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.conversaciones WHERE clave_directa = ?", Long.class, clave);
        assertThat(filasConversacion).as("una sola fila de conversacion").isEqualTo(1);

        Long filasParticipantes = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM renaser.participantes_conversacion
                        WHERE conversacion_id = (SELECT id FROM renaser.conversaciones WHERE clave_directa = ?)
                        """,
                Long.class, clave);
        assertThat(filasParticipantes).as("exactamente los 2 participantes, no uno por intento").isEqualTo(2);
    }

    private Conversacion obtenerSinFallar(Future<Conversacion> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Fallo inesperado en un intento concurrente (era justo lo que C-10 arreglaba)",
                    e);
        }
    }
}
