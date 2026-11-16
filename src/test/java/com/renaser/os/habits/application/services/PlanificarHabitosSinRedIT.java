package com.renaser.os.habits.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.desbloqueo.DesbloqueosSinRedUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.in.preferencia.CambiarEstadoHabitoEnFechaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.PreferenciasSinRedUseCase;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Planificar hábitos y alarmas sin red contra Postgres de verdad (spec 048, fase 3, FR-019). La persona lo hace el lunes 9 a las 21:40 de Lima
 * (02:40 UTC del 10, el día local anterior en UTC) y la app lo envía el martes 10 a las 08:00. Fija lo que ningún doble puede: que cada
 * escritura deje {@code actualizado_en} en la HORA REAL (R1: ya no la de llegada, así dos envíos seguidos del mismo teléfono no chocan entre sí), que
 * una fila cambiada después en el servidor gane, y que el mismo envío dos veces tenga un solo efecto.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Planificar hábitos sin red: lo que se guarda de verdad (spec 048, fase 3)")
class PlanificarHabitosSinRedIT {

    private static final Instant HECHO = Instant.parse("2026-11-10T02:40:00Z");
    private static final Instant LLEGA = Instant.parse("2026-11-10T13:00:00Z");

    @Autowired private PreferenciasSinRedUseCase preferenciasSinRed;
    @Autowired private DesbloqueosSinRedUseCase desbloqueosSinRed;
    @Autowired private EditarHorarioSemanalUseCase semanal;
    @Autowired private CambiarEstadoHabitoEnFechaUseCase estadoDelDia;
    @Autowired private ElegirHabitoUseCase elegir;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RelojDePrueba reloj;

    private UserId persona;
    private HabitoId habito;

