package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort.RolParticipante;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT contra Postgres real (Testcontainers). Es la pieza mas fragil de este modulo
 * (ver javadoc de ConsultarProgresoParticipantePersistenceAdapter): confirma que la
 * query nativa con JOIN a `usuarios` devuelve los valores ENUM (rol_usuario,
 * estado_usuario) de forma legible via String.valueOf(...), no solo que compile.
 * Cubre los 5 valores de rol_usuario (mapeo completo, para que un rol nuevo que se
 * agregue a la base y no a mapearRol() falle ACA, no en produccion).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ConsultarProgresoParticipantePersistenceAdapterTest {

    @Autowired
    private ConsultarProgresoParticipantePersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId crearParticipante(String rolCrudo, String estadoCrudo, int diaPrograma) {
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
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, :dia)
                        """)
                .setParameter("id", id.value())
                .setParameter("dia", diaPrograma)
                .executeUpdate();
        return id;
    }

    @Test
    void devuelveDiaProgramaYRolDeUnAprendizActivo() {
        UserId id = crearParticipante("APRENDIZ", "ACTIVO", 42);

        var progreso = adapter.deParticipante(id);

        assertThat(progreso).isPresent();
        assertThat(progreso.get().diaPrograma()).isEqualTo(42);
        assertThat(progreso.get().rol()).isEqualTo(RolParticipante.TRAINEE);
        assertThat(progreso.get().suspendido()).isFalse();
    }

    @Test
    void marcaSuspendidoCuandoElEstadoEsSuspendido() {
        UserId id = crearParticipante("MENTOR", "SUSPENDIDO", 10);

        var progreso = adapter.deParticipante(id);

        assertThat(progreso).isPresent();
        assertThat(progreso.get().suspendido()).isTrue();
        assertThat(progreso.get().rol()).isEqualTo(RolParticipante.MENTOR);
    }

    @Test
    void mapeaLosCincoRolesDeUsuario() {
        assertThat(adapter.deParticipante(crearParticipante("APRENDIZ", "ACTIVO", 1)).get().rol())
                .isEqualTo(RolParticipante.TRAINEE);
        assertThat(adapter.deParticipante(crearParticipante("MENTOR", "ACTIVO", 1)).get().rol())
                .isEqualTo(RolParticipante.MENTOR);
        assertThat(adapter.deParticipante(crearParticipante("LIDER_MENTORES", "ACTIVO", 1)).get().rol())
                .isEqualTo(RolParticipante.MENTOR_LEAD);
        assertThat(adapter.deParticipante(crearParticipante("ADMIN", "ACTIVO", 1)).get().rol())
                .isEqualTo(RolParticipante.ADMIN);
        assertThat(adapter.deParticipante(crearParticipante("ALQUIMISTA", "ACTIVO", 1)).get().rol())
                .isEqualTo(RolParticipante.ALCHEMIST);
    }

    @Test
    void devuelveVacioSiElParticipanteNoExiste() {
        assertThat(adapter.deParticipante(UserId.of(UUID.randomUUID()))).isEmpty();
    }
}
