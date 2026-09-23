package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocasemanal;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT del adaptador del objetivo semanal contra Postgres de verdad.
 *
 * > <b>Corregido el 2026-09-22.</b> Decia <i>"cubre el riesgo real de este adaptador: el
 * > @ElementCollection de `acciones_criticas`"</i>, y sus dos primeros tests guardaban y releian
 * > esa coleccion. La tabla se borro en la V62 (estaba vacia) y el mapeo con ella. El riesgo real
 * > paso a ser el contrario —<b>que quede algo apuntando a una tabla que ya no existe</b>— y eso
 * > es justo lo que verifica el ida y vuelta de abajo: si sobreviviera un solo rastro del mapeo,
 * > Hibernate fallaria al arrancar o el SELECT moriria con "relation does not exist".
 */
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
        rocaMaestraId = RocaMaestraId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.rocas_maestras (id, participante_id, eje, objetivo)
                        VALUES (:id, :pid, CAST('CUERPO' AS renaser.eje_objetivo), 'objetivo')
                        """)
                .setParameter("id", rocaMaestraId.value())
                .setParameter("pid", participanteId.value())
                .executeUpdate();
    }

    /** El id ya no lo sortea la factoria: entra por parametro (puerto IdGenerator). */
    private static RocaSemanalId unId() {
        return RocaSemanalId.of(UUID.randomUUID());
    }

    @Test
    void guardaYRecuperaElObjetivoDeLaSemanaEntero() {
        RocaSemanal roca = RocaSemanal.planificar(unId(), rocaMaestraId, 3, "Titulo", "obstaculo",
                "contingencia", 6, CLOCK);

        adapter.save(roca);
        entityManager.flush();
        entityManager.clear();

        var recuperada = adapter.byId(roca.id());
        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().titulo()).isEqualTo("Titulo");
        assertThat(recuperada.get().numeroSemana()).isEqualTo(3);
        assertThat(recuperada.get().obstaculo()).isEqualTo("obstaculo");
        assertThat(recuperada.get().contingencia()).isEqualTo("contingencia");
        assertThat(recuperada.get().autoevaluacionInicio()).isEqualTo(6);
    }

    @Test
    void deMaestraYSemanaEncuentraLaRocaDeEsaSemana() {
        RocaSemanal roca = RocaSemanal.planificar(unId(), rocaMaestraId, 5, "T", null, null, null, CLOCK);
        adapter.save(roca);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.deMaestraYSemana(rocaMaestraId, 5)).isPresent();
        assertThat(adapter.deMaestraYSemana(rocaMaestraId, 6)).isEmpty();
    }

    @Test
    void actualizarYGuardarSobreescribeLoEditado() {
        RocaSemanal roca = RocaSemanal.planificar(unId(), rocaMaestraId, 2, "T", null, null, null, CLOCK);
        roca = adapter.save(roca);
        roca.actualizarPlanificacion("Titulo corregido", "obstaculo nuevo", null, 8, CLOCK);

        adapter.save(roca);
        entityManager.flush();
        entityManager.clear();

        var recuperada = adapter.byId(roca.id());
        assertThat(recuperada.get().titulo()).isEqualTo("Titulo corregido");
        assertThat(recuperada.get().obstaculo()).isEqualTo("obstaculo nuevo");
        assertThat(recuperada.get().autoevaluacionInicio()).isEqualTo(8);
    }
}