    @BeforeEach
    void sembrar() {
        reloj.fijar(LLEGA);
        persona = UserId.of(UUID.randomUUID());
        habito = HabitoId.of(UUID.randomUUID());
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, 'Prueba', 'APRENDIZ', 'ACTIVO')",
                persona.value(), persona + "@prueba.test");
        // Día 10 de su programa a la llegada: pasada la semana de acomodo libre, así que la cuota semanal cuenta.
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone) "
                + "VALUES (?, ?, 10, now(), 'America/Lima')", persona.value(), LocalDate.of(2026, 11, 1));
        jdbc.update("INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave) VALUES (?, 'SISTEMA', 'Meditar', 'CHECKBOX', 'MENTE')",
                habito.value());
        jdbc.update("INSERT INTO renaser.horarios_habito (habito_id, dia_inicio, dia_fin, tipo_dia, hora_disparo, hora_limite) "
                + "VALUES (?, 1, NULL, 'TODOS', ?, ?)", habito.value(), LocalTime.of(6, 0), LocalTime.of(8, 0));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", persona.value());
        jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", habito.value());
    }

    private HechoSinRed envio(UUID id, Instant hechoEn) {
        return new HechoSinRed(hechoEn, id, OrigenDeLaHora.RELOJ_DEL_SISTEMA);
    }

    private Instant actualizadoEnDelDia(DayOfWeek dia) {
        return jdbc.queryForObject("SELECT actualizado_en FROM renaser.horario_semanal_habito WHERE participante_id = ? AND habito_id = ? AND dia_semana = ?",
                Timestamp.class, persona.value(), habito.value(), (short) dia.getValue()).toInstant();
    }

    @Test
    @DisplayName("la hora de un día sin red deja la fila con la HORA REAL, y la alarma y el aviso del mismo día enviados a continuación NO chocan con ella")
    void dosEnviosSeguidosDelMismoTelefonoNoChocan() { // spec 048 FR-019
        preferenciasSinRed.fijarDiaDeLaSemana(persona, habito, DayOfWeek.WEDNESDAY, LocalTime.of(6, 30), LocalTime.of(8, 0),
                envio(UUID.randomUUID(), HECHO));
        assertThat(actualizadoEnDelDia(DayOfWeek.WEDNESDAY)).isEqualTo(HECHO);

        preferenciasSinRed.fijarAlarmaDelDia(persona, habito, DayOfWeek.WEDNESDAY, CombinacionDeAlarma.COMO_HOY,
                envio(UUID.randomUUID(), HECHO.plusSeconds(1)));
        preferenciasSinRed.fijarAvisoDelDia(persona, habito, DayOfWeek.WEDNESDAY, true, java.util.List.of(10),
                envio(UUID.randomUUID(), HECHO.plusSeconds(2)));

        assertThat(actualizadoEnDelDia(DayOfWeek.WEDNESDAY)).isEqualTo(HECHO.plusSeconds(2));
        assertThat(jdbc.queryForObject("SELECT hora_disparo::text FROM renaser.horario_semanal_habito WHERE participante_id = ? AND habito_id = ? AND dia_semana = 3",
                String.class, persona.value(), habito.value())).startsWith("06:30");
    }

    @Test
    @DisplayName("D13: una fila del día cambiada en el servidor DESPUÉS de lo hecho en el teléfono gana el servidor: no se aplica, queda anotado y repetirlo da lo mismo")
    void ganaElServidor() { // spec 048 FR-019
        preferenciasSinRed.fijarDiaDeLaSemana(persona, habito, DayOfWeek.THURSDAY, LocalTime.of(6, 30), LocalTime.of(8, 0),
                envio(UUID.randomUUID(), HECHO));
        jdbc.update("UPDATE renaser.horario_semanal_habito SET hora_disparo = ?, actualizado_en = ? WHERE participante_id = ? AND habito_id = ? AND dia_semana = 4",
                LocalTime.of(5, 0), Timestamp.from(HECHO.plusSeconds(3600)), persona.value(), habito.value());
        UUID envio = UUID.randomUUID();

        assertThatThrownBy(() -> preferenciasSinRed.fijarDiaDeLaSemana(persona, habito, DayOfWeek.THURSDAY, LocalTime.of(7, 0), LocalTime.of(8, 30),
                envio(envio, HECHO.plusSeconds(60)))).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));

        assertThat(jdbc.queryForObject("SELECT hora_disparo::text FROM renaser.horario_semanal_habito WHERE participante_id = ? AND habito_id = ? AND dia_semana = 4",
                String.class, persona.value(), habito.value())).startsWith("05:00");
        assertThat(jdbc.queryForMap("SELECT resultado, codigo FROM renaser.envios_sin_red WHERE id_cliente = ?", envio))
                .containsEntry("resultado", "NO_APLICADO").containsEntry("codigo", "CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR");
        assertThatThrownBy(() -> preferenciasSinRed.fijarDiaDeLaSemana(persona, habito, DayOfWeek.THURSDAY, LocalTime.of(7, 0), LocalTime.of(8, 30),
                envio(envio, HECHO.plusSeconds(60)))).isInstanceOf(NoAplicadoSinRedException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.envios_sin_red WHERE id_cliente = ?", Integer.class, envio)).isEqualTo(1);
    }

    @Test
    @DisplayName("el mismo envío dos veces se aplica una sola vez (aunque entre uno y otro se cambie la hora desde otro lado)")
    void envioRepetido() { // spec 048 FR-008
        UUID id = UUID.randomUUID();
        preferenciasSinRed.fijarDiaDeLaSemana(persona, habito, DayOfWeek.FRIDAY, LocalTime.of(6, 30), LocalTime.of(8, 0), envio(id, HECHO));
        semanal.fijar(persona, habito, DayOfWeek.FRIDAY, LocalTime.of(7, 15), LocalTime.of(9, 0), LLEGA);

        preferenciasSinRed.fijarDiaDeLaSemana(persona, habito, DayOfWeek.FRIDAY, LocalTime.of(6, 30), LocalTime.of(8, 0), envio(id, HECHO));

        assertThat(jdbc.queryForObject("SELECT hora_disparo::text FROM renaser.horario_semanal_habito WHERE participante_id = ? AND habito_id = ? AND dia_semana = 5",
                String.class, persona.value(), habito.value())).startsWith("07:15");
    }

    @Test
    @DisplayName("un hábito que ya no existe es RECURSO_YA_NO_EXISTE; quitar el día de lo que ya no está se aplica sin más")
    void habitoQueYaNoExiste() { // spec 048 FR-019
        HabitoId fantasma = HabitoId.of(UUID.randomUUID());

        assertThatThrownBy(() -> preferenciasSinRed.fijarDiaDeLaSemana(persona, fantasma, DayOfWeek.MONDAY, LocalTime.of(6, 0), null,
                envio(UUID.randomUUID(), HECHO))).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));
        assertThatCode(() -> preferenciasSinRed.quitarDiaDeLaSemana(persona, fantasma, DayOfWeek.MONDAY, envio(UUID.randomUUID(), HECHO)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("R1: apagar un día y su aviso por fecha dejan actualizado_en en la hora de la escritura, con red y sin ella")
    void r1AvisoYFechaMuevenActualizadoEn() { // spec 048 FR-019
        LocalDate fecha = LocalDate.of(2026, 11, 20);
        preferenciasSinRed.cambiarEstadoDelDia(persona, habito, fecha, false, envio(UUID.randomUUID(), HECHO));
        assertThat(jdbc.queryForObject("SELECT actualizado_en FROM renaser.horarios_habito_por_fecha WHERE participante_id = ? AND habito_id = ? AND fecha = ?",
                Timestamp.class, persona.value(), habito.value(), fecha).toInstant()).isEqualTo(HECHO);

        Instant lasNueve = Instant.parse("2026-11-10T14:00:00Z");
        reloj.fijar(lasNueve);
        semanal.fijarRecordatorio(persona, habito, DayOfWeek.SATURDAY, true, java.util.List.of(10));
        assertThat(actualizadoEnDelDia(DayOfWeek.SATURDAY)).isEqualTo(lasNueve);
        estadoDelDia.cambiarEstadoEnFecha(persona, habito, fecha, false);
        assertThat(jdbc.queryForObject("SELECT actualizado_en FROM renaser.horarios_habito_por_fecha WHERE participante_id = ? AND habito_id = ? AND fecha = ?",
                Timestamp.class, persona.value(), habito.value(), fecha).toInstant()).isEqualTo(lasNueve);
    }

    @Test
    @DisplayName("elegir un hábito del plan sin red deja la fila con la hora real; un cambio posterior del servidor gana")
    void elegirDelPlan() { // spec 048 FR-019
        UUID id = UUID.randomUUID();
        desbloqueosSinRed.elegir(new ElegirHabitoCommand(persona, habito, null), envio(id, HECHO));

        assertThat(jdbc.queryForObject("SELECT actualizado_en FROM renaser.desbloqueos_habito WHERE participante_id = ? AND habito_id = ?",
                Timestamp.class, persona.value(), habito.value()).toInstant()).isEqualTo(HECHO);

        jdbc.update("UPDATE renaser.desbloqueos_habito SET actualizado_en = ? WHERE participante_id = ? AND habito_id = ?",
                Timestamp.from(HECHO.plusSeconds(600)), persona.value(), habito.value());
        assertThatThrownBy(() -> desbloqueosSinRed.elegir(new ElegirHabitoCommand(persona, habito, null), envio(UUID.randomUUID(), HECHO.plusSeconds(60))))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
    }
}
