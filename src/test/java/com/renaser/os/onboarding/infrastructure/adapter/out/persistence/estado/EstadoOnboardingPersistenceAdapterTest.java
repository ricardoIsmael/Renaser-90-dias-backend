package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.estado;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica en particular el mapeo de `progreso_flujo` (jsonb) — String de dominio -> String
 * de la columna Postgres real via {@code @JdbcTypeCode(SqlTypes.JSON)}, sin doble-serializar
 * ni perder el contenido (el riesgo real de mapear jsonb con Hibernate 6, ver
 * docs/MODULO_ONBOARDING.md).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class EstadoOnboardingPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private EstadoOnboardingPersistenceAdapter adapter;

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
    @DisplayName("guardar()+deUsuario(): roundtrip completo, incluyendo progreso_flujo jsonb")
    void guardaYRecuperaConJsonbIntacto() {
        EstadoOnboarding estado = EstadoOnboarding.iniciar(usuarioId, CLOCK);
        String progreso = "{\"pantalla\":\"bienvenida\",\"pasos\":[1,2,3]}";
        estado.avanzar("v90", "seccion-1", 2, progreso, CLOCK);

        adapter.guardar(estado);
        entityManager.flush();
        entityManager.clear();

        var recuperado = adapter.deUsuario(usuarioId);

        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().flujoActual()).isEqualTo("v90");
        assertThat(json(recuperado.get().progresoFlujo())).isEqualTo(json(progreso));
        assertThat(recuperado.get().pasoActual()).isEqualTo(2);
    }

    /**
     * `jsonb` en Postgres no preserva el texto original: re-serializa en su forma canonica
     * (orden de claves y espacios pueden cambiar) sin perder contenido — comparar como texto
     * literal es una asercion demasiado estricta para esa columna. Se compara el arbol JSON
     * parseado, no el string crudo.
     */
    private static com.fasterxml.jackson.databind.JsonNode json(String raw) {
        try {
            return new ObjectMapper().readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    @DisplayName("guardar() dos veces sobre el mismo usuario actualiza (PK = usuario_id), no duplica")
    void guardarDosVecesActualiza() {
        EstadoOnboarding estado = EstadoOnboarding.iniciar(usuarioId, CLOCK);
        adapter.guardar(estado);

        estado.aceptarHito(HitoOnboarding.TERMINOS, CLOCK);
        adapter.guardar(estado);
        entityManager.flush();
        entityManager.clear();

        long total = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM renaser.estado_onboarding WHERE usuario_id = :id")
                .setParameter("id", usuarioId.value())
                .getSingleResult()).longValue();

        assertThat(total).isEqualTo(1);
        assertThat(adapter.deUsuario(usuarioId).get().terminosAceptadosEn()).isEqualTo(CLOCK.now());
    }

    @Test
    @DisplayName("deUsuario(): vacio si nunca se guardo nada para ese usuario")
    void deUsuarioVacioSiNoExiste() {
        assertThat(adapter.deUsuario(UserId.of(UUID.randomUUID()))).isEmpty();
    }
}
