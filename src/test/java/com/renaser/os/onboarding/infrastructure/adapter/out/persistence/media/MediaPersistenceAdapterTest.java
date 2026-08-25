package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.media;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.onboarding.domain.model.media.ClaseMedia;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class MediaPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private MediaPersistenceAdapter adapter;

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
    @DisplayName("guardar()+porId(): roundtrip completo, incluyendo metadatos jsonb")
    void guardaYRecuperaConMetadatosJsonb() {
        String metadatos = "{\"codec\":\"aac\",\"sampleRate\":44100}";
        MediaOnboarding media = MediaOnboarding.registrar(usuarioId, "v90", "clave-1", ClaseMedia.AUDIO,
                MediaOnboarding.BUCKET_DEFAULT, "onboarding/x/audio/uuid-1", "audio/aac", 2048L, null, metadatos,
                CLOCK);

        MediaOnboarding guardada = adapter.guardar(media);
        entityManager.flush();
        entityManager.clear();

        var recuperada = adapter.porId(guardada.id());
        assertThat(recuperada).isPresent();
        assertThat(json(recuperada.get().metadatos())).isEqualTo(json(metadatos));
        assertThat(recuperada.get().clase()).isEqualTo(ClaseMedia.AUDIO);
    }

    /** jsonb no preserva el texto original (orden de claves/espacios) — comparar el arbol
     * parseado, no el string crudo. */
    private static com.fasterxml.jackson.databind.JsonNode json(String raw) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    @DisplayName("porIdYUsuario(): vacio si la media es de otro usuario (blindaje de ownership)")
    void porIdYUsuarioFiltraPorPropietario() {
        MediaOnboarding media = MediaOnboarding.registrar(usuarioId, "v90", "clave-1", ClaseMedia.FIRMA,
                MediaOnboarding.BUCKET_DEFAULT, "onboarding/x/firma/uuid-2", "image/svg+xml", null, null, null,
                CLOCK);
        MediaOnboarding guardada = adapter.guardar(media);
        entityManager.flush();

        UserId otro = UserId.of(UUID.randomUUID());
        assertThat(adapter.porIdYUsuario(guardada.id(), otro)).isEmpty();
        assertThat(adapter.porIdYUsuario(guardada.id(), usuarioId)).isPresent();
    }

    @Test
    @DisplayName("porId(): vacio si no existe")
    void porIdVacioSiNoExiste() {
        assertThat(adapter.porId(999_999L)).isEmpty();
    }
}
