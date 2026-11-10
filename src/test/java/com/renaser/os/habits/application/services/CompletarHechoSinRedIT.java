package com.renaser.os.habits.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.registro.CompletarHechoSinRedUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarHechoSinRedUseCase.HechoSinRed;
import com.renaser.os.habits.application.ports.in.registro.CompletarHechoSinRedUseCase.MotivoSinPuntos;
import com.renaser.os.habits.application.ports.in.registro.CompletarHechoSinRedUseCase.OrigenDeLaHora;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.registro.ResultadoOtorgamiento;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 048 contra Postgres de verdad: un hábito del 6 de octubre (06:00–08:00 en Lima) marcado sin red a las 06:30 llega el 7 a
 * las 10:00, cuando el barrido ya lo venció. Se completa con sus puntos completos y la hora real, y el mismo envío dos veces
 * queda una sola vez (V188).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CompletarHechoSinRedIT {

    private static final LocalDate EL_6 = LocalDate.of(2026, 10, 6);
    /** 06:30 del 6 en Lima. */
    private static final Instant HECHO = Instant.parse("2026-10-06T11:30:00Z");
    /** 10:00 del 7 en Lima: el 6 ya terminó. */
    private static final Instant LLEGA = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired private CompletarHechoSinRedUseCase completarSinRed;
    @Autowired private SaveRegistroHabitoPort saveRegistroPort;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RelojDePrueba reloj;

    private UserId persona;
    private HabitoId habito;

    @BeforeEach
    void sembrar() {
        persona = UserId.of(UUID.randomUUID());
        habito = HabitoId.of(UUID.randomUUID());
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, 'Fixture', 'APRENDIZ', 'ACTIVO')",
                persona.value(), persona + "@renaser.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone) VALUES (?, 5, 'America/Lima')",
                persona.value());
        jdbc.update("INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave) VALUES (?, 'SISTEMA', 'Meditar', 'CHECKBOX', 'MENTE')",
                habito.value());
        jdbc.update("INSERT INTO renaser.horarios_habito (habito_id, dia_inicio, dia_fin, tipo_dia, hora_disparo, hora_limite) "
                + "VALUES (?, 1, NULL, 'TODOS', ?, ?)", habito.value(), LocalTime.of(6, 0), LocalTime.of(8, 0));
        reloj.fijar(LLEGA);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", persona.value());
        jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", habito.value());
    }

    @Test
    @DisplayName("Hecho a tiempo sin red y llegado al día siguiente: puntos completos, hora real y una sola vez")
    void llegaTardePeroHechoATiempo() { // spec 048 FR-014 FR-008
        RegistroHabitoId id = RegistroHabitoId.of(UUID.randomUUID());
        RegistroHabito registro = RegistroHabito.generar(id, persona, habito, EL_6, 5, TipoDia.DISCIPLINA, false,
                Instant.parse("2026-10-06T05:00:00Z"));
        registro.expirar(Instant.parse("2026-10-07T05:10:00Z"));
        saveRegistroPort.save(registro);
        CompletarRegistroCommand comando = new CompletarRegistroCommand(persona, id, null, null);
        HechoSinRed envio = new HechoSinRed(HECHO, UUID.randomUUID(), OrigenDeLaHora.RELOJ_DEL_SISTEMA);

        var primero = completarSinRed.completar(comando, envio);
        var repetido = completarSinRed.completar(comando, envio);

        assertThat(primero.motivoSinPuntos()).isNull();
        assertThat(repetido.motivoSinPuntos()).isNull();
        var fila = jdbc.queryForMap("SELECT estado, puntos_otorgados, completado_en FROM renaser.registros_habito WHERE id = ?", id.value());
        assertThat(fila.get("estado")).isEqualTo("COMPLETADO");
        assertThat(fila.get("puntos_otorgados")).isEqualTo(ResultadoOtorgamiento.PUNTOS_COMPLETOS);
        assertThat(((Timestamp) fila.get("completado_en")).toInstant()).isEqualTo(HECHO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.envios_sin_red WHERE participante_id = ?", Integer.class,
                persona.value())).isEqualTo(1);
    }

    @Test
    @DisplayName("Llegado más de 48 h después: queda completado sin puntos, nunca rechazado")
    void masDe48HorasSinPuntos() { // spec 048 FR-015
        reloj.fijar(HECHO.plusSeconds(50 * 3600));
        RegistroHabitoId id = RegistroHabitoId.of(UUID.randomUUID());
        RegistroHabito registro = RegistroHabito.generar(id, persona, habito, EL_6, 5, TipoDia.DISCIPLINA, false,
                Instant.parse("2026-10-06T05:00:00Z"));
        registro.expirar(Instant.parse("2026-10-07T05:10:00Z"));
        saveRegistroPort.save(registro);

        var resultado = completarSinRed.completar(new CompletarRegistroCommand(persona, id, null, null),
                new HechoSinRed(HECHO, UUID.randomUUID(), OrigenDeLaHora.RELOJ_DEL_SISTEMA));

        assertThat(resultado.motivoSinPuntos()).isEqualTo(MotivoSinPuntos.LLEGO_DESPUES_DE_48_H);
        var fila = jdbc.queryForMap("SELECT estado, puntos_otorgados FROM renaser.registros_habito WHERE id = ?", id.value());
        assertThat(fila.get("estado")).isEqualTo("COMPLETADO");
        assertThat(fila.get("puntos_otorgados")).isEqualTo(0);
    }
}
