package com.renaser.os.habits.infrastructure.adapter.out.persistence.radar;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar.DiaPropio;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mi Radar (spec 012, V119) contra Postgres real, por el camino completo: el adaptador de {@code habits} →
 * {@code users.api.HorasDeMiRadarPort} → {@code participantes_programa.horas_mi_radar} (las horas de todos los dias) y
 * {@code dias_mi_radar} (los dias con horas propias, jsonb). NULL es apagado y ningun dia propio.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MiRadarPersistenceAdapterTest {

    @Autowired
    private MiRadarPersistenceAdapter adapter;
    @Autowired
    private EntityManager entityManager;

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
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone)
                        VALUES (:usuarioId, 10, 'America/Argentina/Buenos_Aires')
                        """)
                .setParameter("usuarioId", participanteId.value())
                .executeUpdate();
    }

    /** Pregunta por el NULL en la base y no lee el arreglo: asi la prueba no depende de como Hibernate mapee un smallint[]. */
    private boolean horasSonNull() {
        return (Boolean) columna("horas_mi_radar IS NULL");
    }

    private Object columna(String nombre) {
        return entityManager.createNativeQuery(
                        "SELECT " + nombre + " FROM renaser.participantes_programa WHERE usuario_id = :id")
                .setParameter("id", participanteId.value()).getSingleResult();
    }

    @Test
    void sinHorasGuardadasEstaApagado() {
        assertThat(adapter.deParticipante(participanteId)).isEqualTo(MiRadar.apagado());
        assertThat(horasSonNull()).isTrue();
    }

    @Test
    void guardaYLeeLasHorasDeMenorAMayor() {
        adapter.guardar(participanteId, MiRadar.encendido(List.of(20, 9, 14)));

        MiRadar leido = adapter.deParticipante(participanteId);

        assertThat(leido.estaEncendido()).isTrue();
        assertThat(leido.horas()).containsExactly(9, 14, 20);
    }

    @Test
    void unaSolaHoraYLosExtremosFuncionan() {
        adapter.guardar(participanteId, MiRadar.encendido(List.of(3)));
        assertThat(adapter.deParticipante(participanteId).horas()).containsExactly(3);

        adapter.guardar(participanteId, MiRadar.encendido(List.of(3, 23)));
        assertThat(adapter.deParticipante(participanteId).horas()).containsExactly(3, 23);
    }

    @Test
    void doceHorasCaben() {
        List<Integer> doce = List.of(8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19);

        adapter.guardar(participanteId, MiRadar.encendido(doce));

        assertThat(adapter.deParticipante(participanteId).horas()).containsExactlyElementsOf(doce);
    }

    @Test
    void cambiarLasHorasReemplazaLasAnteriores() {
        adapter.guardar(participanteId, MiRadar.encendido(List.of(9, 14, 20)));
        adapter.guardar(participanteId, MiRadar.encendido(List.of(7)));

        assertThat(adapter.deParticipante(participanteId).horas()).containsExactly(7);
    }

    @Test
    void apagarGuardaNullYNoDejaHorasViejas() {
        adapter.guardar(participanteId, MiRadar.encendido(List.of(9, 14, 20)));

        adapter.guardar(participanteId, MiRadar.apagado());

        assertThat(adapter.deParticipante(participanteId).estaEncendido()).isFalse();
        assertThat(horasSonNull()).isTrue();
    }

    @Test
    void guardarMiRadarNoTocaElRestoDeLaFilaDelPrograma() {
        adapter.guardar(participanteId, MiRadar.encendido(List.of(9)));

        assertThat(((Number) columna("dia_programa")).intValue()).isEqualTo(10);
        assertThat(columna("timezone")).isEqualTo("America/Argentina/Buenos_Aires");
    }

    @Test
    void guardaYLeeLosDiasConHorasPropiasYElDiaSinRadar() {
        MiRadar miRadar = MiRadar.encendido(List.of(9, 14, 20),
                List.of(new DiaPropio(7, List.of()), new DiaPropio(6, List.of(11, 10))));

        adapter.guardar(participanteId, miRadar);
        MiRadar leido = adapter.deParticipante(participanteId);

        assertThat(leido).isEqualTo(miRadar);
        assertThat(leido.dias()).extracting(DiaPropio::dia).containsExactly(6, 7);
        assertThat(leido.dias().get(0).horas()).containsExactly(10, 11);
        assertThat(leido.dias().get(1).horas()).isEmpty();
        assertThat((Boolean) columna("dias_mi_radar IS NULL")).isFalse();
    }

    @Test
    void cambiarLasHorasSinDiasQuitaLosDiasPropiosAnteriores() {
        adapter.guardar(participanteId, MiRadar.encendido(List.of(9), List.of(new DiaPropio(6, List.of(11)))));

        adapter.guardar(participanteId, MiRadar.encendido(List.of(9)));

        assertThat(adapter.deParticipante(participanteId).dias()).isEmpty();
        assertThat((Boolean) columna("dias_mi_radar IS NULL")).isTrue();
    }

    @Test
    void apagarTambienDejaLosDiasEnNull() {
        adapter.guardar(participanteId, MiRadar.encendido(List.of(9), List.of(new DiaPropio(6, List.of(11)))));

        adapter.guardar(participanteId, MiRadar.apagado());

        assertThat(adapter.deParticipante(participanteId)).isEqualTo(MiRadar.apagado());
        assertThat((Boolean) columna("dias_mi_radar IS NULL")).isTrue();
        assertThat(horasSonNull()).isTrue();
    }

    @Test
    void quienNoTieneProgramaEsNoEncontrado() {
        UserId sinPrograma = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> adapter.deParticipante(sinPrograma)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> adapter.guardar(sinPrograma, MiRadar.apagado()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
