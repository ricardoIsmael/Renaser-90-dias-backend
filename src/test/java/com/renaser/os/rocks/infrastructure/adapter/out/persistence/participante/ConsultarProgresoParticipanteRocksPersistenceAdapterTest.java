package com.renaser.os.rocks.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Copia propia (RK-1) del patron de `phasecontracts` — ver javadoc del puerto. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ConsultarProgresoParticipanteRocksPersistenceAdapterTest {

    @Autowired
    private ConsultarProgresoParticipanteRocksPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId crearParticipante(String rolCrudo, String estadoCrudo, int diaPrograma, String timezone,
                                      LocalDate fechaInicio) {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST(:rol AS renaser.rol_usuario), CAST(:estado AS renaser.estado_usuario))
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", "Fixture " + id)
                .setParameter("rol", rolCrudo)
                .setParameter("estado", estadoCrudo)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone, fecha_inicio)
                        VALUES (:id, :dia, :tz, :fecha)
                        """)
                .setParameter("id", id.value())
                .setParameter("dia", diaPrograma)
                .setParameter("tz", timezone)
                .setParameter("fecha", fechaInicio)
                .executeUpdate();
        return id;
    }

    @Test
    void devuelveDiaProgramaZonaYFechaDeUnAprendizActivo() {
        UserId id = crearParticipante("APRENDIZ", "ACTIVO", 20, "America/Lima", LocalDate.of(2026, 8, 1));

        var progreso = adapter.deParticipante(id);

        assertThat(progreso).isPresent();
        assertThat(progreso.get().diaPrograma()).isEqualTo(20);
        assertThat(progreso.get().rol()).isEqualTo(RolParticipante.TRAINEE);
        assertThat(progreso.get().suspendido()).isFalse();
        assertThat(progreso.get().zona()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(progreso.get().fechaInicio()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void marcaSuspendidoCuandoElEstadoEsSuspendido() {
        UserId id = crearParticipante("APRENDIZ", "SUSPENDIDO", 10, "America/Lima", LocalDate.of(2026, 8, 1));

        var progreso = adapter.deParticipante(id);

        assertThat(progreso).isPresent();
        assertThat(progreso.get().suspendido()).isTrue();
    }

    @Test
    void devuelveVacioSiElParticipanteNoExiste() {
        assertThat(adapter.deParticipante(UserId.of(UUID.randomUUID()))).isEmpty();
    }
}
