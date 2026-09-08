package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.mapa;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.mapa.LoadMapaPort;
import com.renaser.os.onboarding.application.ports.out.mapa.ReemplazarListaMapaPort;
import com.renaser.os.onboarding.domain.model.mapa.AccionMapa;
import com.renaser.os.onboarding.domain.model.mapa.AccionesDelMapa;
import com.renaser.os.onboarding.domain.model.mapa.AreaMapa;
import com.renaser.os.onboarding.domain.model.mapa.EvidenciaAccion;
import com.renaser.os.onboarding.domain.model.mapa.MomentoAccion;
import com.renaser.os.onboarding.domain.model.mapa.ProtocoloReemplazoMapa;
import com.renaser.os.onboarding.domain.model.mapa.ProtocolosDelMapa;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra Postgres real, porque lo que se verifica es SQL: las tres tablas de `V41`, la
 * `@ElementCollection` sobre `dias_accion_mapa` y —lo importante— que reemplazar el paso NO pierda
 * el habito ya vinculado.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MapaPersistenceAdapterTest {

    @Autowired private LoadMapaPort loadMapaPort;
    @Autowired private ReemplazarListaMapaPort reemplazarPort;
    @Autowired private EtapaOnboardingPort etapaPort;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock clock;

    private UserId aprendiz;

    @BeforeEach
    void crearAprendiz() {
        aprendiz = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture mapa', CAST('APRENDIZ' AS renaser.rol_usuario),
                                CAST('ACTIVO' AS renaser.estado_usuario))
                        """)
                .setParameter("id", aprendiz.value())
                .setParameter("email", aprendiz + "@renaser.test")
                .executeUpdate();
    }

    private AccionMapa accion(String accionId, AreaMapa area, String texto) {
        return AccionMapa.crear(UUID.randomUUID(), aprendiz, accionId, area, texto, 3,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), MomentoAccion.MANANA,
                EvidenciaAccion.FOTO, clock);
    }

    @Test
    void guardaYDevuelveLasAccionesConSusDias() {
        reemplazarPort.reemplazarAcciones(aprendiz,
                new AccionesDelMapa(List.of(accion("a1", AreaMapa.SALUD, "Caminar 40 minutos"))));
        entityManager.flush();
        entityManager.clear();

        var guardadas = loadMapaPort.accionesDe(aprendiz).acciones();

        assertThat(guardadas).singleElement().satisfies(a -> {
            assertThat(a.accionId()).isEqualTo("a1");
            assertThat(a.area()).isEqualTo(AreaMapa.SALUD);
            assertThat(a.frecuenciaSemanal()).isEqualTo(3);
            assertThat(a.dias()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.FRIDAY);
            assertThat(a.yaEsHabito()).isFalse();
        });
    }

    /**
     * <b>La prueba que importa.</b> Si al reeditar V06 se perdiera el `habito_id`, el proximo
     * "Activar mi mapa" volveria a crear los habitos y el aprendiz terminaria con duplicados —
     * exactamente lo que AC-07 no permite.
     */
    @Test
    void reemplazarElPasoConservaElHabitoYaVinculado() {
        reemplazarPort.reemplazarAcciones(aprendiz,
                new AccionesDelMapa(List.of(accion("a1", AreaMapa.SALUD, "Caminar 40 minutos"),
                        accion("a2", AreaMapa.RELACIONES, "Llamar a mi madre"))));
        entityManager.flush();
        UUID habito = crearHabitoDeSistema();
        entityManager.createNativeQuery(
                        "UPDATE renaser.acciones_mapa SET habito_id = :h WHERE usuario_id = :u AND accion_id = 'a1'")
                .setParameter("h", habito).setParameter("u", aprendiz.value()).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // El aprendiz vuelve a V06 y cambia el texto de a1, saca a2 y agrega a3.
        reemplazarPort.reemplazarAcciones(aprendiz,
                new AccionesDelMapa(List.of(accion("a1", AreaMapa.SALUD, "Caminar 60 minutos"),
                        accion("a3", AreaMapa.NEGOCIO_DINERO, "Llamar a tres clientes"))));
        entityManager.flush();
        entityManager.clear();

        var guardadas = loadMapaPort.accionesDe(aprendiz).acciones();

        assertThat(guardadas).hasSize(2);
        assertThat(guardadas).anySatisfy(a -> {
            assertThat(a.accionId()).isEqualTo("a1");
            assertThat(a.texto()).isEqualTo("Caminar 60 minutos");
            assertThat(a.habitoId()).isEqualTo(habito);
        });
        assertThat(guardadas).anySatisfy(a -> {
            assertThat(a.accionId()).isEqualTo("a3");
            assertThat(a.habitoId()).isNull();
        });
    }

    /**
     * Un habito REAL, no un UUID inventado: `acciones_mapa.habito_id` tiene FK contra `habitos`, y
     * la primera version de este test se puso roja justamente por eso — que es la FK haciendo lo
     * que se le pidio. La categoria sale del catalogo sembrado por V4, no de un literal.
     */
    private UUID crearHabitoDeSistema() {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.habitos (id, ambito, titulo, categoria_clave)
                        VALUES (:id, CAST('SISTEMA' AS renaser.ambito_habito), 'Habito de fixture',
                                (SELECT clave FROM renaser.categorias_habito ORDER BY clave LIMIT 1))
                        """)
                .setParameter("id", id)
                .executeUpdate();
        return id;
    }

    @Test
    void guardaYDevuelveLosProtocolosDeReemplazo() {
        reemplazarPort.reemplazarProtocolos(aprendiz, new ProtocolosDelMapa(List.of(
                ProtocoloReemplazoMapa.crear(UUID.randomUUID(), aprendiz, "p1", "scroll",
                        "termino de almorzar", "abrir Instagram 30 minutos", "caminar 10 minutos", clock))));
        entityManager.flush();
        entityManager.clear();

        assertThat(loadMapaPort.protocolosDe(aprendiz).protocolos()).singleElement()
                .satisfies(p -> assertThat(p.frase()).startsWith("Cuando termino de almorzar"));
    }

    /** La marca es POR FLUJO: terminar el mapa no puede marcar terminada la ficha inicial. */
    @Test
    void laEtapaSeMarcaPorFlujoYEsIdempotente() {
        etapaPort.marcarCompletada(aprendiz, "mapa_dia7");
        etapaPort.marcarCompletada(aprendiz, "mapa_dia7");
        entityManager.flush();
        entityManager.clear();

        assertThat(etapaPort.flujosCompletados(aprendiz)).containsExactly("mapa_dia7");
    }
}
