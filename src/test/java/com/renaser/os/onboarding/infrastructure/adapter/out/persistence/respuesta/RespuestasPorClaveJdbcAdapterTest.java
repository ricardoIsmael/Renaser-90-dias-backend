package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RespuestasPorClaveJdbcAdapter#mediaDe} contra Postgres real (D-253): el {@code media_id} de la
 * respuesta de ESE usuario a ESA clave, y nada más. Usa las preguntas del catálogo que siembra V10.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RespuestasPorClaveJdbcAdapterTest {

    @Autowired
    private RespuestasPorClaveJdbcAdapter adapter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId ana;
    private UserId luis;

    @BeforeEach
    void seed() {
        ana = usuario();
        luis = usuario();
    }

    @Test
    @DisplayName("mediaDe(): devuelve el media_id de la respuesta de esa persona a esa clave")
    void devuelveElMediaDeLaRespuesta() {
        long firmaDelPacto = media(ana);
        long firmaDeTerminos = media(ana);
        responder(ana, "signature", firmaDelPacto);
        responder(ana, "terms_signature", firmaDeTerminos);

        assertThat(adapter.mediaDe(ana, "signature")).contains(firmaDelPacto);
        assertThat(adapter.mediaDe(ana, "terms_signature")).contains(firmaDeTerminos);
    }

    @Test
    @DisplayName("mediaDe(): vacío sin respuesta, con la respuesta de otra persona o con una clave inexistente")
    void vacioSiNoHayRespuestaPropia() {
        responder(ana, "signature", media(ana));

        assertThat(adapter.mediaDe(luis, "signature")).isEmpty();
        assertThat(adapter.mediaDe(ana, "no_existe")).isEmpty();
    }

    @Test
    @DisplayName("mediaDe(): vacío si el archivo se borró (el FK deja media_id en NULL)")
    void vacioSiElArchivoSeBorro() {
        long firma = media(ana);
        responder(ana, "signature", firma);
        jdbcTemplate.update("DELETE FROM renaser.medias_onboarding WHERE id = ?", firma);

        assertThat(adapter.mediaDe(ana, "signature")).isEmpty();
    }

    private UserId usuario() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test");
        return UserId.of(id);
    }

    private long media(UserId dueno) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO renaser.medias_onboarding (usuario_id, clase, bucket, ruta_storage, mime)
                VALUES (?, 'firma', 'onboarding-media', ?, 'image/png') RETURNING id
                """, Long.class, dueno.value(), "onboarding/" + dueno + "/firma/" + UUID.randomUUID());
    }

    private void responder(UserId usuario, String clave, long mediaId) {
        jdbcTemplate.update("""
                INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, media_id)
                SELECT ?, id, ? FROM renaser.preguntas_onboarding WHERE clave_pregunta = ?
                """, usuario.value(), mediaId, clave);
    }
}
