package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.ports.in.registro.CerrarPostDiarioComunidadUseCase;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-438, contra Postgres real: quien publica en el Muro ANTES de que exista su registro del dia
 * igual queda con el habito de post diario cumplido.
 *
 * <p>El caso de la base local que lo mostro: publicacion a las 18:33 UTC, registro del dia creado
 * a las 18:41 UTC (lo genero la app al abrir Training), y el registro PENDIENTE para siempre. En
 * produccion lo mismo le pasa a cualquier cuenta que el barrido de las 05:02 UTC no alcanzo (activada
 * ese dia, backend caido a la medianoche) y a un miembro del staff que lleva su programa.
 *
 * <p>Contra el codigo anterior las dos variantes fallan: no queda ningun registro del habito ese dia
 * (el oyente devolvia sin hacer nada).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PostDiarioSinRegistroPrevioIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private CerrarPostDiarioComunidadUseCase cerrarPostDiarioUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    /** El mismo reloj que usa el servidor: el "hoy" sale de aqui, en la zona del participante (Lima). */
    @Autowired
    private Clock reloj;

    private UUID participanteId;

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa, registros_habito, publicaciones_muro y
        // los puntos. El catalogo no se toca.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"APRENDIZ", "ADMIN"})
    void publicarSinRegistroDelDiaGeneraLaJornadaYCierraElHabito(String rol) {
        Instant ahora = reloj.now();
        LocalDate hoyEnLima = ahora.atZone(LIMA).toLocalDate();
        sembrarParticipanteEnSuDiaCinco(rol, hoyEnLima);
        jdbcTemplate.update("""
                INSERT INTO renaser.publicaciones_muro (id, autor_id, tipo, texto, creado_en)
                VALUES (?, ?, 'MANUAL', 'Publico antes de abrir Training', ?)
                """, UUID.randomUUID(), participanteId, Timestamp.from(ahora));
        assertThat(estadosDelPostDiario(hoyEnLima)).as("el fixture arranca SIN registro del dia").isEmpty();

        transactionTemplate.executeWithoutResult(status ->
                cerrarPostDiarioUseCase.alPublicarEnElMuro(participanteId(), ahora));

        assertThat(estadosDelPostDiario(hoyEnLima)).containsExactly("COMPLETADO");
    }

    /** Dia 5 coherente con sus fechas: fecha_inicio = hoy − 4 (regla 03, fixtures). */
    private void sembrarParticipanteEnSuDiaCinco(String rol, LocalDate hoyEnLima) {
        participanteId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture post sin registro', ?::renaser.rol_usuario, 'ACTIVO')
                """, participanteId, participanteId + "@renaser.test", rol);
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa
                    (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone)
                VALUES (?, 5, ?, ?, 'America/Lima')
                """, participanteId, Date.valueOf(hoyEnLima.minusDays(4)),
                Timestamp.from(hoyEnLima.minusDays(5).atStartOfDay(LIMA).toInstant()));
    }

    private List<String> estadosDelPostDiario(LocalDate dia) {
        return jdbcTemplate.queryForList("""
                SELECT r.estado::text FROM renaser.registros_habito r
                JOIN renaser.habitos h ON h.id = r.habito_id
                WHERE r.participante_id = ? AND r.fecha_ejecucion = ? AND h.clave_sistema = ?
                """, String.class, participanteId, Date.valueOf(dia), PoliticaPostDiarioComunidad.CLAVE_SISTEMA);
    }

    private UserId participanteId() {
        return UserId.of(participanteId);
    }
}
