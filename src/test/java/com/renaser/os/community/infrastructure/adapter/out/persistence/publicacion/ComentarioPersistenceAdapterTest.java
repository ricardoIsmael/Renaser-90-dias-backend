package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.domain.model.publicacion.Comentario;
import com.renaser.os.community.domain.model.publicacion.ComentarioId;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-31 (docs/BITACORA_ERRORES.md): {@code pagina} tenia el mismo defecto que
 * {@code feed}/{@code feedOculto} de {@link PublicacionPersistenceAdapterTest} — el patron
 * {@code (:cursor IS NULL OR c.creadoEn > :cursor)} rompia contra Postgres real en
 * {@code GET /wall/{id}/comments} sin cursor (la primera pagina). Cubre ambos caminos
 * (con/sin cursor) contra Postgres real via Testcontainers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ComentarioPersistenceAdapterTest {

    @Autowired
    private ComentarioPersistenceAdapter adapter;

    @Autowired
    private PublicacionPersistenceAdapter publicacionAdapter;

    @Autowired
    private EntityManager entityManager;

    private UserId autorId;
    private PublicacionId publicacionId;

    @BeforeEach
    void seedAutorYPublicacion() {
        autorId = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", autorId.value())
                .setParameter("email", autorId + "@renaser.test")
                .executeUpdate();
        Publicacion publicacion = Publicacion.publicar(PublicacionId.of(UUID.randomUUID()), autorId,
                "texto de prueba",
                List.of(new MediaPublicacion(MediaPublicacion.BUCKET_DEFAULT, "ruta/1.jpg", "image/jpeg", 0)),
                null, Instant.parse("2026-08-20T09:00:00Z"));
        publicacionAdapter.save(publicacion);
        publicacionId = publicacion.id();
    }

    private void crearComentario(Instant creadoEn) {
        adapter.save(Comentario.escribir(ComentarioId.of(UUID.randomUUID()), publicacionId, autorId,
                "comentario " + creadoEn, creadoEn));
    }

    @Test
    void paginaSinCursorTraeTodoOrdenAscendente() {
        crearComentario(Instant.parse("2026-08-20T10:00:00Z"));
        crearComentario(Instant.parse("2026-08-20T11:00:00Z"));
        crearComentario(Instant.parse("2026-08-20T12:00:00Z"));

        List<Comentario> pagina = adapter.pagina(publicacionId, null, 10);

        assertThat(pagina).hasSize(3);
        assertThat(pagina).extracting(c -> c.creadoEn())
                .containsExactly(Instant.parse("2026-08-20T10:00:00Z"), Instant.parse("2026-08-20T11:00:00Z"),
                        Instant.parse("2026-08-20T12:00:00Z"));
    }

    @Test
    void paginaConCursorSoloTraeLoPosteriorAlCursor() {
        crearComentario(Instant.parse("2026-08-20T10:00:00Z"));
        crearComentario(Instant.parse("2026-08-20T11:00:00Z"));
        crearComentario(Instant.parse("2026-08-20T12:00:00Z"));

        List<Comentario> pagina = adapter.pagina(publicacionId, Instant.parse("2026-08-20T10:00:00Z"), 10);

        assertThat(pagina).hasSize(2);
        assertThat(pagina).extracting(c -> c.creadoEn())
                .containsExactly(Instant.parse("2026-08-20T11:00:00Z"), Instant.parse("2026-08-20T12:00:00Z"));
    }
}
