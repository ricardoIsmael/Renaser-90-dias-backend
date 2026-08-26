package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamaestra;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RocaMaestraPersistenceAdapterTest {

    @Autowired
    private RocaMaestraPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;

    @BeforeEach
    void seedParticipante() {
        participanteId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", participanteId.value())
                .setParameter("email", participanteId + "@renaser.test")
                .setParameter("nombre", "Fixture")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 20)
                        """)
                .setParameter("id", participanteId.value())
                .executeUpdate();
    }

    private RocaMaestra maestra(EjeObjetivo eje) {
        return RocaMaestra.rehydrate(RocaMaestraId.newId(), participanteId, eje, "objetivo " + eje, Instant.now());
    }

    @Test
    void guardaYRecuperaLasTresRocasMaestrasDeUnParticipante() {
        insertar(maestra(EjeObjetivo.CUERPO));
        insertar(maestra(EjeObjetivo.TRABAJO));
        insertar(maestra(EjeObjetivo.RELACIONES));

        List<RocaMaestra> encontradas = adapter.deParticipante(participanteId);

        assertThat(encontradas).hasSize(3);
        assertThat(encontradas).extracting(RocaMaestra::eje)
                .containsExactlyInAnyOrder(EjeObjetivo.CUERPO, EjeObjetivo.TRABAJO, EjeObjetivo.RELACIONES);
    }

    @Test
    void deParticipanteYEjeEncuentraLaCorrecta() {
        insertar(maestra(EjeObjetivo.CUERPO));

        var encontrada = adapter.deParticipanteYEje(participanteId, EjeObjetivo.CUERPO);
        var noExiste = adapter.deParticipanteYEje(participanteId, EjeObjetivo.TRABAJO);

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().objetivo()).isEqualTo("objetivo CUERPO");
        assertThat(noExiste).isEmpty();
    }

    private RocaMaestraJpaEntity insertar(RocaMaestra m) {
        var mapper = new RocaMaestraPersistenceMapper();
        var entity = mapper.toEntity(m);
        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }
}
