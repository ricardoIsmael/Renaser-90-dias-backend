package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.grabacionv90;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class GrabacionV90PersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private GrabacionV90PersistenceAdapter adapter;

    @Autowired
    private SpringDataGrabacionV90Repository repository;

    @Autowired
    private EntityManager entityManager;

    private UserId usuarioId;

    @BeforeEach
    void seedUsuario() {
        usuarioId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", usuarioId.value())
                .setParameter("email", usuarioId + "@renaser.test")
                .setParameter("nombre", "Fixture " + usuarioId)
                .executeUpdate();
    }

    @Test
    @DisplayName("guardar()+porSlot(): roundtrip por (usuario, fase, eje, indice)")
    void guardaYRecuperaPorSlot() {
        GrabacionV90 g = GrabacionV90.crearSlot(usuarioId, "FASE_1", "MENTE", (short) 2, "v90_mente_2", CLOCK);

        adapter.guardar(g);
        entityManager.flush();
        entityManager.clear();

        var recuperado = adapter.porSlot(usuarioId, "FASE_1", "MENTE", (short) 2);
        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().estadoIa()).isEqualTo(EstadoIAv90.PENDIENTE);
        assertThat(recuperado.get().grabada()).isFalse();
    }

    @Test
    @DisplayName("guardar() dos veces sobre el mismo slot actualiza (upsert), no duplica")
    void guardarDosVecesActualizaElMismoSlot() {
        long mediaId = seedMedia();
        GrabacionV90 g = GrabacionV90.crearSlot(usuarioId, "FASE_1", "MENTE", (short) 0, "v90_mente_0", CLOCK);
        g.marcarGrabada(mediaId, null, "transcripcion", CLOCK);
        adapter.guardar(g);

        GrabacionV90 recuperado = adapter.porSlot(usuarioId, "FASE_1", "MENTE", (short) 0).orElseThrow();
        // reintento fallido -> PENDIENTE otra vez, mismo slot
        recuperado.procesarIntentoDeValidacion(CLOCK);
        recuperado.registrarSinResultado(CLOCK);
        adapter.guardar(recuperado);
        entityManager.flush();

        long total = repository.count();
        assertThat(total).isEqualTo(1);
        assertThat(adapter.porSlot(usuarioId, "FASE_1", "MENTE", (short) 0).get().intentosIa()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("feedback_ia (jsonb) hace roundtrip identico")
    void feedbackIaHaceRoundtrip() {
        long mediaId = seedMedia();
        GrabacionV90 g = GrabacionV90.crearSlot(usuarioId, "FASE_1", "MENTE", (short) 1, "v90_mente_1", CLOCK);
        g.marcarGrabada(mediaId, null, "transcripcion", CLOCK);
        g.procesarIntentoDeValidacion(CLOCK);
        String feedback = "{\"claridad\":8,\"tono\":\"seguro\"}";
        g.registrarAprobacion(feedback, CLOCK);

        adapter.guardar(g);
        entityManager.flush();
        entityManager.clear();

        var recuperado = adapter.porSlot(usuarioId, "FASE_1", "MENTE", (short) 1);
        assertThat(recuperado).isPresent();
        assertThat(json(recuperado.get().feedbackIa())).isEqualTo(json(feedback));
        assertThat(recuperado.get().estadoIa()).isEqualTo(EstadoIAv90.APROBADA);
    }

    /** jsonb no preserva el texto original (orden de claves/espacios) — comparar el arbol
     * parseado, no el string crudo. */
    private static com.fasterxml.jackson.databind.JsonNode json(String raw) {
        try {
            return new ObjectMapper().readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private long seedMedia() {
        return ((Number) entityManager.createNativeQuery("""
                        INSERT INTO renaser.medias_onboarding (usuario_id, clase, bucket, ruta_storage)
                        VALUES (:usuarioId, 'audio', 'onboarding-media', :ruta) RETURNING id
                        """)
                .setParameter("usuarioId", usuarioId.value())
                .setParameter("ruta", "onboarding/" + usuarioId + "/audio/" + UUID.randomUUID())
                .getSingleResult()).longValue();
    }

    @Test
    @DisplayName("UNIQUE (usuario_id, fase, eje, indice) de Postgres: dos filas para el mismo slot colisionan en la base")
    void uniqueSlotColisionaEnLaBase() {
        var primera = new GrabacionV90JpaEntity(null, usuarioId.value(), "FASE_1", "MENTE", (short) 3, null, false,
                null, null, null, EstadoIAv90Jpa.PENDIENTE, (short) 0, null, null, Instant.now(), Instant.now());
        repository.saveAndFlush(primera);

        var segunda = new GrabacionV90JpaEntity(null, usuarioId.value(), "FASE_1", "MENTE", (short) 3, null, false,
                null, null, null, EstadoIAv90Jpa.PENDIENTE, (short) 0, null, null, Instant.now(), Instant.now());

        assertThatThrownBy(() -> repository.saveAndFlush(segunda)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
