package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-230 contra Postgres real: dos pedidos a {@code /hoy} en el mismo milisegundo intentaban crear el
 * mismo track, y el segundo reventaba con {@code registros_habito_participante_id_habito_id_fecha_ejecucion_key}
 * (409). Con {@code INSERT ... ON CONFLICT DO NOTHING}, dos inserciones simultaneas terminan en una
 * sola fila y ninguna lanza.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InsertarTrackSiNoExisteIT {

    private static final int PEDIDOS_SIMULTANEOS = 4;
    private static final LocalDate HOY = LocalDate.of(2026, 9, 24);

    @Autowired
    private SaveRegistroHabitoPort savePort;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private UserId participante;
    private HabitoId habito;

    @BeforeEach
    void seed() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture E-230', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, 5)", id);
        participante = UserId.of(id);
        habito = HabitoId.of(jdbcTemplate.queryForObject(
                "SELECT id FROM renaser.habitos WHERE participante_id IS NULL LIMIT 1", UUID.class));
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participante.value());
    }

    private RegistroHabito nuevo() {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participante, habito, HOY, 5,
                TipoDia.TODOS, false, Instant.parse("2026-09-24T03:00:00Z"));
    }

    private boolean insertar() {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> savePort.insertarSiNoExiste(nuevo())));
    }

    private int filas() {
        Integer cuantas = jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.registros_habito "
                + "WHERE participante_id = ? AND habito_id = ? AND fecha_ejecucion = ?", Integer.class,
                participante.value(), habito.value(), HOY);
        return cuantas == null ? 0 : cuantas;
    }

    @Test
    @DisplayName("la primera vez inserta; la segunda dice que ya existia, sin excepcion")
    void segundaVezNoInserta() {
        assertThat(insertar()).isTrue();
        assertThat(insertar()).isFalse();
        assertThat(filas()).isEqualTo(1);
    }

    @Test
    @DisplayName("pedidos simultaneos de verdad: exactamente uno inserta, ninguno falla, queda una fila")
    void simultaneos() throws Exception {
        CyclicBarrier largada = new CyclicBarrier(PEDIDOS_SIMULTANEOS);
        ExecutorService hilos = Executors.newFixedThreadPool(PEDIDOS_SIMULTANEOS);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < PEDIDOS_SIMULTANEOS; i++) {
                resultados.add(hilos.submit(() -> {
                    largada.await(10, TimeUnit.SECONDS);
                    return insertar();
                }));
            }
            int insertaron = 0;
            for (Future<Boolean> resultado : resultados) {
                insertaron += resultado.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(insertaron).isEqualTo(1);
            assertThat(filas()).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
    }
}
