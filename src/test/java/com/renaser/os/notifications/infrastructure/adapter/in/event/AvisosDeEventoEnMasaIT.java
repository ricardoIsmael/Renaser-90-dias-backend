package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.application.ports.in.recordatorio.DespacharRecordatoriosUseCase;
import com.renaser.os.shared.infrastructure.event.EventPublicationMaintenanceScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HALLAZGO-A1 (E-360): mil recordatorios de evento que vencen en el mismo minuto, contra Postgres de
 * verdad y con el pool de produccion (20 conexiones, 5 s de espera).
 *
 * <p><b>Lo que paso en la prueba de punta a punta del 2026-09-27.</b> El despachador publico 29 avisos a
 * la vez; cada uno abrio su propio hilo ({@code SimpleAsyncTaskExecutor}, sin tope) y cada hilo pedia DOS
 * conexiones a la vez (la del listener y la transaccion propia del INSERT de la notificacion). A los 5 s
 * Hikari corto: {@code Connection is not available, request timed out after 5000ms (total=20, active=20,
 * idle=0, waiting=27)}, 17 avisos no llegaron y el resto de la API espero con ellos.
 *
 * <p>Esta clase prueba cuatro cosas, y las cuatro fallan contra el codigo anterior:
 * <ol>
 *   <li>llegan los mil, uno por persona (antes el propio despacho se quedaba sin conexion:
 *       {@code CannotCreateTransactionException: Could not open JPA EntityManager for transaction});</li>
 *   <li>mientras se entregan, la API sigue consiguiendo conexion enseguida (una sonda pide una cada 50 ms);</li>
 *   <li>el outbox guarda una publicacion por aviso (antes, ninguna: marcar la fila enviada vaciaba el contexto
 *       de persistencia y se llevaba las publicaciones sin escribirlas);</li>
 *   <li>un aviso que fallo no se pierde: queda incompleto en el outbox y el reintento lo entrega una vez.</li>
 * </ol>
 *
 * <p>Sin {@code @Transactional} de clase: el despacho publica dentro de su transaccion y los listeners
 * corren despues del commit, en otros hilos; hay que mirar el estado ya comprometido.
 */
@SpringBootTest(properties = {
        // El pool de produccion (application.yaml), que en las pruebas no esta espejado.
        "spring.datasource.hikari.maximum-pool-size=20",
        "spring.datasource.hikari.connection-timeout=5000",
        // El reintento se dispara a mano, sin esperar los 5 minutos de produccion, y el cron queda
        // apagado para que no se cruce con la prueba.
        "renaser.eventos.reintento-tras=PT0S",
        "renaser.eventos.mantenimiento-cron=-",
        // Produccion borra la publicacion al completarse (DELETE); aca se conserva para poder contar que el
        // outbox guardo una por aviso. Es justo lo que no pasaba (E-360).
        "spring.modulith.events.completion-mode=UPDATE"
})
@Import(TestcontainersConfiguration.class)
class AvisosDeEventoEnMasaIT {

    private static final int PERSONAS = 1000;
    private static final String TITULO = "Clase en masa";
    /** Espera de una conexion que ya es un problema para la API (produccion corta a los 5 s). */
    private static final Duration ESPERA_TOLERABLE = Duration.ofMillis(2500);

    @Autowired
    private DespacharRecordatoriosUseCase despachar;
    @Autowired
    private EventPublicationMaintenanceScheduler mantenimientoDelOutbox;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID eventoId;
    private final List<UUID> personas = new ArrayList<>();

