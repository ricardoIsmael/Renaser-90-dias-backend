package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.application.ports.in.santuario.CerrarRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.CerrarRachaUseCase.CerrarRachaCommand;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.santuario.SaveRachaSinCelularPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelularId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regresion de C-9 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html) para
 * {@link RachaService#cerrar}: mismo defecto que {@code CompletarRegistroExpiracionTransaccionIT}
 * pero del lado de "Dia sin celular" — antes, cerrar una racha cuyo plazo ya vencio
 * revertia, junto con el {@code throw}, el {@code racha.expirar()+save} Y el
 * {@code liberarRegistro} que se acababan de guardar. La racha quedaba ACTIVA para
 * siempre y {@code rachas_viva_uk} le impedia al aprendiz iniciar otra.
 *
 * <p>Requiere Postgres real por el mismo motivo que la version de registro: el defecto
 * es un rollback real de una transaccion real, invisible con mocks.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CerrarRachaExpiracionTransaccionIT {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private CerrarRachaUseCase cerrarUseCase;
    @Autowired
    private SaveRegistroHabitoPort saveRegistroPort;
    @Autowired
    private SaveRachaSinCelularPort saveRachaPort;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private UserId participanteId;
    private HabitoId habitoId;

    @BeforeEach
    void seedFixtures() {
        participanteId = UserId.of(UUID.randomUUID());
        // El habito de sistema 'dia sin celular' ya viene sembrado por
        // V4__catalogo_habitos_default.sql y su clave_sistema es UNIQUE: insertar uno propio
        // choca contra habitos_clave_sistema_key. Se usa el del catalogo y no se borra al final.
        habitoId = HabitoId.of(jdbcTemplate.queryForObject(
                "SELECT id FROM renaser.habitos WHERE clave_sistema = ?", UUID.class,
                RachaService.CLAVE_SISTEMA_SIN_CELULAR));

        // El EntityManager compartido exige una transaccion activa para executeUpdate;
        // @BeforeEach no la trae. La semilla commitea aparte, que es lo que la prueba
        // necesita: los datos deben existir ANTES de que corra el caso de uso.
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
        });
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa / registros_habito / rachas_sin_celular.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
    }

    /** Iniciada 60 dias atras: {@code plazoCierre} (24h+3h) vencio hace rato, sin depender del reloj real. */
    private RachaSinCelularId seedRachaActivaMuyVencida() {
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), participanteId,
                habitoId, LocalDate.of(2026, 6, 20), 5, TipoDia.DISCIPLINA, false, CLOCK.now());
        registro.iniciar(CLOCK.now());
        RegistroHabito guardado = saveRegistroPort.save(registro);

        RachaSinCelularId rachaId = RachaSinCelularId.of(UUID.randomUUID());
        RachaSinCelular racha = RachaSinCelular.iniciar(rachaId, participanteId, guardado.id(), 24,
                CLOCK.now().minus(Duration.ofDays(60)));
        saveRachaPort.save(racha);
        return rachaId;
    }

    private String estadoRachaEnBaseDe(RachaSinCelularId id) {
        return jdbcTemplate.queryForObject("SELECT estado FROM renaser.rachas_sin_celular WHERE id = ?", String.class,
                id.value());
    }

    @Test
    @DisplayName("C-9: cerrar() sobre una racha con el plazo vencido lanza, pero la expiracion queda persistida")
    void cerrarSobreRachaVencidaPersisteLaExpiracionPeseAlThrow() {
        RachaSinCelularId id = seedRachaActivaMuyVencida();

        assertThatThrownBy(() -> cerrarUseCase.cerrar(
                new CerrarRachaCommand(participanteId, TipoEvidencia.TEXTO, null, null, "una nota", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vencio");

        // El corazon de C-9: sin el arreglo, esto devolveria ACTIVA (el rollback revertia el
        // save de la expiracion junto con el throw), y rachas_viva_uk le impediria al
        // aprendiz iniciar una racha nueva para siempre.
        assertThat(estadoRachaEnBaseDe(id)).as("la expiracion sobrevive al throw, no la revierte el rollback")
                .isEqualTo("EXPIRADA");
    }

    @Test
    @DisplayName("C-9: tras el cierre fallido por vencimiento, el aprendiz puede iniciar una racha nueva")
    void trasElVencimientoNoQuedaBloqueadoPorRachasVivaUk() {
        seedRachaActivaMuyVencida();
        assertThatThrownBy(() -> cerrarUseCase.cerrar(
                new CerrarRachaCommand(participanteId, TipoEvidencia.TEXTO, null, null, "una nota", null)))
                .isInstanceOf(IllegalStateException.class);

        // Si la racha vieja siguiera ACTIVA, esta segunda insercion chocaria contra
        // rachas_viva_uk (a lo sumo UNA racha ACTIVA por aprendiz).
        Long activas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.rachas_sin_celular WHERE participante_id = ? AND estado = 'ACTIVA'",
                Long.class, participanteId.value());
        assertThat(activas).as("ninguna racha ACTIVA le queda al aprendiz tras el vencimiento").isZero();
    }
}
