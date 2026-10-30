package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.MiRadarGuardadoPort.DiaGuardado;
import com.renaser.os.users.api.MiRadarGuardadoPort.MiRadarGuardado;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code participantes_programa.mi_radar_encendido} y {@code dias_mi_radar} (V122) contra Postgres real: ida y vuelta de
 * lo encendido y de los dias por el {@code jsonb}, {@code NULL} para ningun dia propio, y el CHECK de la base como segunda
 * barrera: el adaptador NO valida, asi que algo que no sea un arreglo llega a la base y la base lo rechaza.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MiRadarGuardadoJdbcAdapterTest {

    @Autowired
    private MiRadarGuardadoJdbcAdapter adapter;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbc;

    private UserId participanteId;

    @BeforeEach
    void seedFixtures() {
        participanteId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                        """)
                .setParameter("id", participanteId.value())
                .setParameter("email", participanteId + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("INSERT INTO renaser.participantes_programa (usuario_id) VALUES (:id)")
                .setParameter("id", participanteId.value())
                .executeUpdate();
    }

    private boolean diasSonNull() {
        return (Boolean) entityManager.createNativeQuery(
                        "SELECT dias_mi_radar IS NULL FROM renaser.participantes_programa WHERE usuario_id = :id")
                .setParameter("id", participanteId.value()).getSingleResult();
    }

    @Test
    void unaFilaNuevaTieneMiRadarApagadoYSinDias() {
        MiRadarGuardado leido = adapter.de(participanteId);

        assertThat(leido.encendido()).isFalse();
        assertThat(leido.dias()).isEmpty();
        assertThat(diasSonNull()).isTrue();
    }

    @Test
    void guardaYDevuelveElEncendidoYLosDias() {
        adapter.guardar(participanteId, new MiRadarGuardado(true, List.of(
                new DiaGuardado(6, "PERSONALIZADO", 3, false), new DiaGuardado(7, "AUTOMATICO", null, true))));

        MiRadarGuardado leido = adapter.de(participanteId);

        assertThat(leido.encendido()).isTrue();
        assertThat(leido.dias()).containsExactly(new DiaGuardado(6, "PERSONALIZADO", 3, false),
                new DiaGuardado(7, "AUTOMATICO", null, true));
        assertThat(diasSonNull()).isFalse();
    }

    @Test
    void losDiasQuedanComoUnArregloJson() {
        adapter.guardar(participanteId, new MiRadarGuardado(true, List.of(new DiaGuardado(1, "PERSONALIZADO", 4, false))));

        String json = entityManager.createNativeQuery(
                        "SELECT CAST(dias_mi_radar AS text) FROM renaser.participantes_programa WHERE usuario_id = :id")
                .setParameter("id", participanteId.value()).getSingleResult().toString();

        assertThat(json).isEqualTo("[{\"dia\": 1, \"modo\": \"PERSONALIZADO\", \"veces\": 4, \"sinRadar\": false}]");
    }

    @Test
    void sinDiasGuardaNullYApagarConservaLosDiasQueLlegan() {
        adapter.guardar(participanteId, new MiRadarGuardado(true, List.of(new DiaGuardado(6, "PERSONALIZADO", 3, false))));

        adapter.guardar(participanteId, new MiRadarGuardado(false, List.of(new DiaGuardado(6, "PERSONALIZADO", 3, false))));
        assertThat(adapter.de(participanteId).encendido()).isFalse();
        assertThat(adapter.de(participanteId).dias()).hasSize(1);

        adapter.guardar(participanteId, new MiRadarGuardado(false, List.of()));
        assertThat(diasSonNull()).isTrue();
    }

    @Test
    void laBaseRechazaAlgoQueNoSeaUnArreglo() {
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE renaser.participantes_programa SET dias_mi_radar = CAST('{"dia": 1}' AS jsonb)
                WHERE usuario_id = :id""").param("id", participanteId.value()).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void laColumnaDeLasHorasALaV119SeElimino() {
        Integer existe = jdbc.sql("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'renaser' AND table_name = 'participantes_programa' AND column_name = 'horas_mi_radar'
                """).query(Integer.class).single();

        assertThat(existe).isZero();
    }

    @Test
    void sinFilaDeProgramaNoGuardaNiLee() {
        UserId sinPrograma = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> adapter.de(sinPrograma)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> adapter.guardar(sinPrograma, new MiRadarGuardado(true, List.of())))
                .isInstanceOf(NoSuchElementException.class);
    }
}
