package com.renaser.os.rag.infrastructure.adapter.out.persistence.agenda;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.agenda.AgendaSemanalPort;
import com.renaser.os.rag.domain.model.agenda.AgendaSemanal;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code agenda_ocupada} (V64) contra Postgres real: que los CHECK aceptan lo que escribe el dominio
 * (incluido el fin del dia, 1440), que guardar reemplaza todo, y que la agenda cae con la cuenta.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AgendaSemanalPersistenceAdapterIT {

    @Autowired
    private AgendaSemanalPort port;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId participante;

    @BeforeEach
    void seedParticipante() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture agenda', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test");
        participante = UserId.of(id);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participante.value());
    }

    @Test
    @DisplayName("ida y vuelta: dias, tramos, el fin del dia (24:00) y la madrugada del dia siguiente")
    void idaYVuelta() {
        AgendaSemanal agenda = AgendaSemanal.vacia()
                .conDias(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), "09:00-13:00, 14:00-18:00")
                .conDias(Set.of(DayOfWeek.SUNDAY), "22:00-06:00");

        port.guardar(participante, agenda);

        AgendaSemanal leida = port.de(participante);
        assertThat(leida).isEqualTo(agenda);
        assertThat(leida.delDia(DayOfWeek.SUNDAY).texto()).isEqualTo("22:00-24:00");
        assertThat(leida.delDia(DayOfWeek.MONDAY).texto()).isEqualTo("00:00-06:00, 09:00-13:00, 14:00-18:00");
    }

    @Test
    @DisplayName("guardar reemplaza la agenda entera; una vacia no deja filas")
    void guardarReemplaza() {
        port.guardar(participante, AgendaSemanal.vacia().conDias(Set.of(DayOfWeek.MONDAY), "09:00-18:00"));
        port.guardar(participante, AgendaSemanal.vacia().conDias(Set.of(DayOfWeek.TUESDAY), "10:00-11:00"));

        assertThat(port.de(participante).texto()).isEqualTo("martes 10:00-11:00");

        port.guardar(participante, AgendaSemanal.vacia());
        assertThat(filas()).isZero();
    }

    @Test
    @DisplayName("los CHECK rechazan un tramo al reves y un dia fuera de 1..7")
    void checks() {
        assertThatThrownBy(() -> insertar(1, 600, 540)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(8, 540, 600)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(1, 540, 1441)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("al borrar la cuenta, su agenda se va con ella (ON DELETE CASCADE)")
    void caeConLaCuenta() {
        port.guardar(participante, AgendaSemanal.vacia().conDias(Set.of(DayOfWeek.MONDAY), "09:00-18:00"));

        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participante.value());

        assertThat(filas()).isZero();
    }

    private void insertar(int dia, int desde, int hasta) {
        jdbcTemplate.update("INSERT INTO renaser.agenda_ocupada (id, participante_id, dia_semana, desde_minuto, "
                + "hasta_minuto) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), participante.value(), dia, desde, hasta);
    }

    private int filas() {
        Integer cuantas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.agenda_ocupada WHERE participante_id = ?", Integer.class,
                participante.value());
        return cuantas == null ? 0 : cuantas;
    }
}
