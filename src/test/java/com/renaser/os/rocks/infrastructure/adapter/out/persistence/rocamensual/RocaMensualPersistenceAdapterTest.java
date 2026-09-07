package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamensual;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensualId;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ida y vuelta contra Postgres. Lo que de verdad se prueba aca es la consulta por participante:
 * {@code rocas_mensuales} no guarda a quien pertenece —cuelga de la Roca Maestra (V36)— asi que
 * esa lectura pasa por {@code rocas_maestras}, y un join equivocado devolveria los tramos de otra
 * persona sin fallar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RocaMensualPersistenceAdapterTest {

    @Autowired
    private RocaMensualPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;
    private RocaMaestraId maestraTrabajo;

    @BeforeEach
    void seedParticipanteConSuObjetivoDeNoventaDias() {
        participanteId = crearParticipante();
        maestraTrabajo = crearMaestra(participanteId, EjeObjetivo.TRABAJO);
    }

    private UserId crearParticipante() {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", "Fixture")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 35)
                        """)
                .setParameter("id", id.value())
                .executeUpdate();
        return id;
    }

    private RocaMaestraId crearMaestra(UserId duenio, EjeObjetivo eje) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.rocas_maestras (id, participante_id, eje, objetivo)
                        VALUES (:id, :participante, CAST(:eje AS renaser.eje_objetivo), :objetivo)
                        """)
                .setParameter("id", id)
                .setParameter("participante", duenio.value())
                .setParameter("eje", eje.name())
                .setParameter("objetivo", "objetivo " + eje)
                .executeUpdate();
        entityManager.flush();
        return RocaMaestraId.of(id);
    }

    private RocaMensual tramo(RocaMaestraId maestra, int numeroMes, MetaCuantitativa meta) {
        Instant ahora = Instant.now();
        return RocaMensual.definir(RocaMensualId.of(UUID.randomUUID()), maestra, numeroMes,
                "tramo del mes " + numeroMes, meta, ahora);
    }

    @Test
    void guardaYRecuperaLosTramosDelParticipante() {
        adapter.guardar(tramo(maestraTrabajo, 1, null));
        adapter.guardar(tramo(maestraTrabajo, 2, MetaCuantitativa.nueva(new BigDecimal("10000.00"), "USD")));

        List<RocaMensual> encontrados = adapter.deParticipante(participanteId);

        assertThat(encontrados).extracting(RocaMensual::numeroMes).containsExactly(1, 2);
        assertThat(encontrados.get(0).tieneMeta()).as("tramo cualitativo: la meta vuelve en null").isFalse();
        assertThat(encontrados.get(1).meta().objetivo()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(encontrados.get(1).meta().unidad()).isEqualTo("USD");
    }

    @Test
    void noDevuelveLosTramosDeOtroParticipante() {
        UserId otro = crearParticipante();
        adapter.guardar(tramo(crearMaestra(otro, EjeObjetivo.CUERPO), 1, null));

        assertThat(adapter.deParticipante(participanteId)).isEmpty();
    }

    @Test
    void deMaestraYMesEncuentraElCorrecto() {
        adapter.guardar(tramo(maestraTrabajo, 3, null));

        assertThat(adapter.deMaestraYMes(maestraTrabajo, 3)).isPresent();
        assertThat(adapter.deMaestraYMes(maestraTrabajo, 1)).isEmpty();
    }
}
