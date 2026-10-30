package com.renaser.os.habits.infrastructure.adapter.out.persistence.radar;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar.Modo;
import com.renaser.os.habits.domain.model.radar.MiRadar;
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
 * Mi Radar (spec 019, V122) contra Postgres real, por el camino completo: el adaptador de {@code habits} →
 * {@code users.api.MiRadarGuardadoPort} → {@code participantes_programa.mi_radar_encendido} y {@code dias_mi_radar}.
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

    @Test
    void sinNadaGuardadoEstaApagadoYConTodosLosDiasDeFabrica() {
        assertThat(adapter.deParticipante(participanteId)).isEqualTo(MiRadar.apagado());
    }

    @Test
    void guardaYLeeElEncendidoYLosDiasQueNoSonDeFabrica() {
        MiRadar miRadar = new MiRadar(true, List.of(new DiaDeMiRadar(6, Modo.PERSONALIZADO, 3, false),
                new DiaDeMiRadar(7, Modo.AUTOMATICO, null, true)));

        adapter.guardar(participanteId, miRadar);

        MiRadar leido = adapter.deParticipante(participanteId);
        assertThat(leido).isEqualTo(miRadar);
        assertThat(leido.dia(6).veces()).isEqualTo(3);
        assertThat(leido.dia(3)).isEqualTo(DiaDeMiRadar.normal(3));
    }

    @Test
    void apagarConservaLosDiasYEncenderLosEncuentra() {
        adapter.guardar(participanteId, new MiRadar(true, List.of(new DiaDeMiRadar(6, Modo.PERSONALIZADO, 4, false))));

        adapter.guardar(participanteId, adapter.deParticipante(participanteId).conEncendido(false));

        MiRadar apagado = adapter.deParticipante(participanteId);
        assertThat(apagado.encendido()).isFalse();
        assertThat(apagado.dia(6).veces()).isEqualTo(4);
    }

    @Test
    void sinFilaDeProgramaNoGuardaNiLee() {
        UserId sinPrograma = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> adapter.deParticipante(sinPrograma)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> adapter.guardar(sinPrograma, MiRadar.apagado()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
