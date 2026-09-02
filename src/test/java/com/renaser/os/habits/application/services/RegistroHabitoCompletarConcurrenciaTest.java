package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.api.HabitoCompletadoEvent;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cierra un hueco de cobertura detectado al revisar C-14 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):
 * ni {@code RegistroService.completar} (el hot-path mas transitado del sistema — CLAUDE.MD §3
 * lo usa como EL ejemplo canonico de "resolver un habit del dia") ni ningun otro test tenian
 * una regresion automatizada de la carrera que el propio codigo de produccion documenta a mano
 * en el javadoc de {@code RegistroService.requireRegistro}: <i>"verificado en vivo: 6 llamadas
 * paralelas devolvian 200 cada una, la 7a secuencial devolvia 409"</i>. C-2 (roca diaria) y C-13
 * (evidencia) ya tienen su regresion — este es el mismo patron de bloqueo pesimista
 * ({@code findByIdParaEscritura}) aplicado al modulo con mas trafico, sin ningun test que
 * demuestre que el lock efectivamente evita el doble pago de puntos y el doble evento.
 *
 * <p>Requiere Postgres real: el bloqueo pesimista de
 * {@code SpringDataRegistroHabitoRepository.findByIdParaEscritura} no se puede probar con
 * mocks — mismo motivo que {@code RocaDiariaConcurrenciaTest} (C-2).
 *
 * <p>Deliberadamente SIN {@code @Transactional} de clase, ni horario configurado para el
 * habito: sin fila en {@code horarios_habito} ni preferencia, {@code resolverVentana} devuelve
 * {@code null} y {@code completar()} omite el chequeo de vencimiento y otorga 0 puntos — evita
 * depender de {@code VentanaEntrega}/{@code ResultadoOtorgamiento} y del modulo {@code points}
 * para aislar exactamente la carrera que este test quiere probar (una sola transicion
 * PENDIENTE -&gt; COMPLETADO, un solo {@link HabitoCompletadoEvent}), igual que
 * {@code RocaDiariaConcurrenciaTest} evita depender de {@code horaFin} para simplificar
 * {@code EscalaPuntosRoca}.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, RegistroHabitoCompletarConcurrenciaTest.CapturaEventosConfig.class})
class RegistroHabitoCompletarConcurrenciaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final int INTENTOS_CONCURRENTES = 6;

    @Autowired
    private CompletarRegistroUseCase completarUseCase;
    @Autowired
    private SaveRegistroHabitoPort saveRegistroPort;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private CapturaEventos capturaEventos;

    private UserId participanteId;
    private HabitoId habitoId;

    @BeforeEach
    void seedFixtures() {
        participanteId = UserId.of(UUID.randomUUID());
        habitoId = HabitoId.of(UUID.randomUUID());
        capturaEventos.eventos().clear();

        // El EntityManager compartido exige una transaccion activa para executeUpdate;
        // @BeforeEach no la trae. La semilla commitea aparte, que es lo que la prueba
        // necesita: los datos deben existir ANTES de que corra el caso de uso. Sin fila en
        // horarios_habito a proposito (ver javadoc de la clase).
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                            VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                            """)
                    .setParameter("id", participanteId.value())
                    .setParameter("email", participanteId + "@renaser.test")
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                            VALUES (:usuarioId, 5)
                            """)
                    .setParameter("usuarioId", participanteId.value())
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave)
                            VALUES (:id, 'SISTEMA', 'Meditar', 'CHECKBOX', 'MENTE')
                            """)
                    .setParameter("id", habitoId.value())
                    .executeUpdate();
        });
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa / registros_habito.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
        jdbcTemplate.update("DELETE FROM renaser.habitos WHERE id = ?", habitoId.value());
    }

    private RegistroHabitoId seedRegistroPendiente() {
        RegistroHabitoId id = RegistroHabitoId.of(UUID.randomUUID());
        RegistroHabito registro = RegistroHabito.generar(id, participanteId, habitoId, LocalDate.of(2026, 8, 24), 5,
                TipoDia.DISCIPLINA, false, CLOCK.now());
        saveRegistroPort.save(registro);
        return id;
    }

    private CompletarRegistroCommand comando(RegistroHabitoId id) {
        return new CompletarRegistroCommand(participanteId, id, "Lo hice", null);
    }

    @Test
    @DisplayName("un completado normal (sin concurrencia) sigue funcionando igual que siempre")
    void completadoNormalSigueFuncionando() {
        RegistroHabitoId id = seedRegistroPendiente();

        RegistroHabito resultado = completarUseCase.completar(comando(id));

        assertThat(resultado.estado().name()).isEqualTo("COMPLETADO");
        assertThat(capturaEventos.eventos()).hasSize(1);
    }

    @Test
    @DisplayName("6 completados concurrentes del MISMO registro -> uno solo completa, un solo evento, "
            + "sin doble pago (regresion de la carrera documentada a mano en RegistroService.requireRegistro)")
    void dobleCompletadoConcurrenteNoDuplicaNada() throws InterruptedException {
        RegistroHabitoId id = seedRegistroPendiente();
        ExecutorService pool = Executors.newFixedThreadPool(INTENTOS_CONCURRENTES);
        List<Future<Boolean>> resultados;
        try {
            List<Callable<Boolean>> intentos = IntStream.range(0, INTENTOS_CONCURRENTES)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            completarUseCase.completar(comando(id));
                            return true; // gano la carrera
                        } catch (IllegalStateException ex) {
                            return false; // perdio la carrera: "no puede completarse" (409), esperado
                        }
                    })
                    .toList();
            resultados = pool.invokeAll(intentos, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        long exitosos = resultados.stream().filter(RegistroHabitoCompletarConcurrenciaTest::obtenerResultado).count();

        // Sin el lock pesimista de findByIdParaEscritura, esto daria 6 exitosos y 6 eventos
        // (exactamente el escenario que el javadoc de requireRegistro dice haber verificado
        // en vivo). Con el lock, exactamente 1 de cada uno.
        assertThat(exitosos).as("solo UNA de las %s llamadas concurrentes debe completar el registro",
                INTENTOS_CONCURRENTES).isEqualTo(1);
        assertThat(capturaEventos.eventos()).as("un solo HabitoCompletadoEvent publicado").hasSize(1);

        String estadoFinal = jdbcTemplate.queryForObject("SELECT estado FROM renaser.registros_habito WHERE id = ?",
                String.class, id.value());
        assertThat(estadoFinal).isEqualTo("COMPLETADO");
    }

    private static boolean obtenerResultado(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Fallo inesperado (no IllegalStateException) en un intento concurrente", e);
        }
    }

    /** Captura in-memory de {@link HabitoCompletadoEvent} para verificar que se publique una sola vez. */
    @TestConfiguration
    static class CapturaEventosConfig {
        @Bean
        CapturaEventos capturaEventos() {
            return new CapturaEventos();
        }
    }

    static class CapturaEventos {
        private final List<HabitoCompletadoEvent> eventos = new CopyOnWriteArrayList<>();

        @EventListener
        void alRecibir(HabitoCompletadoEvent evento) {
            eventos.add(evento);
        }

        List<HabitoCompletadoEvent> eventos() {
            return eventos;
        }
    }
}