    @BeforeEach
    void sembrar() {
        prepararFallasAPedido();
        personas.clear();
        eventoId = UUID.randomUUID();
        Instant ahora = Instant.now();
        jdbc.update("""
                INSERT INTO renaser.eventos (id, titulo, inicia_en, timezone, tipo_ubicacion, tipo_audiencia,
                                             estado, tipo_evento, creado_en, actualizado_en)
                VALUES (?, ?, ?, 'America/Lima', 'LLAMADA_INTERNA', 'TODOS', 'PUBLICADO', 'SESION_ESPECIAL', ?, ?)
                """, eventoId, TITULO, Timestamp.from(ahora.plus(Duration.ofHours(2))),
                Timestamp.from(ahora.minus(Duration.ofDays(1))), Timestamp.from(ahora.minus(Duration.ofDays(1))));
        for (int i = 0; i < PERSONAS; i++) {
            personas.add(UUID.randomUUID());
        }
        jdbc.batchUpdate("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol) VALUES (?, ?, 'Aprendiz en masa', 'APRENDIZ')
                """, personas, 500, (ps, id) -> {
            ps.setObject(1, id);
            ps.setString(2, "masa-" + id + "@renaser.test");
        });
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.fallas_de_aviso_de_prueba");
        jdbc.update("DELETE FROM renaser.eventos WHERE id = ?", eventoId);
        jdbc.batchUpdate("DELETE FROM renaser.usuarios WHERE id = ?", personas, 500,
                (ps, id) -> ps.setObject(1, id));
    }

    @Test
    @DisplayName("A1: mil avisos que vencen juntos llegan todos, y la API no se queda sin conexiones mientras tanto")
    void milAvisosLleganSinAgotarElPool() throws InterruptedException {
        encolarUnAvisoPorPersona();
        Sonda sonda = Sonda.iniciar(jdbc);

        despacharTodo();
        long entregados = esperarNotificaciones(PERSONAS, Duration.ofSeconds(120));
        sonda.detener();

        assertThat(entregados).as("un aviso por persona en la bandeja").isEqualTo(PERSONAS);
        assertThat(sonda.fallas()).as("la API no se quedo sin conexion (" + sonda.fallas() + ")").isEmpty();
        assertThat(sonda.peorEspera()).as("la espera mas larga de la API por una conexion")
                .isLessThan(ESPERA_TOLERABLE);
        assertThat(publicacionesIncompletas()).as("ningun aviso quedo a medio entregar en el outbox").isZero();
        assertThat(publicacionesDelEvento()).as("el outbox guardo una publicacion por aviso: " + estadoDelOutbox())
                .isEqualTo(PERSONAS);
    }

    @Test
    @DisplayName("A1: un aviso que no se pudo entregar no se pierde; el reintento del outbox lo entrega una sola vez")
    void unAvisoQueFalloSeReintentaYLlega() throws InterruptedException {
        List<UUID> conFalla = personas.subList(0, 50);
        hacerFallarLaBandejaDe(conFalla);
        encolarUnAvisoPorPersona();

        despacharTodo();
        long entregados = esperarNotificaciones(PERSONAS - conFalla.size(), Duration.ofSeconds(120));
        assertThat(entregados).as("los que no fallaron llegaron").isEqualTo(PERSONAS - conFalla.size());
        esperarPublicacionesIncompletas(conFalla.size(), Duration.ofSeconds(30));
        assertThat(recordatoriosSinEnviar()).as("todos quedaron entregados al outbox").isZero();

        jdbc.update("DELETE FROM renaser.fallas_de_aviso_de_prueba");
        mantenimientoDelOutbox.reintentarPublicacionesIncompletas();

        assertThat(esperarNotificaciones(PERSONAS, Duration.ofSeconds(60)))
                .as("el reintento entrego los que habian fallado").isEqualTo(PERSONAS);
        assertThat(avisosRepetidos()).as("nadie recibio el mismo aviso dos veces").isZero();
        esperarPublicacionesIncompletas(0, Duration.ofSeconds(30));
    }

    // ── Escenario ───────────────────────────────────────────────────────────

    /** Un recordatorio vencido por persona, todos para el mismo minuto: el aviso de "10 min antes". */
    private void encolarUnAvisoPorPersona() {
        Instant inicio = jdbc.queryForObject("SELECT inicia_en FROM renaser.eventos WHERE id = ?",
                Timestamp.class, eventoId).toInstant();
        Timestamp vencio = Timestamp.from(Instant.now().minusSeconds(30));
        jdbc.batchUpdate("""
                INSERT INTO renaser.recordatorios_evento (evento_id, inicio_ocurrencia, usuario_id, enviar_en)
                VALUES (?, ?, ?, ?)
                """, personas, 500, (ps, id) -> {
            ps.setObject(1, eventoId);
            ps.setTimestamp(2, Timestamp.from(inicio));
            ps.setObject(3, id);
            ps.setTimestamp(4, vencio);
        });
    }

    /** Lo que haria el scheduler en sus pasadas de cada minuto, sin esperar al minuto siguiente. */
    private void despacharTodo() {
        int vueltas = 0;
        while (despachar.despachar(Instant.now()) > 0 && vueltas++ < 50) {
            // sigue hasta vaciar la cola
        }
    }

    /**
     * Una falla de verdad del INSERT, del lado de la base: lo mas parecido a quedarse sin conexion a mitad de
     * la entrega. No es una violacion de integridad a proposito: esas se tratan como "ya existia" y no se
     * reintentan.
     *
     * <p>El trigger se crea UNA vez, antes de despachar, y la falla se prende y se apaga con filas de una
     * tabla de control. Crear o borrar el trigger con avisos en vuelo es DDL sobre una tabla caliente: espera
     * el lock sin limite y, con el pool agotado, no termina nunca (le paso a la primera version de esta clase).
     */
    private void prepararFallasAPedido() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL lock_timeout = '10s'");
            jdbc.execute("CREATE TABLE IF NOT EXISTS renaser.fallas_de_aviso_de_prueba (usuario_id uuid PRIMARY KEY)");
            jdbc.execute("""
                    CREATE OR REPLACE FUNCTION renaser.falla_avisos_de_prueba() RETURNS trigger AS $$
                    BEGIN
                      IF EXISTS (SELECT 1 FROM renaser.fallas_de_aviso_de_prueba f WHERE f.usuario_id = NEW.usuario_id) THEN
                        RAISE EXCEPTION 'falla de prueba al guardar el aviso';
                      END IF;
                      RETURN NEW;
                    END $$ LANGUAGE plpgsql
                    """);
            jdbc.execute("DROP TRIGGER IF EXISTS falla_avisos_de_prueba ON renaser.notificaciones");
            jdbc.execute("""
                    CREATE TRIGGER falla_avisos_de_prueba BEFORE INSERT ON renaser.notificaciones
                    FOR EACH ROW EXECUTE FUNCTION renaser.falla_avisos_de_prueba()
                    """);
        });
    }

    private void hacerFallarLaBandejaDe(List<UUID> usuarios) {
        jdbc.batchUpdate("INSERT INTO renaser.fallas_de_aviso_de_prueba (usuario_id) VALUES (?)", usuarios, 100,
                (ps, id) -> ps.setObject(1, id));
    }

    // ── Lecturas ────────────────────────────────────────────────────────────

    private long esperarNotificaciones(long esperadas, Duration tope) throws InterruptedException {
        Instant limite = Instant.now().plus(tope);
        long hay = contarNotificaciones();
        while (hay < esperadas && Instant.now().isBefore(limite)) {
            Thread.sleep(250);
            hay = contarNotificaciones();
        }
        return hay;
    }

    private void esperarPublicacionesIncompletas(long esperadas, Duration tope) throws InterruptedException {
        Instant limite = Instant.now().plus(tope);
        while (publicacionesIncompletas() != esperadas && Instant.now().isBefore(limite)) {
            Thread.sleep(250);
        }
        assertThat(publicacionesIncompletas()).as("publicaciones incompletas en el outbox: " + estadoDelOutbox())
                .isEqualTo(esperadas);
    }

    private long contarNotificaciones() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM renaser.notificaciones n
                WHERE n.tipo = 'RECORDATORIO_EVENTO' AND n.titulo = ?
                  AND n.usuario_id IN (SELECT r.usuario_id FROM renaser.recordatorios_evento r WHERE r.evento_id = ?)
                """, Long.class, TITULO, eventoId);
    }

