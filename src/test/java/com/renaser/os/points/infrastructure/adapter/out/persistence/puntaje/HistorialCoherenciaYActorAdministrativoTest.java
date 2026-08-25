package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT con Testcontainers de dos adaptadores chicos que no ameritan un archivo cada uno:
 * el upsert de historial_coherencia y la consulta nativa de VerificarActorAdministrativoPort
 * (el porque de la consulta SQL nativa en vez de users.api esta documentado en
 * VerificarActorAdministrativoPort).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class HistorialCoherenciaYActorAdministrativoTest {

    @Autowired
    private HistorialCoherenciaPersistenceAdapter historialAdapter;
    @Autowired
    private ActorAdministrativoPersistenceAdapter actorAdapter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID participanteId;
    private UUID adminId;
    private UUID traineeId;
    private UUID adminSuspendidoId;

    @BeforeEach
    void crearUsuarios() {
        participanteId = crearUsuario("APRENDIZ", "ACTIVO");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id) VALUES (?)", participanteId);

        adminId = crearUsuario("ADMIN", "ACTIVO");
        traineeId = crearUsuario("APRENDIZ", "ACTIVO");
        adminSuspendidoId = crearUsuario("ADMIN", "SUSPENDIDO");
    }

    private UUID crearUsuario(String rol, String estado) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Usuario de Prueba', ?::renaser.rol_usuario, ?::renaser.estado_usuario)
                """, id, "u-" + id + "@renaser.com", rol, estado);
        return id;
    }

    @Test
    void upsertEscribeYLuegoSobreescribeElMismoDia() {
        LocalDate fecha = LocalDate.of(2026, 8, 24);

        historialAdapter.upsert(UserId.of(participanteId), fecha, new BigDecimal("70.00"));
        historialAdapter.upsert(UserId.of(participanteId), fecha, new BigDecimal("85.50"));

        BigDecimal valor = jdbcTemplate.queryForObject(
                "SELECT valor FROM renaser.historial_coherencia WHERE participante_id = ? AND fecha = ?",
                BigDecimal.class, participanteId, fecha);
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.historial_coherencia WHERE participante_id = ?", Long.class,
                participanteId);

        assertThat(valor).isEqualByComparingTo("85.50");
        assertThat(total).isEqualTo(1L);
    }

    @Test
    void unAdminActivoEsAdministrativo() {
        assertThat(actorAdapter.esAdministrativoActivo(UserId.of(adminId))).isTrue();
    }

    @Test
    void unAprendizNoEsAdministrativo() {
        assertThat(actorAdapter.esAdministrativoActivo(UserId.of(traineeId))).isFalse();
    }

    @Test
    void unAdminSuspendidoNoEsAdministrativo() {
        assertThat(actorAdapter.esAdministrativoActivo(UserId.of(adminSuspendidoId))).isFalse();
    }

    @Test
    void unActorInexistenteNoEsAdministrativo() {
        assertThat(actorAdapter.esAdministrativoActivo(UserId.of(UUID.randomUUID()))).isFalse();
    }
}
