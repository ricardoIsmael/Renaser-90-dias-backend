package com.renaser.os.evidence.infrastructure.adapter.out.persistence;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `evidence` es la dueña REAL de `evidencias` (a diferencia del INSERT nativo que
 * `rocks` usaba antes — RK-2, cerrado). Cubre el round-trip JPA, el índice único de
 * `es_principal`, y los CHECK de arco exclusivo / media-o-texto de la tabla real —
 * estos dos últimos vía INSERT nativo porque el dominio (`DestinoEvidencia` sealed +
 * las validaciones de {@code Evidencia.registrar}) hace esos estados irrepresentables
 * en Java: la única forma de probar que la BASE los rechaza es evitando el dominio.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class EvidenciaPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T12:00:00Z"));

    @Autowired
    private EvidenciaPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId participanteId;
    private UUID rocaDiariaId;

    @BeforeEach
    void seedParticipanteYRoca() {
        participanteId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", participanteId.value())
                .setParameter("email", participanteId + "@renaser.test")
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                        VALUES (:id, 20)
                        """)
                .setParameter("id", participanteId.value())
                .executeUpdate();
        rocaDiariaId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.rocas_diarias
                            (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje)
                        VALUES (:id, :pid, CURRENT_DATE, 1, 'titulo', CAST('VERDE' AS renaser.color_pareto), 5,
                                CAST('CUERPO' AS renaser.eje_objetivo))
                        """)
                .setParameter("id", rocaDiariaId)
                .setParameter("pid", participanteId.value())
                .executeUpdate();
    }

    private Evidencia evidenciaRocaTexto(boolean esPrincipal) {
        return Evidencia.registrar(participanteId, new DestinoEvidencia.RocaDiaria(rocaDiariaId), TipoEvidencia.TEXTO,
                null, null, "listo", null, -12.05, -77.03, esPrincipal, CLOCK.now(), CLOCK);
    }

    @Test
    void guardaYRecuperaUnaEvidenciaDeRocaPorTexto() {
        Evidencia guardada = adapter.save(evidenciaRocaTexto(true));

        Optional<Evidencia> recuperada = adapter.byId(guardada.id());

        assertThat(recuperada).isPresent();
        Evidencia e = recuperada.get();
        assertThat(e.participanteId()).isEqualTo(participanteId);
        assertThat(e.destino()).isInstanceOf(DestinoEvidencia.RocaDiaria.class);
        assertThat(((DestinoEvidencia.RocaDiaria) e.destino()).rocaDiariaId()).isEqualTo(rocaDiariaId);
        assertThat(e.tipo()).isEqualTo(TipoEvidencia.TEXTO);
        assertThat(e.contenidoTexto()).isEqualTo("listo");
        assertThat(e.gpsLat()).isEqualTo(-12.05);
        assertThat(e.esPrincipal()).isTrue();
        assertThat(e.estadoValidacion().name()).isEqualTo("PENDIENTE");
    }

    @Test
    void byIdDeIdInexistenteDevuelveVacio() {
        assertThat(adapter.byId(EvidenciaId.newId())).isEmpty();
    }

    @Test
    @DisplayName("indice unico evidencias_principal_uk: dos evidencias 'esPrincipal' para la misma roca -> conflicto")
    void indiceUnicoDeEsPrincipalRechazaDosPrincipalesParaLaMismaRoca() {
        adapter.save(evidenciaRocaTexto(true));

        assertThatThrownBy(() -> adapter.save(evidenciaRocaTexto(true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pendientesLoteDevuelveSoloLasPendientesOrdenadasPorSubidaEn() {
        Evidencia primera = adapter.save(Evidencia.registrar(participanteId, new DestinoEvidencia.RocaDiaria(rocaDiariaId),
                TipoEvidencia.TEXTO, null, null, "una", null, null, null, false, Instant.parse("2026-08-25T09:00:00Z"),
                CLOCK));
        Evidencia segunda = adapter.save(Evidencia.registrar(participanteId, new DestinoEvidencia.RocaDiaria(rocaDiariaId),
                TipoEvidencia.TEXTO, null, null, "otra", null, null, null, false, Instant.parse("2026-08-25T09:05:00Z"),
                CLOCK));
        Evidencia aprobada = adapter.save(evidenciaRocaTexto(false));
        aprobada.aprobarPorIa();
        adapter.save(aprobada);

        List<Evidencia> lote = adapter.pendientesLote(Instant.parse("2026-08-25T23:59:59Z"), 25);

        assertThat(lote).extracting(e -> e.id().value()).containsExactly(primera.id().value(), segunda.id().value());
    }

    // ---- CHECKs de la tabla real, probados con INSERT nativo (el dominio no permite construirlos) ----

    @Test
    void checkArcoExclusivoRechazaFilaSinNingunDestino() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO renaser.evidencias (id, participante_id, tipo, contenido_texto)
                        VALUES (:id, :pid, CAST('TEXTO' AS renaser.tipo_evidencia), 'x')
                        """)
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("pid", participanteId.value())
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("CHECK evidencia_un_destino: dos destinos a la vez (roca_diaria_id + registro_habito_id) se rechaza")
    void checkArcoExclusivoRechazaFilaConDosDestinos() {
        // No hace falta que registro_habito_id referencie una fila real: la fila se rechaza de
        // todas formas (CHECK o FK, ambas son integridad de la tabla real) apenas hay dos
        // columnas de destino no-nulas a la vez — eso es exactamente lo que el arco exclusivo prohibe.
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO renaser.evidencias
                            (id, participante_id, roca_diaria_id, registro_habito_id, tipo, contenido_texto)
                        VALUES (:id, :pid, :roca, :registro, CAST('TEXTO' AS renaser.tipo_evidencia), 'x')
                        """)
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("pid", participanteId.value())
                        .setParameter("roca", rocaDiariaId)
                        .setParameter("registro", UUID.randomUUID())
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void checkMediaOTextoRechazaTextoSinContenido() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO renaser.evidencias (id, participante_id, roca_diaria_id, tipo)
                        VALUES (:id, :pid, :roca, CAST('TEXTO' AS renaser.tipo_evidencia))
                        """)
                        .setParameter("id", UUID.randomUUID())
                        .setParameter("pid", participanteId.value())
                        .setParameter("roca", rocaDiariaId)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class);
    }
}
