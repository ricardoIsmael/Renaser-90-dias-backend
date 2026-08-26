package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocasemanal;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.shared.domain.FixedClock;
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

/** IT que cubre el riesgo real de este adaptador: el @ElementCollection de `acciones_criticas`. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RocaSemanalPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private RocaSemanalPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private RocaMaestraId rocaMaestraId;

    @BeforeEach
    void seedRocaMaestra() {
        UserId participanteId = UserId.of(UUID.randomUUID());
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
        rocaMaestraId = RocaMaestraId.newId();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.rocas_maestras (id, participante_id, eje, objetivo)
                        VALUES (:id, :pid, CAST('CUERPO' AS renaser.eje_objetivo), 'objetivo')
                        """)
                .setParameter("id", rocaMaestraId.value())
                .setParameter("pid", participanteId.value())
                .executeUpdate();
    }

    private static List<AccionCritica> tresAcciones() {
        return List.of(new AccionCritica(1, "uno"), new AccionCritica(2, "dos"), new AccionCritica(3, "tres"));
    }

    @Test
    void guardaYRecuperaLasTresAccionesCriticasEnOrden() {
        RocaSemanal roca = RocaSemanal.planificar(rocaMaestraId, 3, "Titulo", tresAcciones(), "obstaculo",
                "contingencia", 6, CLOCK);

        adapter.save(roca);
        entityManager.flush();
        entityManager.clear();

        var recuperada = adapter.byId(roca.id());
        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().acciones()).extracting(AccionCritica::descripcion)
                .containsExactly("uno", "dos", "tres");
    }

    @Test
    void deMaestraYSemanaEncuentraLaRocaDeEsaSemana() {
        RocaSemanal roca = RocaSemanal.planificar(rocaMaestraId, 5, "T", tresAcciones(), null, null, null, CLOCK);
        adapter.save(roca);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.deMaestraYSemana(rocaMaestraId, 5)).isPresent();
        assertThat(adapter.deMaestraYSemana(rocaMaestraId, 6)).isEmpty();
    }

    @Test
    void actualizarYGuardarSobreescribeLasAccionesCriticas() {
        RocaSemanal roca = RocaSemanal.planificar(rocaMaestraId, 2, "T", tresAcciones(), null, null, null, CLOCK);
        roca = adapter.save(roca);
        roca.actualizarPlanificacion(null,
                List.of(new AccionCritica(1, "nueva1"), new AccionCritica(2, "nueva2"),
                        new AccionCritica(3, "nueva3")),
                null, null, null, CLOCK);

        adapter.save(roca);
        entityManager.flush();
        entityManager.clear();

        var recuperada = adapter.byId(roca.id());
        assertThat(recuperada.get().acciones()).extracting(AccionCritica::descripcion)
                .containsExactly("nueva1", "nueva2", "nueva3");
    }
}
