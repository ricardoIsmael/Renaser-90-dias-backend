package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.RegistrosDeHabitoFinder;
import com.renaser.os.habits.api.RegistrosDeHabitoFinder.RegistroDeHabito;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code RegistrosDeHabitoFinder} contra Postgres real (spec 015): lo que {@code registros_habito} guardó de UNA
 * persona en un rango, con el instante en que cumplió cada hábito. Corre en una transacción que se deshace.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RegistrosDelRangoJdbcAdapterIT {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 19);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 25);

    @Autowired
    private RegistrosDeHabitoFinder finder;
    @Autowired
    private JdbcClient jdbcClient;

    private UserId persona;
    private UserId otra;
    private UUID habito;

    @BeforeEach
    void preparar() {
        persona = aprendiz();
        otra = aprendiz();
        habito = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                        VALUES (:id, :titulo, (SELECT clave FROM renaser.categorias_habito LIMIT 1))
                        """).param("id", habito).param("titulo", "H " + habito).update();
    }

    @Test
    @DisplayName("trae estado, opcional e instante de cumplimiento de los dias del rango, de la persona pedida")
    void delRango() {
        Instant cumplido = Instant.parse("2026-10-20T12:30:00Z");
        registro(persona, DESDE, "COMPLETADO", false, cumplido);
        registro(persona, DESDE.plusDays(1), "EXPIRADO", true, null);
        registro(persona, DESDE.minusDays(1), "COMPLETADO", false, cumplido);
        registro(persona, HASTA.plusDays(1), "COMPLETADO", false, cumplido);
        registro(otra, DESDE, "COMPLETADO", false, cumplido);

        List<RegistroDeHabito> registros = finder.deParticipanteEntre(persona, DESDE, HASTA);

        assertThat(registros).containsExactly(
                new RegistroDeHabito(habito, DESDE, EstadoObligacion.COMPLETADO, false, cumplido),
                new RegistroDeHabito(habito, DESDE.plusDays(1), EstadoObligacion.EXPIRADO, true, null));
    }

    @Test
    @DisplayName("un rango al reves se rechaza y sin registros no hay nada")
    void rangoYVacio() {
        assertThat(finder.deParticipanteEntre(persona, DESDE, HASTA)).isEmpty();
        assertThatThrownBy(() -> finder.deParticipanteEntre(persona, HASTA, DESDE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UserId aprendiz() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture registros', 'APRENDIZ', 'ACTIVO')
                        """).param("id", id).param("email", id + "@renaser.test").update();
        jdbcClient.sql("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                                                                    programa_activado_en, timezone)
                        VALUES (:id, 12, :inicio, :activado, 'America/Lima')
                        """)
                .param("id", id).param("inicio", DESDE.minusDays(10))
                .param("activado", Timestamp.from(Instant.parse("2026-10-01T15:00:00Z"))).update();
        return UserId.of(id);
    }

    private void registro(UserId participante, LocalDate fecha, String estado, boolean opcional, Instant cumplidoEn) {
        jdbcClient.sql("""
                        INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion,
                                                             dia_programa, tipo_dia, es_opcional, estado, completado_en)
                        VALUES (:id, :pid, :hid, :fecha, 1, CAST('DISCIPLINA' AS renaser.tipo_dia), :opcional,
                                CAST(:estado AS renaser.estado_registro), :completadoEn)
                        """)
                .param("id", UUID.randomUUID()).param("pid", participante.value()).param("hid", habito)
                .param("fecha", fecha).param("opcional", opcional).param("estado", estado)
                .param("completadoEn", cumplidoEn == null ? null : Timestamp.from(cumplidoEn)).update();
    }
}
