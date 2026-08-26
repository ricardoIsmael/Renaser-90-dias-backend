package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cubre las dos restricciones reales del baseline que el dominio ya replica pero que hay
 * que probar contra Postgres de verdad (CLAUDE.MD §0.2): el CHECK {@code un_solo_valor} y
 * el UNIQUE {@code (usuario_id, pregunta_id)}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RespuestaPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private RespuestaPersistenceAdapter adapter;

    @Autowired
    private SpringDataRespuestaOnboardingRepository repository;

    @Autowired
    private EntityManager entityManager;

    private UserId usuarioId;
    private int preguntaId;

    @BeforeEach
    void seedUsuarioYPregunta() {
        usuarioId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", usuarioId.value())
                .setParameter("email", usuarioId + "@renaser.test")
                .setParameter("nombre", "Fixture " + usuarioId)
                .executeUpdate();

        short seccionId = ((Number) entityManager.createNativeQuery("""
                        INSERT INTO renaser.secciones_onboarding (flujo, clave_seccion, titulo)
                        VALUES ('v90', 'intro', 'Introduccion') RETURNING id
                        """).getSingleResult()).shortValue();

        preguntaId = ((Number) entityManager.createNativeQuery("""
                        INSERT INTO renaser.preguntas_onboarding (seccion_id, clave_pregunta, texto, tipo)
                        VALUES (:seccionId, 'clave-1', 'Pregunta de prueba', CAST('TEXTO' AS renaser.tipo_pregunta_onboarding))
                        RETURNING id
                        """)
                .setParameter("seccionId", seccionId)
                .getSingleResult()).intValue();
    }

    @Test
    @DisplayName("guardar() dos veces sobre la misma (usuario, pregunta) actualiza, no duplica")
    void guardarDosVecesActualiza() {
        Respuesta primera = Respuesta.crear(TipoPreguntaOnboarding.TEXTO, usuarioId, preguntaId, "primero", null,
                null, null, null, null, CLOCK);
        adapter.guardar(primera);

        FixedClock masTarde = FixedClock.at(CLOCK.now().plusSeconds(60));
        Respuesta segunda = Respuesta.crear(TipoPreguntaOnboarding.TEXTO, usuarioId, preguntaId, "segundo", null,
                null, null, null, null, masTarde);
        adapter.guardar(segunda);
        entityManager.flush();

        long total = repository.count();
        assertThat(total).isEqualTo(1);
        assertThat(adapter.porUsuarioYPregunta(usuarioId, preguntaId)).get()
                .extracting(Respuesta::valorTexto).isEqualTo("segundo");
    }

    @Test
    @DisplayName("valorJson (jsonb) hace roundtrip identico, sin doble-serializar")
    void valorJsonHaceRoundtrip() {
        String json = "[\"opcion-a\",\"opcion-b\"]";
        Respuesta r = Respuesta.crear(TipoPreguntaOnboarding.SELECCION_MULTIPLE, usuarioId, preguntaId, null, null,
                null, null, json, null, CLOCK);

        adapter.guardar(r);
        entityManager.flush();
        entityManager.clear();

        var recuperada = adapter.porUsuarioYPregunta(usuarioId, preguntaId);
        assertThat(recuperada).isPresent();
        assertThat(jsonTree(recuperada.get().valorJson())).isEqualTo(jsonTree(json));
    }

    /** jsonb no preserva el texto original (orden de claves/espacios) — comparar el arbol
     * parseado, no el string crudo. */
    private static com.fasterxml.jackson.databind.JsonNode jsonTree(String raw) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    @DisplayName("CHECK un_solo_valor de Postgres: dos valores a la vez, bypaseando el dominio, colisiona en la base")
    void checkUnSoloValorColisionaEnLaBase() {
        var entity = new RespuestaOnboardingJpaEntity(null, usuarioId.value(), preguntaId, "texto",
                BigDecimal.TEN, null, null, null, null, null, Instant.now(), Instant.now());

        assertThatThrownBy(() -> {
            repository.save(entity);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("UNIQUE (usuario_id, pregunta_id) de Postgres: dos filas para la misma clave, bypaseando el upsert, colisionan")
    void uniqueUsuarioPreguntaColisionaEnLaBase() {
        var primera = new RespuestaOnboardingJpaEntity(null, usuarioId.value(), preguntaId, "uno", null, null, null,
                null, null, null, Instant.now(), Instant.now());
        repository.saveAndFlush(primera);

        var segunda = new RespuestaOnboardingJpaEntity(null, usuarioId.value(), preguntaId, "dos", null, null, null,
                null, null, null, Instant.now(), Instant.now());

        assertThatThrownBy(() -> repository.saveAndFlush(segunda)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
