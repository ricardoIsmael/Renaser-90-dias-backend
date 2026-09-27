package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-196 contra Postgres real: la consulta agregada que dice si un habito del plan "ya corrio" (el
 * {@code dia_programa} mas alto con el que se genero un registro), que es lo que decide si sigue
 * generandose despues de un retroceso de dia.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DiaMasAltoGeneradoPorHabitoIT {

    @Autowired
    private LoadRegistroHabitoPort loadPort;
    @Autowired
    private SaveRegistroHabitoPort savePort;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private UserId participante;
    private HabitoId conRegistros;
    private HabitoId sinRegistros;

    @BeforeEach
    void seed() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture D-196', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, 25)", id);
        participante = UserId.of(id);
        List<UUID> habitos = jdbcTemplate.queryForList(
                "SELECT id FROM renaser.habitos WHERE participante_id IS NULL ORDER BY id LIMIT 2", UUID.class);
        conRegistros = HabitoId.of(habitos.get(0));
        sinRegistros = HabitoId.of(habitos.get(1));
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participante.value());
    }

    private void generar(LocalDate fecha, int diaPrograma) {
        transactionTemplate.executeWithoutResult(status -> savePort.insertarSiNoExiste(RegistroHabito.generar(
                RegistroHabitoId.of(UUID.randomUUID()), participante, conRegistros, fecha, diaPrograma,
                TipoDia.TODOS, false, Instant.parse("2026-09-24T03:00:00Z"))));
    }

    @Test
    @DisplayName("devuelve el dia mas alto por habito y omite los habitos sin registros")
    void diaMasAltoPorHabito() {
        generar(LocalDate.of(2026, 9, 20), 30);
        generar(LocalDate.of(2026, 9, 22), 32);
        generar(LocalDate.of(2026, 9, 21), 31);

        var resultado = loadPort.diaProgramaMasAltoGeneradoPorHabito(participante, List.of(conRegistros, sinRegistros));

        assertThat(resultado).containsOnlyKeys(conRegistros);
        assertThat(resultado.get(conRegistros)).isEqualTo(32);
    }

    @Test
    @DisplayName("sin habitos pedidos no consulta y devuelve vacio")
    void sinHabitos() {
        assertThat(loadPort.diaProgramaMasAltoGeneradoPorHabito(participante, List.of())).isEmpty();
    }
}
