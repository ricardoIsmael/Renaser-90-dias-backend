package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.HorasDeMiRadarPort.HorasDeMiRadar;
import com.renaser.os.users.api.HorasDeMiRadarPort.HorasDeUnDia;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code participantes_programa.horas_mi_radar} y {@code dias_mi_radar} (V119) contra Postgres real: ida y vuelta de las
 * horas de todos los dias por el texto separado por comas y de los dias con horas propias por el {@code jsonb},
 * {@code NULL} para apagado y para ningun dia propio, y los CHECK de la base como segunda barrera (SC-008): el adaptador
 * NO valida, asi que una lista fuera de las reglas llega a la base y la base la rechaza.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HorasDeMiRadarJdbcAdapterTest {

    @Autowired
    private HorasDeMiRadarJdbcAdapter adapter;
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

    private static HorasDeMiRadar horas(Integer... horas) {
        return new HorasDeMiRadar(List.of(horas), List.of());
    }

    /** Pregunta por el NULL de una columna en la base y no la lee: asi la prueba no depende de como se mapee. */
    private boolean esNull(String columna) {
        return (Boolean) entityManager.createNativeQuery(
                        "SELECT " + columna + " IS NULL FROM renaser.participantes_programa WHERE usuario_id = :id")
                .setParameter("id", participanteId.value()).getSingleResult();
    }

    @Test
    void unaFilaNuevaTieneMiRadarApagado() {
        assertThat(adapter.horasDe(participanteId)).isEqualTo(HorasDeMiRadar.apagado());
    }

    @Test
    void guardaYDevuelveLasHorasEnElOrdenEnQueSeGuardaron() {
        adapter.guardarHoras(participanteId, horas(9, 14, 20));

        HorasDeMiRadar leido = adapter.horasDe(participanteId);

        assertThat(leido.horas()).containsExactly(9, 14, 20);
        assertThat(leido.dias()).isEmpty();
        assertThat(esNull("dias_mi_radar")).isTrue();
    }

    @Test
    void guardaYDevuelveLosDiasConHorasPropiasYElDiaSinRadar() {
        adapter.guardarHoras(participanteId, new HorasDeMiRadar(List.of(9, 14, 20),
                List.of(new HorasDeUnDia(6, List.of(11)), new HorasDeUnDia(7, List.of()))));

        HorasDeMiRadar leido = adapter.horasDe(participanteId);

        assertThat(leido.horas()).containsExactly(9, 14, 20);
        assertThat(leido.dias()).containsExactly(new HorasDeUnDia(6, List.of(11)), new HorasDeUnDia(7, List.of()));
        assertThat(esNull("dias_mi_radar")).isFalse();
    }

    @Test
    void losDiasQuedanComoUnArregloJsonConDiaYHoras() {
        adapter.guardarHoras(participanteId, new HorasDeMiRadar(List.of(9),
                List.of(new HorasDeUnDia(1, List.of(15, 16)), new HorasDeUnDia(7, List.of()))));

        String json = entityManager.createNativeQuery(
                        "SELECT CAST(dias_mi_radar AS text) FROM renaser.participantes_programa WHERE usuario_id = :id")
                .setParameter("id", participanteId.value()).getSingleResult().toString();

        assertThat(json).isEqualTo("[{\"dia\": 1, \"horas\": [15, 16]}, {\"dia\": 7, \"horas\": []}]");
    }

    @Test
    void cambiarLasHorasSinDiasBorraLosDiasViejos() {
        adapter.guardarHoras(participanteId, new HorasDeMiRadar(List.of(9), List.of(new HorasDeUnDia(6, List.of(11)))));

        adapter.guardarHoras(participanteId, horas(10));

        assertThat(adapter.horasDe(participanteId).dias()).isEmpty();
        assertThat(esNull("dias_mi_radar")).isTrue();
    }

    @Test
    void unaListaVaciaApagaYGuardaNullEnLasDosColumnas() {
        adapter.guardarHoras(participanteId, new HorasDeMiRadar(List.of(9, 14), List.of(new HorasDeUnDia(6, List.of(11)))));

        adapter.guardarHoras(participanteId, HorasDeMiRadar.apagado());

        assertThat(adapter.horasDe(participanteId)).isEqualTo(HorasDeMiRadar.apagado());
        assertThat(esNull("horas_mi_radar")).isTrue();
        assertThat(esNull("dias_mi_radar")).isTrue();
    }

    @Test
    void apagarIgnoraLosDiasQueLleguen() {
        adapter.guardarHoras(participanteId, new HorasDeMiRadar(List.of(), List.of(new HorasDeUnDia(6, List.of(11)))));

        assertThat(adapter.horasDe(participanteId)).isEqualTo(HorasDeMiRadar.apagado());
        assertThat(esNull("dias_mi_radar")).isTrue();
    }

    @Test
    void quienNoTieneProgramaEsNoEncontrado() {
        UserId sinPrograma = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> adapter.horasDe(sinPrograma)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> adapter.guardarHoras(sinPrograma, horas(9)))
                .isInstanceOf(NoSuchElementException.class);
    }

    // Cada rechazo de la base va en su metodo: Postgres aborta la transaccion de la prueba al fallar un CHECK.

    @Test
    void laBaseRechazaLaHoraDosDeLaMadrugada() {
        assertThatThrownBy(() -> adapter.guardarHoras(participanteId, horas(2)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("participantes_programa_horas_mi_radar_validas");
    }

    @Test
    void laBaseRechazaLaHoraVeinticuatro() {
        assertThatThrownBy(() -> adapter.guardarHoras(participanteId, horas(9, 24)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laBaseRechazaTrece() {
        List<Integer> trece = IntStream.rangeClosed(8, 20).boxed().toList();

        assertThatThrownBy(() -> adapter.guardarHoras(participanteId, new HorasDeMiRadar(trece, List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laBaseRechazaDiasQueNoSonUnArreglo() {
        adapter.guardarHoras(participanteId, horas(9));

        assertThatThrownBy(() -> jdbc.sql("UPDATE renaser.participantes_programa "
                + "SET dias_mi_radar = CAST('{\"dia\": 6, \"horas\": [11]}' AS jsonb) WHERE usuario_id = :id")
                .param("id", participanteId.value()).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("participantes_programa_dias_mi_radar_validos");
    }

    @Test
    void laBaseRechazaDiasPropiosSinHorasParaTodosLosDias() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE renaser.participantes_programa "
                + "SET dias_mi_radar = CAST('[{\"dia\": 6, \"horas\": [11]}]' AS jsonb) WHERE usuario_id = :id")
                .param("id", participanteId.value()).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("participantes_programa_dias_mi_radar_validos");
    }
}