    private long avisosRepetidos() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM (
                  SELECT n.usuario_id FROM renaser.notificaciones n
                  WHERE n.tipo = 'RECORDATORIO_EVENTO' AND n.titulo = ?
                  GROUP BY n.usuario_id HAVING count(*) > 1) repetidos
                """, Long.class, TITULO);
    }

    private long publicacionesDelEvento() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?", Long.class,
                "%" + eventoId + "%");
    }

    private long publicacionesIncompletas() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE completion_date IS NULL AND serialized_event LIKE ?
                """, Long.class, "%" + eventoId + "%");
    }

    /** Para el mensaje de una falla: que hay en el outbox, por estado. */
    private String estadoDelOutbox() {
        return jdbc.queryForList("""
                SELECT status, (completion_date IS NULL) AS incompleta, count(*) AS filas,
                       min(left(serialized_event, 160)) AS ejemplo, min(event_type) AS tipo
                FROM event_publication GROUP BY 1, 2
                """).toString();
    }

    private long recordatoriosSinEnviar() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM renaser.recordatorios_evento WHERE evento_id = ? AND enviado_en IS NULL",
                Long.class, eventoId);
    }

    /**
     * Pide una conexion cada 50 ms, como lo haria cualquier request de la API, y anota cuanto espero.
     * Hilo de plataforma propio: no compite por el ejecutor de los listeners.
     */
    private static final class Sonda {

        private final AtomicBoolean activa = new AtomicBoolean(true);
        private final AtomicLong peorEsperaNanos = new AtomicLong();
        private final List<String> fallas = new CopyOnWriteArrayList<>();
        private final Thread hilo;

        private Sonda(JdbcTemplate jdbc) {
            this.hilo = Thread.ofPlatform().name("sonda-de-la-api").start(() -> medir(jdbc));
        }

        static Sonda iniciar(JdbcTemplate jdbc) {
            return new Sonda(jdbc);
        }

        private void medir(JdbcTemplate jdbc) {
            while (activa.get()) {
                long antes = System.nanoTime();
                try {
                    jdbc.queryForObject("SELECT 1", Integer.class);
                } catch (RuntimeException e) {
                    fallas.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                peorEsperaNanos.accumulateAndGet(System.nanoTime() - antes, Math::max);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        void detener() throws InterruptedException {
            activa.set(false);
            hilo.join(Duration.ofSeconds(10));
        }

        List<String> fallas() {
            return fallas;
        }

        Duration peorEspera() {
            return Duration.ofNanos(peorEsperaNanos.get());
        }
    }
}
