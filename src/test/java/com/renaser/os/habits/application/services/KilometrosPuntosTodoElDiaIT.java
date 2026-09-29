package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-226 (confirmación del dueño del 2026-09-29): KILÓMETROS DIARIOS paga el puntaje COMPLETO si se
 * registra en cualquier momento del día local del aprendiz, hasta las 23:59.
 *
 * <p>Contra Postgres real con TODAS las migraciones, porque la regla vive en el dato: la fila de
 * {@code horarios_habito} que V86 le pone al hábito ({@code 07:00 – 23:59}). Sin V86 el horario es el
 * de V9 (07:00, sin hora límite): el ancla queda a las 07:00, el puntaje completo termina a las 10:00
 * y a las 22:30 el registro paga 0 con motivo {@code LATE_HABIT}. Estas pruebas fallan así.
 *
 * <p><b>El reloj cae en el día ANTERIOR en Lima</b> (regla 02 §3): 03:30Z del 30/09 son las 22:30 del
 * 29/09 en Lima, y 04:59Z son las 23:59. Un reloj a media mañana UTC caería el mismo día calendario y
 * no probaría que la fecha del registro es la del aprendiz y no la del servidor.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, KilometrosPuntosTodoElDiaIT.RelojMovilConfig.class})
class KilometrosPuntosTodoElDiaIT {

    private static final UUID HABITO_KM = UUID.fromString("ea87fdec-d4c1-4c4f-9e61-1557bc7255d1");
    /** El otro hábito del catálogo que termina a las 23:59, desde V4. */
    private static final UUID DIA_SIN_CELULAR = UUID.fromString("d2d58e66-db7f-4226-9c95-30b380f68b73");
    /** El día del aprendiz en Lima; en UTC ya es el 30. */
    private static final LocalDate DIA_EN_LIMA = LocalDate.of(2026, 9, 29);
    private static final int DIA_PROGRAMA = 12;

    /** 22:30 del 29/09 en Lima (UTC−5). */
    private static final Instant A_LAS_22_30_EN_LIMA = Instant.parse("2026-09-30T03:30:00Z");
    /** 23:59:30 del 29/09 en Lima: el último minuto del día. */
    private static final Instant A_LAS_23_59_EN_LIMA = Instant.parse("2026-09-30T04:59:30Z");

    /** El reloj del contexto; cada prueba lo pone donde necesita antes de actuar. */
    private static final AtomicReference<Instant> AHORA = new AtomicReference<>(A_LAS_22_30_EN_LIMA);

    @Autowired
    private CompletarRegistroUseCase completar;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID aprendiz;

    @BeforeEach
    void seed() {
        aprendiz = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture km todo el dia', 'APRENDIZ', 'ACTIVO')
                """, aprendiz, aprendiz + "@renaser.test");
        // Día 12 el 29/09: fecha de inicio coherente (29/09 − 11), regla 03.
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                        programa_activado_en, timezone)
                VALUES (?, ?, ?, now(), 'America/Lima')
                """, aprendiz, DIA_PROGRAMA, DIA_EN_LIMA.minusDays(DIA_PROGRAMA - 1));
    }

    @AfterEach
    void limpiar() {
        // CASCADE arrastra participantes_programa, registros_habito y los puntos del aprendiz.
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", aprendiz);
    }

    @Test
    @DisplayName("registrar los km a las 22:30 de Lima (03:30 UTC del día siguiente) paga los 10 puntos, no 0 por tarde")
    void aLas2230PagaCompleto() {
        Map<String, Object> fila = completarKmA(A_LAS_22_30_EN_LIMA);

        assertThat(fila).containsEntry("estado", "COMPLETADO");
        assertThat(((Number) fila.get("puntos_otorgados")).intValue()).isEqualTo(10);
        assertThat(motivosDePuntos()).as("a tiempo: ni LATE_HABIT ni HABIT_EXTENDED")
                .containsExactly("HABITO_COMPLETADO");
    }

    @Test
    @DisplayName("a las 23:59 de Lima todavía paga los 10 puntos")
    void aLas2359TodaviaPagaCompleto() {
        Map<String, Object> fila = completarKmA(A_LAS_23_59_EN_LIMA);

        assertThat(((Number) fila.get("puntos_otorgados")).intValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("V86 toca solo el horario de KILÓMETROS DIARIOS (07:00 – 23:59); el único otro que termina a las 23:59 es el de V4")
    void v86SoloTocaElHorarioDeKm() {
        assertThat(jdbc.queryForList(
                "SELECT hora_disparo, hora_limite FROM renaser.horarios_habito WHERE habito_id = ?", HABITO_KM))
                .isNotEmpty()
                .allSatisfy(h -> {
                    assertThat(h.get("hora_disparo").toString()).startsWith("07:00");
                    assertThat(h.get("hora_limite").toString()).startsWith("23:59");
                });
        // DÍA SIN CELULAR ya venía con 06:30 – 23:59 desde V4: el precedente, no un efecto de V86.
        assertThat(jdbc.queryForList("""
                SELECT DISTINCT habito_id FROM renaser.horarios_habito
                 WHERE hora_limite = ? AND habito_id <> ?
                """, UUID.class, LocalTime.of(23, 59), HABITO_KM)).containsExactly(DIA_SIN_CELULAR);
    }

    private Map<String, Object> completarKmA(Instant instante) {
        AHORA.set(instante);
        assertThat(instante.atOffset(ZoneOffset.UTC).toLocalDate())
                .as("el instante tiene que caer en el día siguiente en UTC").isAfter(DIA_EN_LIMA);
        UUID registro = registroPendienteDeKm();

        completar.completar(CompletarRegistroCommand.conValorManual(UserId.of(aprendiz),
                RegistroHabitoId.of(registro), null, null, new BigDecimal("5.20")));

        return jdbc.queryForMap(
                "SELECT estado::text AS estado, puntos_otorgados FROM renaser.registros_habito WHERE id = ?",
                registro);
    }

    private UUID registroPendienteDeKm() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa,
                                                      tipo_dia, es_opcional, estado)
                VALUES (?, ?, ?, ?, ?, 'TODOS', true, 'PENDIENTE')
                """, id, aprendiz, HABITO_KM, DIA_EN_LIMA, DIA_PROGRAMA);
        return id;
    }

    private List<String> motivosDePuntos() {
        return jdbc.queryForList(
                "SELECT motivo::text FROM renaser.ajustes_puntos_liga WHERE participante_id = ?", String.class,
                aprendiz);
    }

    @TestConfiguration
    static class RelojMovilConfig {
        @Bean
        @Primary
        Clock relojMovil() {
            return new Clock() {
                @Override
                public Instant now() {
                    return AHORA.get();
                }

                @Override
                public LocalDate today() {
                    return AHORA.get().atOffset(ZoneOffset.UTC).toLocalDate();
                }
            };
        }
    }
}
